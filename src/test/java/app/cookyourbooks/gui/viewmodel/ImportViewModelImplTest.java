package app.cookyourbooks.gui.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javafx.application.Platform;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import app.cookyourbooks.gui.ViewModelTestBase;
import app.cookyourbooks.model.Recipe;
import app.cookyourbooks.model.RecipeCollection;
import app.cookyourbooks.services.LibrarianService;
import app.cookyourbooks.services.ocr.FakeRecipeOcrService;
import app.cookyourbooks.services.ocr.OcrException;
import app.cookyourbooks.services.ocr.RecipeOcrService;

/**
 * Unit tests for {@link ImportViewModelImpl}.
 *
 * <p>Covers all I1–I10 requirements from the spec. Uses {@link FakeRecipeOcrService} (0 ms delay)
 * for instant async completion and {@link #awaitState} to reliably wait for async state transitions
 * without polling.
 */
class ImportViewModelImplTest extends ViewModelTestBase {

  /**
   * Waits (up to 5 s) for the given ViewModel to reach {@code expectedState}. Registers a listener
   * on the FX thread so it does not miss events, then blocks until the latch fires.
   */
  private static void awaitState(ImportViewModelImpl target, String expectedState)
      throws InterruptedException {
    if (target.getState().equals(expectedState)) {
      return;
    }
    CountDownLatch latch = new CountDownLatch(1);
    Platform.runLater(
        () ->
            target
                .stateProperty()
                .addListener(
                    (obs, oldVal, newVal) -> {
                      if (newVal.name().toLowerCase(Locale.ROOT).equals(expectedState)) {
                        latch.countDown();
                      }
                    }));
    waitForFxEvents(); // let the listener be registered before the task posts its callback
    latch.await(5, TimeUnit.SECONDS);
    waitForFxEvents(); // flush any follow-up FX events
  }

  // ── Test doubles ─────────────────────────────────────────────────────────

  /** OCR service that always succeeds instantly. */
  private final RecipeOcrService fastOcr = new FakeRecipeOcrService(0);

  /** OCR service that always fails with a known message. */
  private final RecipeOcrService failingOcr =
      imagePath -> {
        throw new OcrException("network timeout");
      };

  /** Minimal stub for LibrarianService that tracks the last saveRecipe call. */
  private static final class StubLibrarianService implements LibrarianService {

    private @Nullable Recipe savedRecipe;
    private final List<RecipeCollection> collections;

    StubLibrarianService(List<RecipeCollection> collections) {
      this.collections = collections;
    }

    @Nullable Recipe getSavedRecipe() {
      return savedRecipe;
    }

    @Override
    public List<RecipeCollection> listCollections() {
      return collections;
    }

    @Override
    public void saveRecipe(Recipe recipe, String collectionId) {
      this.savedRecipe = recipe;
    }

    // ── Unused interface methods ──────────────────────────────────────────

    @Override
    public RecipeCollection createCollection(String name) {
      throw new UnsupportedOperationException();
    }

    @Override
    public RecipeCollection createCollection(
        String name, app.cookyourbooks.model.SourceType sourceType) {
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
    public Recipe importFromJson(Path file, String collectionName) {
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

  /** A minimal RecipeCollection stub used by StubLibrarianService. */
  private static final class StubCollection implements RecipeCollection {
    private final String id;
    private final String title;

    StubCollection(String id, String title) {
      this.id = id;
      this.title = title;
    }

    @Override
    public String getId() {
      return id;
    }

    @Override
    public String getTitle() {
      return title;
    }

    @Override
    public app.cookyourbooks.model.SourceType getSourceType() {
      return app.cookyourbooks.model.SourceType.PERSONAL;
    }

    @Override
    public List<Recipe> getRecipes() {
      return List.of();
    }

    @Override
    public java.util.Optional<Recipe> findRecipeById(String recipeId) {
      return java.util.Optional.empty();
    }

    @Override
    public boolean containsRecipe(String recipeId) {
      return false;
    }

    @Override
    public RecipeCollection addRecipe(Recipe recipe) {
      return this;
    }

    @Override
    public RecipeCollection removeRecipe(String recipeId) {
      return this;
    }
  }

  // ── Fixtures ──────────────────────────────────────────────────────────────

  private StubLibrarianService librarian;
  private ImportViewModelImpl vm;

  @BeforeEach
  void setUp() {
    librarian =
        new StubLibrarianService(
            List.of(
                new StubCollection("col-1", "My Recipes"),
                new StubCollection("col-2", "Favorites")));
    vm = new ImportViewModelImpl(fastOcr, librarian);
  }

  // ── I1: Initial state ─────────────────────────────────────────────────────

  @Test
  void i1_initialStateIsIdle() {
    assertThat(vm.getState()).isEqualTo("idle");
    assertThat(vm.getStatusMessage()).isEqualTo("Ready");
    assertThat(vm.getErrorMessage()).isNull();
    assertThat(vm.getImportedRecipeTitle()).isNull();
    assertThat(vm.getImportedIngredientNames()).isEmpty();
  }

  // ── I2: startImport transitions to processing ─────────────────────────────

  @Test
  void i2_startImportTransitionsToProcessing() throws InterruptedException {
    // Use a slow OCR service so we can observe PROCESSING before it completes
    var slowVm = new ImportViewModelImpl(new FakeRecipeOcrService(5000), librarian);
    slowVm.startImport(Path.of("pancakes.jpg"));

    assertThat(slowVm.getState()).isEqualTo("processing");
    assertThat(slowVm.getStatusMessage()).isEqualTo("Extracting recipe...");

    // Clean up the background task
    slowVm.cancelImport();
  }

  // ── I3: Successful OCR → review ───────────────────────────────────────────

  @Test
  void i3_successfulOcrTransitionsToReview() throws InterruptedException {
    vm.startImport(Path.of("pancakes.jpg"));
    awaitState(vm, "review");

    assertThat(vm.getState()).isEqualTo("review");
    assertThat(vm.getImportedRecipeTitle()).isNotBlank();
    assertThat(vm.getImportedIngredientNames()).isNotEmpty();
    assertThat(vm.getStatusMessage()).isEqualTo("Review the extracted recipe");
  }

  // ── I4: OCR failure → error ───────────────────────────────────────────────

  @Test
  void i4_ocrFailureTransitionsToError() throws InterruptedException {
    var failVm = new ImportViewModelImpl(failingOcr, librarian);
    failVm.startImport(Path.of("bad.jpg"));
    awaitState(failVm, "error");

    assertThat(failVm.getState()).isEqualTo("error");
    assertThat(failVm.getErrorMessage()).isEqualTo("network timeout");
    assertThat(failVm.getStatusMessage()).isEqualTo("Import failed");
  }

  // ── I5: cancelImport → idle ───────────────────────────────────────────────

  @Test
  void i5_cancelImportTransitionsToIdle() throws InterruptedException {
    var slowVm = new ImportViewModelImpl(new FakeRecipeOcrService(5000), librarian);
    slowVm.startImport(Path.of("pancakes.jpg"));
    assertThat(slowVm.getState()).isEqualTo("processing");

    slowVm.cancelImport();
    assertThat(slowVm.getState()).isEqualTo("idle");
    assertThat(slowVm.getStatusMessage()).isEqualTo("Import cancelled");
  }

  // ── I6: acceptImport transitions to SUCCESS ───────────────────────────────
  // NOTE: acceptImport() no longer calls librarianService.saveRecipe() directly.
  // Saving is handled by the embedded RecipeEditorViewModelImpl.saveDraft(). acceptImport()
  // is called by ImportViewController AFTER the editor saves successfully. It now goes to
  // SUCCESS (not IDLE) so the user sees a confirmation pane with navigation options.

  @Test
  void i6_acceptImportTransitionsToSuccess() throws InterruptedException {
    vm.startImport(Path.of("pasta.jpg"));
    awaitState(vm, "review");

    assertThat(vm.getState()).isEqualTo("review");
    vm.selectTargetCollection("col-1");
    vm.acceptImport();

    assertThat(vm.getState()).isEqualTo("success");
    assertThat(vm.getStatusMessage()).isEqualTo("Recipe imported successfully");
    // saveRecipe is not called here — the embedded editor handles persistence.
    assertThat(librarian.getSavedRecipe()).isNull();
  }

  // ── I11: returnToLibrary resets to IDLE ───────────────────────────────────
  // After the confirmation pane is shown (SUCCESS state), the user can click
  // "Return to Library" or "Import Another". Both call vm.returnToLibrary() which
  // resets the state to IDLE. Navigation to the Library view is handled in the
  // controller (not the ViewModel), so we only test the state transition here.

  @Test
  void i11_returnToLibraryResetsToIdle() throws InterruptedException {
    vm.startImport(Path.of("pasta.jpg"));
    awaitState(vm, "review");
    vm.acceptImport(); // → SUCCESS

    assertThat(vm.getState()).isEqualTo("success");
    vm.returnToLibrary();

    assertThat(vm.getState()).isEqualTo("idle");
    assertThat(vm.getStatusMessage()).isEqualTo("Ready");
  }

  // ── I7: rejectImport discards and returns to idle ─────────────────────────

  @Test
  void i7_rejectImportDiscardsAndTransitionsToIdle() throws InterruptedException {
    vm.startImport(Path.of("pasta.jpg"));
    awaitState(vm, "review");

    assertThat(vm.getState()).isEqualTo("review");
    vm.rejectImport();

    assertThat(vm.getState()).isEqualTo("idle");
    assertThat(vm.getStatusMessage()).isEqualTo("Import discarded");
    assertThat(vm.getImportedRecipeTitle()).isNull();
    assertThat(vm.getImportedIngredientNames()).isEmpty();
    assertThat(librarian.getSavedRecipe()).isNull(); // nothing was saved
  }

  // ── I8: loadCollections populates available collections ───────────────────

  @Test
  void i8_loadCollectionsPopulatesAvailableCollections() {
    vm.loadCollections();

    assertThat(vm.getAvailableCollectionIds()).containsExactly("col-1", "col-2");
  }

  // ── I9: title/ingredients editable in review state ────────────────────────

  @Test
  void i9_titleAndIngredientsAreEditableInReviewState() throws InterruptedException {
    vm.startImport(Path.of("pasta.jpg"));
    awaitState(vm, "review");

    assertThat(vm.getState()).isEqualTo("review");

    // Edit title via the observable property
    vm.importedTitleProperty().set("My Custom Title");
    assertThat(vm.getImportedRecipeTitle()).isEqualTo("My Custom Title");

    // Add an ingredient
    vm.importedIngredientsProperty().add(new javafx.beans.property.SimpleStringProperty("garlic"));
    assertThat(vm.getImportedIngredientNames()).contains("garlic");
  }

  // ── I10: acceptImport is a pure state transition ──────────────────────────
  // Design change: acceptImport() no longer validates the collection ID itself.
  // Collection validation (and saving) is the responsibility of the embedded
  // RecipeEditorViewModelImpl.saveDraft() — called from ImportViewController only after
  // the editor reports a successful save. acceptImport() is therefore a pure REVIEW→IDLE
  // state transition, callable without a collection ID. The controller is responsible for
  // never calling it unless a save has already succeeded.

  @Test
  void i10_acceptImportTransitionsToSuccessRegardlessOfCollection() throws InterruptedException {
    vm.startImport(Path.of("pasta.jpg"));
    awaitState(vm, "review");

    assertThat(vm.getState()).isEqualTo("review");
    // No selectTargetCollection call — acceptImport() is a pure state transition to SUCCESS.
    vm.acceptImport();

    assertThat(vm.getState()).isEqualTo("success");
    assertThat(vm.getStatusMessage()).isEqualTo("Recipe imported successfully");
    // No save was performed — librarian is untouched.
    assertThat(librarian.getSavedRecipe()).isNull();
  }
}
