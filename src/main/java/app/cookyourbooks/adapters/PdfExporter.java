package app.cookyourbooks.adapters;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import app.cookyourbooks.model.Ingredient;
import app.cookyourbooks.model.Instruction;
import app.cookyourbooks.model.Recipe;

/**
 * Exports a {@link Recipe} to a PDF file using Apache PDFBox.
 *
 * <p>The generated PDF contains:
 *
 * <ul>
 *   <li>The recipe title (large bold heading)
 *   <li>Serving count (if present)
 *   <li>An "Ingredients" section listing each ingredient
 *   <li>An "Instructions" section with numbered steps, word-wrapped to fit the page
 * </ul>
 *
 * <p>Long instruction texts are automatically word-wrapped. New pages are added whenever the
 * remaining vertical space is insufficient.
 */
public final class PdfExporter {

  // ── Page geometry constants ──────────────────────────────────────────────

  private static final float MARGIN = 60f;
  private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();
  private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
  private static final float CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN;

  // ── Font size constants ──────────────────────────────────────────────────

  private static final float TITLE_SIZE = 22f;
  private static final float HEADING_SIZE = 14f;
  private static final float BODY_SIZE = 11f;

  /**
   * Exports the given recipe to a PDF file at {@code outputPath}.
   *
   * <p>Overwrites the file if it already exists.
   *
   * @param recipe the recipe to export (must not be null)
   * @param outputPath the destination file path (must not be null)
   * @throws IOException if the PDF cannot be written
   */
  public void export(Recipe recipe, Path outputPath) throws IOException {
    try (PDDocument doc = new PDDocument()) {
      PageWriter writer = new PageWriter(doc);

      // ── Title ────────────────────────────────────────────────────────────
      writer.writeLine(recipe.getTitle(), TITLE_SIZE, true);
      writer.addSpace(10f);

      // ── Servings (optional field on Recipe) ──────────────────────────────
      if (recipe.getServings() != null) {
        writer.writeLine("Serves: " + recipe.getServings(), BODY_SIZE, false);
        writer.addSpace(14f);
      }

      // ── Ingredients section ───────────────────────────────────────────────
      writer.writeLine("Ingredients", HEADING_SIZE, true);
      writer.addSpace(6f);
      for (Ingredient ing : recipe.getIngredients()) {
        // Ingredient.toString() is already formatted ("2 cups flour, sifted")
        writer.writeWrapped("- " + ing, BODY_SIZE);
        writer.addSpace(3f);
      }
      writer.addSpace(12f);

      // ── Instructions section ──────────────────────────────────────────────
      writer.writeLine("Instructions", HEADING_SIZE, true);
      writer.addSpace(6f);
      for (Instruction step : recipe.getInstructions()) {
        // Instruction.toString() returns "1. Preheat oven..." — we use getters
        // for clarity
        writer.writeWrapped(step.getStepNumber() + ". " + step.getText(), BODY_SIZE);
        writer.addSpace(6f);
      }

      writer.close();
      doc.save(outputPath.toFile());
    }
  }

  // ── Inner class: stateful page writer ───────────────────────────────────

  /**
   * Manages the current PDF page and Y-cursor position. Creates new pages automatically when
   * content reaches the bottom margin.
   *
   * <p>This is a private implementation detail of {@link PdfExporter}; it is not visible outside
   * this class.
   */
  private static final class PageWriter {

    private final PDDocument doc;
    private final PDType1Font boldFont;
    private final PDType1Font regularFont;

    /** Current content stream for the active page. */
    private PDPageContentStream stream;

    /** Current vertical position (Y coordinate) on the active page, measured from the bottom. */
    private float y;

    PageWriter(PDDocument doc) throws IOException {
      this.doc = doc;
      // Standard14Fonts avoids embedding — these fonts are built into every PDF viewer
      this.boldFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
      this.regularFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
      startNewPage();
    }

    /**
     * Writes a single line of text at the current Y position. Starts a new page first if there is
     * not enough vertical room.
     *
     * @param text the text to print
     * @param fontSize point size
     * @param bold whether to use the bold font variant
     */
    void writeLine(String text, float fontSize, boolean bold) throws IOException {
      PDType1Font font = bold ? boldFont : regularFont;
      float lineHeight = fontSize * 1.4f; // 1.4× leading is standard typographic practice
      if (y - lineHeight < MARGIN) {
        startNewPage();
      }
      stream.beginText();
      stream.setFont(font, fontSize);
      stream.newLineAtOffset(MARGIN, y);
      stream.showText(sanitize(text));
      stream.endText();
      y -= lineHeight;
    }

    /**
     * Word-wraps {@code text} so each line fits within {@link PdfExporter#CONTENT_WIDTH}, then
     * writes each resulting line via {@link #writeLine}.
     */
    void writeWrapped(String text, float fontSize) throws IOException {
      for (String line : wrap(sanitize(text), regularFont, fontSize)) {
        writeLine(line, fontSize, false);
      }
    }

    /**
     * Moves the Y cursor downward by {@code points} without printing anything (blank space between
     * sections). Triggers a new page if the cursor would fall below the margin.
     */
    void addSpace(float points) throws IOException {
      if (y - points < MARGIN) {
        startNewPage();
      } else {
        y -= points;
      }
    }

    /** Closes the current page content stream. Must be called before saving the document. */
    void close() throws IOException {
      stream.close();
    }

    // ── Private helpers ──────────────────────────────────────────────────

    /** Opens a new PDF page and resets {@link #y} to just below the top margin. */
    private void startNewPage() throws IOException {
      if (stream != null) {
        stream.close();
      }
      PDPage page = new PDPage(PDRectangle.A4);
      doc.addPage(page);
      stream = new PDPageContentStream(doc, page);
      y = PAGE_HEIGHT - MARGIN;
    }

    /**
     * Splits {@code text} into a list of strings each narrow enough to fit within {@link
     * PdfExporter#CONTENT_WIDTH} at the given font and size.
     *
     * <p>Words that individually exceed the content width are placed on their own line.
     */
    private static List<String> wrap(String text, PDType1Font font, float fontSize)
        throws IOException {
      List<String> lines = new ArrayList<>();
      String[] words = text.split(" ", -1);
      StringBuilder current = new StringBuilder();

      for (String word : words) {
        String candidate = current.isEmpty() ? word : current + " " + word;
        // getStringWidth returns width in 1/1000 of a text-space unit; divide by 1000 and
        // multiply by font size to get points
        float width = font.getStringWidth(candidate) / 1000f * fontSize;
        if (width > CONTENT_WIDTH && !current.isEmpty()) {
          lines.add(current.toString());
          current = new StringBuilder(word);
        } else {
          current = new StringBuilder(candidate);
        }
      }
      if (!current.isEmpty()) {
        lines.add(current.toString());
      }
      return lines;
    }

    /**
     * Replaces Unicode characters that cannot be encoded by PDFBox's built-in Latin-1 fonts with
     * safe ASCII equivalents, preventing {@link IllegalArgumentException} from PDFBox at render
     * time.
     */
    private static String sanitize(String text) {
      return text.replace('\u2019', '\'') // right single quotation mark → apostrophe
          .replace('\u2018', '\'') // left single quotation mark → apostrophe
          .replace('\u201C', '"') // left double quotation mark
          .replace('\u201D', '"') // right double quotation mark
          .replace('\u2013', '-') // en dash
          .replace('\u2014', '-') // em dash
          .replace('\u2022', '-') // bullet point
          .replace('\u00e9', 'e') // é
          .replace('\u00e0', 'a') // à
          .replace('\u00fc', 'u'); // ü
    }
  }
}
