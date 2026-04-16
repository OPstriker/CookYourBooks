package app.cookyourbooks.adapters;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import app.cookyourbooks.cli.fixtures.TestRecipeBuilder;
import app.cookyourbooks.model.Instruction;
import app.cookyourbooks.model.Recipe;
import app.cookyourbooks.model.Servings;
import app.cookyourbooks.model.Unit;

/** Unit tests for {@link PdfExporter}. */
@SuppressWarnings("NullAway.Init") // @TempDir and @BeforeEach fields are injected by JUnit 5
class PdfExporterTest {

  // @TempDir gives us a fresh temporary directory per test — JUnit 5 cleans it up automatically
  @TempDir Path tempDir;

  private PdfExporter exporter;

  @BeforeEach
  void setUp() {
    exporter = new PdfExporter();
  }

  // Helper

  /**
   * Loads the PDF at {@code path} and returns all text extracted from it. Uses PDFTextStripper,
   * which reads the text operators embedded in the PDF content stream.
   */
  private String extractText(Path path) throws IOException {
    try (PDDocument doc = Loader.loadPDF(path.toFile())) {
      return new PDFTextStripper().getText(doc);
    }
  }

  // Tests

  /** The output file must exist and be a valid PDF (non-empty) after export. */
  @Test
  void export_createsNonEmptyFileAtOutputPath() throws IOException {
    Recipe recipe =
        TestRecipeBuilder.recipe("Garlic Butter Pasta")
            .serves(2)
            .withIngredient("pasta", 200, Unit.GRAM)
            .withStep("Boil pasta")
            .build();

    Path output = tempDir.resolve("test.pdf");
    exporter.export(recipe, output);

    assertThat(output).exists().isNotEmptyFile();
  }

  /** The recipe title must appear in the extracted text of the generated PDF. */
  @Test
  void export_pdfContainsRecipeTitle() throws IOException {
    Recipe recipe =
        TestRecipeBuilder.recipe("Lemon Herb Chicken")
            .serves(4)
            .withIngredient("chicken", 500, Unit.GRAM)
            .withStep("Season chicken")
            .build();

    Path output = tempDir.resolve("test.pdf");
    exporter.export(recipe, output);

    assertThat(extractText(output)).contains("Lemon Herb Chicken");
  }

  /** Every ingredient name must appear in the generated PDF. */
  @Test
  void export_pdfContainsAllIngredientNames() throws IOException {
    Recipe recipe =
        TestRecipeBuilder.recipe("Pancakes")
            .serves(4)
            .withIngredient("flour", 2, Unit.CUP)
            .withIngredient("milk", 1, Unit.CUP)
            .withVagueIngredient("salt", "to taste")
            .withStep("Mix and cook")
            .build();

    Path output = tempDir.resolve("test.pdf");
    exporter.export(recipe, output);

    String text = extractText(output);
    assertThat(text).contains("flour").contains("milk").contains("salt");
  }

  /** Every instruction step text must appear in the generated PDF. */
  @Test
  void export_pdfContainsAllInstructionSteps() throws IOException {
    Recipe recipe =
        TestRecipeBuilder.recipe("Simple Soup")
            .serves(2)
            .withIngredient("water", 1, Unit.LITER)
            .withStep("Boil the water")
            .withStep("Add vegetables")
            .withStep("Season and serve")
            .build();

    Path output = tempDir.resolve("test.pdf");
    exporter.export(recipe, output);

    String text = extractText(output);
    assertThat(text)
        .contains("Boil the water")
        .contains("Add vegetables")
        .contains("Season and serve");
  }

  /** A recipe with null servings must not throw — servings line is simply omitted. */
  @Test
  void export_recipeWithoutServings_doesNotThrow() {
    Recipe recipe =
        new Recipe(
            null,
            "Mystery Dish",
            null, // servings is null
            List.of(),
            List.of(new Instruction(1, "Follow your heart", List.of())),
            List.of());

    Path output = tempDir.resolve("test.pdf");
    assertThatCode(() -> exporter.export(recipe, output)).doesNotThrowAnyException();
  }

  /**
   * Recipe titles or ingredients containing Unicode smart-quotes, dashes, or accented characters
   * must not cause PDFBox to throw — the sanitize() method should convert them to ASCII.
   */
  @Test
  void export_recipeWithUnicodeCharacters_doesNotThrow() {
    // These characters commonly appear in web-imported recipes and would crash PDFBox
    // if not sanitized (PDFBox standard fonts use Latin-1 encoding)
    Recipe recipe =
        TestRecipeBuilder.recipe("Chef\u2019s Special") // right single quote (U+2019)
            .serves(2)
            .withIngredient("cr\u00e8me fra\u00eeche", 1, Unit.CUP) // è, î
            .withStep("Mix well \u2013 serve immediately") // en dash (U+2013)
            .build();

    Path output = tempDir.resolve("test.pdf");
    assertThatCode(() -> exporter.export(recipe, output)).doesNotThrowAnyException();
  }

  /**
   * A recipe whose instruction text is long enough to require word-wrapping must produce a valid
   * PDF with at least one page, with no exception thrown.
   */
  @Test
  void export_longInstructionText_wrapsWithoutThrowing() throws IOException {
    String longStep =
        "Preheat the oven to 375 degrees Fahrenheit and prepare a large baking sheet by lining it "
            + "with parchment paper, then arrange all the prepared vegetables in a single layer "
            + "making sure they do not overlap so that they roast evenly rather than steaming.";

    Recipe recipe =
        TestRecipeBuilder.recipe("Roasted Vegetables")
            .serves(4)
            .withIngredient("mixed vegetables", 500, Unit.GRAM)
            .withStep(longStep)
            .build();

    Path output = tempDir.resolve("test.pdf");
    exporter.export(recipe, output);

    try (PDDocument doc = Loader.loadPDF(output.toFile())) {
      assertThat(doc.getNumberOfPages()).isGreaterThanOrEqualTo(1);
    }
  }

  /**
   * A recipe with enough content to overflow a single A4 page must produce a PDF with more than one
   * page.
   */
  @Test
  void export_recipeWithManySteps_producesMultiplePages() throws IOException {
    TestRecipeBuilder builder = TestRecipeBuilder.recipe("Elaborate Recipe").serves(10);
    builder.withIngredient("flour", 2, Unit.CUP);
    // 40 steps is reliably enough to overflow one A4 page
    for (int i = 1; i <= 40; i++) {
      builder.withStep("Step " + i + ": do something specific and important here");
    }
    Recipe recipe = builder.build();

    Path output = tempDir.resolve("test.pdf");
    exporter.export(recipe, output);

    try (PDDocument doc = Loader.loadPDF(output.toFile())) {
      assertThat(doc.getNumberOfPages()).isGreaterThan(1);
    }
  }

  /** Serving count must appear in the PDF when the recipe has servings. */
  @Test
  void export_pdfContainsServingCount() throws IOException {
    Recipe recipe =
        new Recipe(
            null,
            "Banana Pancakes",
            new Servings(6, "pancakes"),
            List.of(),
            List.of(new Instruction(1, "Mix and cook", List.of())),
            List.of());

    Path output = tempDir.resolve("test.pdf");
    exporter.export(recipe, output);

    // Servings.toString() returns "6 pancakes"
    assertThat(extractText(output)).contains("6 pancakes");
  }
}
