package app.cookyourbooks.gui.viewmodel;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;

import org.jspecify.annotations.Nullable;

import app.cookyourbooks.gui.BackgroundTaskRunner;
import app.cookyourbooks.model.Instruction;
import app.cookyourbooks.model.Recipe;
import app.cookyourbooks.services.LibrarianService;
import app.cookyourbooks.services.ocr.RecipeOcrService;

/**
 * ViewModel for the Import Interface feature.
 *
 * <p>Manages a four-state machine for OCR-based recipe import. Each state maps to one visible pane
 * in the View:
 *
 * <pre>
 *   IDLE ──startImport()──▶ PROCESSING ──(success)──▶ REVIEW ──acceptImport()──▶ SUCCESS
 *                                │                       │                            │
 *                                │                       └──rejectImport()──▶ IDLE    │
 *                                └──(failure)──▶ ERROR ──resetToIdle()──▶ IDLE        │
 *                                └──cancelImport()──▶ IDLE          returnToLibrary()─┘──▶ IDLE
 * </pre>
 *
 * <h2>Threading model</h2>
 *
 * <p>OCR is a potentially slow network/I-O call. Running it on the JavaFX Application Thread would
 * freeze the entire UI until it completes. Instead, {@link BackgroundTaskRunner#run} puts the OCR
 * call on a daemon background thread. The {@code onSuccess} and {@code onFailure} callbacks are
 * automatically posted back onto the FX Application Thread, which is the only thread allowed to
 * mutate {@link javafx.beans.property.Property} values that are bound to live UI nodes. Violating
 * this rule throws an {@link IllegalStateException} at runtime.
 *
 * <h2>Why use an enum instead of boolean flags?</h2>
 *
 * <p>States are mutually exclusive — the view should show exactly one pane at a time. If we used
 * booleans (e.g. {@code isProcessing}, {@code isReview}) we'd need to keep them in sync manually
 * and could accidentally set two to {@code true}. The enum makes illegal combinations structurally
 * impossible and lets the View bind all four panes to a single property.
 *
 * <h2>Why accept the concrete service types in the constructor?</h2>
 *
 * <p>Constructor injection (rather than a service locator or static factory) makes the ViewModel
 * trivially testable: tests just pass stub/fake implementations. The production app wires in real
 * implementations in {@code CookYourBooksGuiApp}.
 */
public class ImportViewModelImpl implements ImportViewModel {

  // ── State enum ───────────────────────────────────────────────────────────

  /**
   * The four mutually-exclusive workflow states.
   *
   * <p>Each state corresponds to one visible pane in {@code ImportView.fxml}. The View binds each
   * pane's {@code visible} and {@code managed} properties to an equality check against this enum
   * value via {@link javafx.beans.binding.Bindings#equal}.
   *
   * <ul>
   *   <li>{@code IDLE} — initial state; user picks a file and collection
   *   <li>{@code PROCESSING} — OCR is running on background thread; spinner is shown
   *   <li>{@code REVIEW} — OCR succeeded; user edits via embedded Recipe Editor before saving
   *   <li>{@code SUCCESS} — recipe saved; confirmation + "Return to Library" / "Import Another"
   *   <li>{@code ERROR} — OCR threw an exception; error message is shown with a "Try Again" button
   * </ul>
   */
  public enum ImportState {
    IDLE,
    PROCESSING,
    REVIEW,
    SUCCESS,
    ERROR
  }

  /**
   * Entry type used by the View's ComboBox to display available recipe collections.
   *
   * <p>A {@code record} is used instead of a full class because it is immutable data with no
   * behavior — Java records auto-generate the constructor, getters, {@code equals}, {@code
   * hashCode}, and {@code toString}. The View uses {@code title()} for display text and {@code
   * id()} when calling {@link #selectTargetCollection}.
   *
   * <p>This is declared public so that {@link app.cookyourbooks.gui.view.ImportViewController} can
   * reference it in the ComboBox {@link javafx.util.StringConverter} and selection listener.
   */
  public record CollectionEntry(String id, String title) {}

  // ── Observable state ─────────────────────────────────────────────────────

  /**
   * The current workflow state. The View binds all four panes' visibility to this property.
   *
   * <p>Declared as an {@link ObjectProperty} so the View can use {@link
   * javafx.beans.binding.Bindings#equal} against a specific {@link ImportState} value. Initialized
   * to {@link ImportState#IDLE} so the idle pane is visible on first load.
   */
  private final ObjectProperty<ImportState> state = new SimpleObjectProperty<>(ImportState.IDLE);

  /**
   * Human-readable status message shown in the processing pane's status label (and optionally in
   * other panes). Changes at each state transition to reflect what just happened: "Extracting
   * recipe…", "Review the extracted recipe", "Import cancelled", etc.
   */
  private final StringProperty statusMessage = new SimpleStringProperty("Ready");

  /**
   * Error message from a failed OCR call. Set in the {@code onFailure} callback of {@link
   * BackgroundTaskRunner#run} when {@link RecipeOcrService#extractRecipe} throws. Cleared on state
   * transitions that leave the ERROR state (resetToIdle, acceptImport, rejectImport). The View
   * binds the error pane's label to this property.
   */
  private final StringProperty errorMessage = new SimpleStringProperty();

  /**
   * The recipe title extracted by OCR and displayed in the review pane's {@link
   * javafx.scene.control.TextField}. The View uses a bidirectional binding so that edits the user
   * makes in the text field are reflected here in real time, and the title written to the library
   * comes from this property (not a snapshot).
   */
  private final StringProperty importedTitle = new SimpleStringProperty();

  /**
   * The list of extracted ingredient names shown in the review pane's ListView. Each element is a
   * {@link SimpleStringProperty} so that the {@link
   * app.cookyourbooks.gui.view.ImportViewController.IngredientEditCell} can bind its TextField
   * bidirectionally to the property, letting the user edit ingredient names inline.
   *
   * <p>Using an {@link ObservableList} means the ListView auto-refreshes whenever the list changes
   * — no manual repaint needed.
   */
  private final ObservableList<SimpleStringProperty> importedIngredients =
      FXCollections.observableArrayList();

  // adding to help import instructions
  private final ObservableList<Instruction> importedInstructions =
      FXCollections.observableArrayList();

  /**
   * The list of recipe collections available to save into, populated from {@link
   * LibrarianService#listCollections()} by {@link #loadCollections()}.
   *
   * <p>Both the idle-pane and review-pane ComboBoxes share this exact same list instance. When the
   * list is updated (e.g. after a new collection is created), both ComboBoxes automatically reflect
   * the change.
   */
  private final ObservableList<CollectionEntry> availableCollections =
      FXCollections.observableArrayList();

  // ── Internal mutable state ───────────────────────────────────────────────

  /**
   * Handle to the currently running OCR {@link Task}, or {@code null} when no task is in flight.
   * Stored so that {@link #cancelImport()} can call {@link Task#cancel()} on it.
   *
   * <p><strong>Important:</strong> {@code Task.cancel()} triggers the task's {@code onCancelled}
   * handler, NOT {@code onFailed}. Because {@link BackgroundTaskRunner} only wires {@code
   * onSuccess} and {@code onFailure}, a cancellation does NOT call either callback. That is why
   * {@link #cancelImport()} must manually update the state instead of relying on the callbacks.
   */
  private @Nullable Task<?> currentTask;

  /**
   * The ID of the collection chosen by the user in the ComboBox, or {@code null} if no selection
   * has been made. Set by {@link #selectTargetCollection}. Used by {@link #acceptImport()} to
   * determine where to save the recipe. If null when the user clicks "Save Recipe", {@code
   * acceptImport} is a no-op (I10 requirement).
   */
  private @Nullable String selectedCollectionId;

  // ── Injected services ────────────────────────────────────────────────────

  /**
   * The OCR service that extracts a {@link Recipe} from an image file. In production this is {@code
   * GeminiOcrAdapter}; in development and tests it is {@link
   * app.cookyourbooks.services.ocr.FakeRecipeOcrService} so we don't need a live Gemini API key.
   * The ViewModel never knows (or cares) which implementation is injected.
   */
  private final RecipeOcrService ocrService;

  /**
   * Shared service used to list collections and persist the imported recipe. The same {@code
   * LibrarianServiceImpl} instance is shared with the Library View and Recipe Editor ViewModels —
   * they all operate on the same underlying data store.
   */
  private final LibrarianService librarianService;

  // ── Constructor ──────────────────────────────────────────────────────────

  /**
   * Creates an {@code ImportViewModelImpl}.
   *
   * <p>Both services are injected rather than looked up statically, which makes it easy to pass
   * stubs or fakes in unit tests without needing a full application context.
   *
   * @param ocrService the OCR service used to extract a recipe from an image
   * @param librarianService the librarian service used to list collections and save recipes
   */
  public ImportViewModelImpl(RecipeOcrService ocrService, LibrarianService librarianService) {
    this.ocrService = ocrService;
    this.librarianService = librarianService;
  }

  // ── Extra property exposed to the View (not in the ImportViewModel interface) ──

  /**
   * Returns the current workflow state as a read-only JavaFX property.
   *
   * <p>This method is NOT part of the {@link ImportViewModel} interface. It is exposed here
   * specifically so that {@link app.cookyourbooks.gui.view.ImportViewController} can bind each
   * pane's {@code visible}/{@code managed} properties to an equality check:
   *
   * <pre>{@code
   * BooleanBinding isIdle = Bindings.equal(vm.stateProperty(), ImportState.IDLE);
   * idlePane.visibleProperty().bind(isIdle);
   * }</pre>
   *
   * <p>The {@link ImportViewModel} interface exposes only {@link #getState()} (a plain {@link
   * String}) so that grading tests don't depend on JavaFX classes. The controller accepts the
   * concrete {@code ImportViewModelImpl} type so it can call this method.
   *
   * @return a read-only view of the state property
   */
  public ReadOnlyObjectProperty<ImportState> stateProperty() {
    return state;
  }

  // ── ImportViewModel: observable properties ───────────────────────────────

  /**
   * @return the status message property, bound to the processing pane's status label
   */
  @Override
  public StringProperty statusMessageProperty() {
    return statusMessage;
  }

  /**
   * @return the error message property, bound to the error pane's error label
   */
  @Override
  public StringProperty errorMessageProperty() {
    return errorMessage;
  }

  /**
   * @return the imported recipe title property; the review pane's TextField is bound
   *     bidirectionally to this so that user edits are immediately reflected in the VM
   */
  @Override
  public StringProperty importedTitleProperty() {
    return importedTitle;
  }

  // getter for instructions to display in recipe editor
  public ObservableList<Instruction> importedInstructionsProperty() {
    return importedInstructions;
  }

  /**
   * @return the observable list of extracted ingredient names as {@link SimpleStringProperty}
   *     objects; the ListView's IngredientEditCell binds bidirectionally to each element
   */
  @Override
  public ObservableList<SimpleStringProperty> importedIngredientsProperty() {
    return importedIngredients;
  }

  /**
   * @return the observable list of available collection entries; shared by both ComboBoxes so a
   *     single list update refreshes both panes simultaneously
   */
  @Override
  public ObservableList<CollectionEntry> availableCollectionsProperty() {
    return availableCollections;
  }

  // ── ImportViewModel: commands ─────────────────────────────────────────────

  /**
   * Loads the list of available collections from the librarian service and populates {@link
   * #availableCollectionsProperty()}.
   *
   * <p>{@code listCollections()} is synchronous (reads from an in-memory repository) so it is safe
   * to call on the FX Application Thread without freezing the UI. The result is mapped to {@link
   * CollectionEntry} records (id + title) so the View's ComboBox StringConverter only needs to call
   * {@code entry.title()} for display.
   *
   * <p>Called from {@link app.cookyourbooks.gui.view.ImportViewController#initialize()} so the
   * ComboBoxes are already populated when the user first sees the idle pane.
   */
  @Override
  public void loadCollections() {
    // Map each RecipeCollection → lightweight CollectionEntry (id + title).
    // The ComboBox only needs the title for display and the id for saving.
    List<CollectionEntry> entries =
        librarianService.listCollections().stream()
            .map(c -> new CollectionEntry(c.getId(), c.getTitle()))
            .toList();
    availableCollections.setAll(entries);
  }

  /**
   * Starts the OCR import workflow for the given image file.
   *
   * <p>Transitions: IDLE → PROCESSING (immediately), then either → REVIEW (on OCR success) or →
   * ERROR (on OCR failure). Both follow-up transitions happen asynchronously on the FX Application
   * Thread via the BackgroundTaskRunner callbacks.
   *
   * <p>Guard: if the state is NOT IDLE, this is a no-op. This prevents double-starts if the user
   * somehow triggers the action twice (e.g. rapid keyboard shortcuts).
   *
   * <p>The OCR call is wrapped in a {@link Task} by {@link BackgroundTaskRunner#run}. This puts the
   * potentially slow network I/O on a daemon thread so the JavaFX UI stays responsive (the spinner
   * animates, Cancel button is clickable).
   *
   * @param imagePath path to the image file to process with OCR
   */
  @Override
  public void startImport(Path imagePath) {
    // Guard: only start from IDLE. Prevents re-entrancy if called twice.
    if (state.get() != ImportState.IDLE) {
      return;
    }

    // Immediately transition to PROCESSING so the spinner pane appears.
    state.set(ImportState.PROCESSING);
    statusMessage.set("Extracting recipe...");
    errorMessage.set(null); // clear any previous error message from a prior failed attempt

    // Launch OCR on a background (daemon) thread.
    // The lambda () -> ocrService.extractRecipe(imagePath) runs OFF the FX thread.
    // onSuccess and onFailure are automatically posted BACK onto the FX thread by
    // BackgroundTaskRunner, so it is safe to mutate observable properties inside them.
    currentTask =
        BackgroundTaskRunner.run(
            () -> ocrService.extractRecipe(imagePath),
            recipe -> {
              // onSuccess — runs on FX Application Thread
              // Populate title and ingredients from the OCR result, then move to REVIEW.
              populateReviewState(recipe);
            },
            error -> {
              // onFailure — runs on FX Application Thread
              // Store the error message and move to ERROR so the error pane appears.
              errorMessage.set(error.getMessage());
              state.set(ImportState.ERROR);
              statusMessage.set("Import failed");
            });
  }

  /**
   * Cancels the in-progress OCR and returns to the IDLE state.
   *
   * <p>Guard: only valid in PROCESSING state. A no-op otherwise.
   *
   * <p><strong>Why not rely on BackgroundTaskRunner's onFailure callback?</strong> {@link
   * Task#cancel()} fires the task's {@code onCancelled} handler, not {@code onFailed}. Because
   * {@link BackgroundTaskRunner} only connects {@code onSuccess} and {@code onFailure}, a cancelled
   * task never calls either callback. Therefore this method must manually set the state back to
   * IDLE — it cannot wait for a callback that will never arrive.
   */
  @Override
  public void cancelImport() {
    // Guard: can only cancel while an OCR task is actually running.
    if (state.get() != ImportState.PROCESSING) {
      return;
    }

    // Cancel the background task (fires onCancelled, NOT onFailed — see javadoc above).
    if (currentTask != null) {
      currentTask.cancel();
      currentTask = null;
    }

    // Manually transition back to IDLE. This is intentional — do NOT move this into a
    // callback, because the BackgroundTaskRunner callbacks are not called on cancellation.
    state.set(ImportState.IDLE);
    statusMessage.set("Import cancelled");
  }

  /**
   * Saves the reviewed recipe to the selected collection and returns to IDLE.
   *
   * <p>Guard (I10): no-op if not in REVIEW state, if no collection has been selected, or if the
   * title is blank. This prevents saving a half-formed recipe.
   *
   * <p><strong>Why {@code librarianService.saveRecipe()} and not {@code
   * recipeRepository.save()}?</strong> {@code saveRecipe(recipe, collectionId)} is a higher-level
   * operation that both persists the recipe in the repository AND adds it to the specified
   * collection. {@code recipeRepository.save()} only updates the recipe data — it does not handle
   * collection membership. Using the wrong method would persist the recipe but it would never
   * appear in the target collection.
   *
   * <p><strong>VagueIngredient simplification:</strong> OCR gives us ingredient names as plain
   * strings with no measured quantities. We wrap each name in a {@link VagueIngredient} (amount =
   * null, unit = null, preparation = null) which represents an ingredient whose quantity is not yet
   * known. The spec explicitly accepts this simplification.
   */
  /**
   * Transitions from REVIEW back to IDLE.
   *
   * <p>Saving is handled by the embedded {@link
   * app.cookyourbooks.gui.viewmodel.RecipeEditorViewModelImpl} instance in the REVIEW pane. {@link
   * app.cookyourbooks.gui.view.ImportViewController} listens to the editor's {@code
   * statusMessageProperty} and calls this method after the editor reports "Saved successfully." —
   * so by the time this is called, the recipe is already persisted.
   *
   * <p>Guard: no-op if not in REVIEW state.
   */
  @Override
  public void acceptImport() {
    // No-op if not in REVIEW state (guards against spurious calls from the status listener).
    if (state.get() != ImportState.REVIEW) {
      return;
    }
    // Saving was already handled by the embedded RecipeEditorViewModelImpl.saveDraft().
    // Transition to SUCCESS so the confirmation pane is shown with a "Return to Library" button.
    clearReviewState();
    state.set(ImportState.SUCCESS);
    statusMessage.set("Recipe imported successfully");
  }

  /**
   * Returns from the SUCCESS state back to IDLE. Called by both the "Import Another" button (stays
   * on Import, no navigation) and the "Return to Library" button in the controller (which
   * additionally calls {@link app.cookyourbooks.gui.NavigationService#navigateTo} for the View).
   *
   * <p>Guard: no-op if not in SUCCESS state.
   */
  public void returnToLibrary() {
    if (state.get() != ImportState.SUCCESS) {
      return;
    }
    state.set(ImportState.IDLE);
    statusMessage.set("Ready");
  }

  /**
   * Discards the OCR result without saving and returns to IDLE.
   *
   * <p>Guard: no-op if not in REVIEW state. Clears title and ingredients so the next import starts
   * fresh.
   */
  @Override
  public void rejectImport() {
    if (state.get() != ImportState.REVIEW) {
      return;
    }
    clearReviewState();
    state.set(ImportState.IDLE);
    statusMessage.set("Import discarded");
  }

  /**
   * Records the collection the user has chosen as the save destination.
   *
   * <p>Called by the ComboBox selection listener in the controller whenever the user picks a
   * collection in either the idle pane or the review pane. The value is used by {@link
   * #acceptImport()} to determine which collection to pass to {@code saveRecipe}.
   *
   * @param collectionId the ID of the selected collection
   */
  @Override
  public void selectTargetCollection(String collectionId) {
    this.selectedCollectionId = collectionId;
  }

  /**
   * Resets from the ERROR state back to IDLE. Bound to the "Try Again" button in the error pane.
   * Not part of the {@link ImportViewModel} interface because the interface is the grading contract
   * and does not need to know about error-recovery UI details.
   */
  public void resetToIdle() {
    errorMessage.set(null);
    state.set(ImportState.IDLE);
    statusMessage.set("Ready");
  }

  // ── ImportViewModel: non-JavaFX accessors (used by grading tests) ─────────

  /**
   * Returns the current state as a lower-case string (e.g. {@code "idle"}, {@code "processing"}).
   *
   * <p>The {@link ImportViewModel} interface exposes this as a plain {@link String} — not as a
   * {@link ReadOnlyObjectProperty} — so grading tests can assert on it without importing any JavaFX
   * classes. The implementation delegates to the enum and lower-cases the name.
   *
   * @return current state name in lower case
   */
  @Override
  public String getState() {
    // Locale.ROOT ensures consistent lower-casing regardless of the system locale
    // (e.g., avoids the Turkish "I" problem where toUpperCase/toLowerCase can produce
    // unexpected results in certain locales).
    return state.get().name().toLowerCase(Locale.ROOT);
  }

  /**
   * @return the current status message (e.g. "Ready", "Extracting recipe…")
   */
  @Override
  public String getStatusMessage() {
    return statusMessage.get();
  }

  /**
   * @return the error message from the most recent failed OCR call, or {@code null} if no error has
   *     occurred (or after the error was cleared)
   */
  @Override
  public @Nullable String getErrorMessage() {
    return errorMessage.get();
  }

  /**
   * @return the recipe title currently in the review pane's title field, or {@code null} if not in
   *     REVIEW state (or if OCR produced no title)
   */
  @Override
  public @Nullable String getImportedRecipeTitle() {
    return importedTitle.get();
  }

  /**
   * @return a snapshot list of ingredient name strings currently shown in the review pane; empty
   *     list if not in REVIEW state
   */
  @Override
  public List<String> getImportedIngredientNames() {
    // Map each observable SimpleStringProperty to its current string value.
    return importedIngredients.stream().map(StringProperty::get).toList();
  }

  /**
   * @return the IDs of all collections currently in the ComboBox; used by grading tests to verify
   *     that {@link #loadCollections()} populated the list correctly
   */
  @Override
  public List<String> getAvailableCollectionIds() {
    return availableCollections.stream().map(CollectionEntry::id).toList();
  }

  /**
   * @return the ID of the collection last selected by the user, or {@code null} if no selection has
   *     been made
   */
  @Override
  public @Nullable String getSelectedCollectionId() {
    return selectedCollectionId;
  }

  // ── Private helpers ───────────────────────────────────────────────────────

  /**
   * Populates the review-pane data from the OCR result and transitions to REVIEW state.
   *
   * <p>Called from the {@code onSuccess} callback of {@link BackgroundTaskRunner#run}, which is
   * guaranteed to run on the FX Application Thread. It is therefore safe to mutate the observable
   * properties here.
   *
   * @param recipe the recipe extracted by the OCR service
   */
  private void populateReviewState(Recipe recipe) {
    // Set the editable title field to the OCR-extracted title.
    importedTitle.set(recipe.getTitle());
    importedInstructions.setAll(recipe.getInstructions());

    // Convert each Ingredient object to a SimpleStringProperty holding just the name.
    // The user can then edit the name strings directly in the review pane's ListView.
    // (Quantity, unit, and preparation data from OCR is discarded here — the spec accepts this.)
    List<SimpleStringProperty> ingredientProps =
        recipe.getIngredients().stream().map(i -> new SimpleStringProperty(i.getName())).toList();
    importedIngredients.setAll(ingredientProps);

    // Transition to REVIEW so the review pane becomes visible.
    state.set(ImportState.REVIEW);
    statusMessage.set("Review the extracted recipe");
  }

  /**
   * Clears all review-pane data. Called by both {@link #acceptImport()} and {@link #rejectImport()}
   * so the next import starts from a clean slate.
   */
  private void clearReviewState() {
    importedTitle.set(null);
    importedIngredients.clear();
    importedInstructions.clear();
    errorMessage.set(null);
  }
}
