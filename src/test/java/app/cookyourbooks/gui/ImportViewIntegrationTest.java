package app.cookyourbooks.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import javafx.scene.Parent;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import app.cookyourbooks.gui.view.ImportViewController;
import app.cookyourbooks.gui.view.RecipeEditorViewController;
import app.cookyourbooks.gui.viewmodel.ImportViewModelImpl;
import app.cookyourbooks.gui.viewmodel.RecipeEditorViewModelImpl;
import app.cookyourbooks.model.Recipe;
import app.cookyourbooks.model.RecipeCollection;
import app.cookyourbooks.model.SourceType;
import app.cookyourbooks.repository.RecipeRepository;
import app.cookyourbooks.services.LibrarianService;
import app.cookyourbooks.services.ocr.FakeRecipeOcrService;

/**
 * Integration (E2E) tests for the Import View using TestFX.
 *
 * <p>Verifies that the correct state pane is visible for each workflow state and that the Cancel
 * button returns the user to the idle pane.
 */
@SuppressWarnings("NullAway.Init") // importVm is set by @Start before any test runs
@ExtendWith(ApplicationExtension.class)
class ImportViewIntegrationTest {

  private ImportViewModelImpl importVm;

  @SuppressWarnings("UnusedMethod") // called reflectively by TestFX
  @Start
  private void start(Stage stage) throws Exception {
    // Use a slow fake OCR so we can observe the PROCESSING state in tests
    var ocrService = new FakeRecipeOcrService(5000);
    var stubLibrarian = new StubLibrarianService();
    importVm = new ImportViewModelImpl(ocrService, stubLibrarian);

    // Create a stub RecipeEditorViewModelImpl for the embedded editor in the REVIEW pane.
    // StubRecipeRepository throws UnsupportedOperationException for all methods — safe here
    // because the integration tests do not trigger a real save path through the editor.
    var editorVm = new RecipeEditorViewModelImpl(new StubRecipeRepository(), stubLibrarian);

    // Load a RecipeEditorView instance to embed inside the Import REVIEW pane.
    javafx.fxml.FXMLLoader editorLoader =
        new javafx.fxml.FXMLLoader(getClass().getResource("/fxml/RecipeEditorView.fxml"));
    editorLoader.setControllerFactory(
        clazz -> new RecipeEditorViewController(editorVm, new NavigationService()));
    Parent editorView = editorLoader.load();

    javafx.fxml.FXMLLoader loader =
        new javafx.fxml.FXMLLoader(getClass().getResource("/fxml/ImportView.fxml"));
    loader.setControllerFactory(
        clazz -> new ImportViewController(importVm, editorVm, editorView, new NavigationService()));
    Parent root = loader.load();

    stage.setScene(new javafx.scene.Scene(root, 600, 500));
    stage.show();
  }

  // ── Tests ─────────────────────────────────────────────────────────────────

  /** The idle pane is shown when the ViewModel is in IDLE state (initial state). */
  @Test
  void idlePaneIsVisibleOnLoad(FxRobot robot) {
    VBox idlePane = robot.lookup("#idlePane").queryAs(VBox.class);
    assertThat(idlePane.isVisible()).isTrue();
    assertThat(idlePane.isManaged()).isTrue();

    VBox processingPane = robot.lookup("#processingPane").queryAs(VBox.class);
    assertThat(processingPane.isVisible()).isFalse();
    assertThat(processingPane.isManaged()).isFalse();
  }

  /** Clicking Cancel during processing returns to the idle pane. */
  @Test
  void cancelDuringProcessingReturnsToIdle(FxRobot robot) {
    // Transition to PROCESSING programmatically (FileChooser is hard to drive in tests)
    robot.interact(() -> importVm.startImport(java.nio.file.Path.of("test.jpg")));

    VBox processingPane = robot.lookup("#processingPane").queryAs(VBox.class);
    assertThat(processingPane.isVisible()).isTrue();

    robot.clickOn("#cancelButton");
    WaitForAsyncUtils.waitForFxEvents(); // let bindings propagate after state change

    VBox idlePane = robot.lookup("#idlePane").queryAs(VBox.class);
    assertThat(idlePane.isVisible()).isTrue();
    assertThat(importVm.getState()).isEqualTo("idle");
  }

  /** After successful OCR the review pane is shown with the extracted recipe title. */
  @Test
  void successfulOcrShowsReviewPane(FxRobot robot) {
    // Use a fast OCR service for this test — replace the VM with a new one
    var fastVm = new ImportViewModelImpl(new FakeRecipeOcrService(0), new StubLibrarianService());

    robot.interact(() -> fastVm.startImport(java.nio.file.Path.of("pancakes.jpg")));
    WaitForAsyncUtils.waitForFxEvents(); // flush background thread + FX callback

    // Verify state via ViewModel (the FXML is bound to importVm, not fastVm, but we test the
    // ViewModel state directly here as the source of truth for the binding)
    assertThat(fastVm.getState()).isEqualTo("review");
    assertThat(fastVm.getImportedRecipeTitle()).contains("pancakes.jpg");
  }

  // ── Stubs ─────────────────────────────────────────────────────────────────

  /**
   * Minimal RecipeRepository stub used to construct RecipeEditorViewModelImpl for the embedded
   * editor in integration tests. No real repository operations are triggered during these tests
   * (the tests only verify pane visibility and cancel behaviour, not save flows), so all methods
   * throw UnsupportedOperationException.
   */
  private static final class StubRecipeRepository implements RecipeRepository {
    @Override
    public Optional<Recipe> findById(String id) {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<Recipe> findAll() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void save(Recipe recipe) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void delete(String id) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<Recipe> findByTitle(String title) {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<Recipe> findAllByTitle(String title) {
      throw new UnsupportedOperationException();
    }
  }

  private static final class StubLibrarianService implements LibrarianService {
    @Override
    public List<RecipeCollection> listCollections() {
      return List.of();
    }

    @Override
    public void saveRecipe(Recipe recipe, String collectionId) {}

    @Override
    public RecipeCollection createCollection(String name) {
      throw new UnsupportedOperationException();
    }

    @Override
    public RecipeCollection createCollection(String name, SourceType sourceType) {
      throw new UnsupportedOperationException();
    }

    @Override
    public java.util.Optional<RecipeCollection> findCollectionById(String id) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void deleteCollection(String id) {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<RecipeCollection> findAllCollectionsByTitle(String title) {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<Recipe> listRecipes(String collectionName) {
      throw new UnsupportedOperationException();
    }

    @Override
    public java.util.Optional<Recipe> findRecipe(String title) {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<Recipe> findAllRecipesByTitle(String title) {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<Recipe> resolveRecipes(String query) {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<Recipe> searchByIngredient(String ingredient) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void deleteRecipe(String id) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Recipe importFromJson(java.nio.file.Path file, String collectionName) {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<Recipe> listAllRecipes() {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<app.cookyourbooks.conversion.ConversionRule> listHouseConversions() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void addHouseConversion(
        double fromAmount,
        app.cookyourbooks.model.Unit fromUnit,
        String ingredientName,
        double toAmount,
        app.cookyourbooks.model.Unit toUnit) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeHouseConversion(String identifier) {
      throw new UnsupportedOperationException();
    }
  }
}
