package app.cookyourbooks.gui.view;

import java.io.File;
import java.util.List;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.util.StringConverter;

import org.jspecify.annotations.Nullable;

import app.cookyourbooks.gui.NavigationService;
import app.cookyourbooks.gui.viewmodel.ImportViewModelImpl;
import app.cookyourbooks.gui.viewmodel.ImportViewModelImpl.CollectionEntry;
import app.cookyourbooks.gui.viewmodel.ImportViewModelImpl.ImportState;
import app.cookyourbooks.gui.viewmodel.RecipeEditorViewModelImpl;
import app.cookyourbooks.model.Ingredient;
import app.cookyourbooks.model.Recipe;
import app.cookyourbooks.model.VagueIngredient;

/**
 * FXML controller for {@code ImportView.fxml}.
 *
 * <p>This class is the "View" in the MVVM pattern. Its only responsibility is to:
 *
 * <ol>
 *   <li>Inject all FXML nodes via {@code @FXML} fields
 *   <li>In {@link #initialize()}, bind every UI element to the corresponding ViewModel property or
 *       command
 *   <li>Forward user actions (button clicks, file selection) to the ViewModel — never perform
 *       business logic here
 * </ol>
 *
 * <h2>Why accept the concrete {@code ImportViewModelImpl} and not the interface?</h2>
 *
 * <p>The {@link app.cookyourbooks.gui.viewmodel.ImportViewModel} interface exposes only plain-Java
 * accessors (no JavaFX properties) so that grading tests don't depend on JavaFX classes. The View
 * needs two things the interface doesn't provide:
 *
 * <ul>
 *   <li>{@link ImportViewModelImpl#stateProperty()} — a {@code ReadOnlyObjectProperty<ImportState>}
 *       needed to create {@link BooleanBinding}s for pane visibility
 *   <li>{@link CollectionEntry} — the inner record type used by the ComboBox
 * </ul>
 *
 * <p>Accepting the concrete type is a deliberate trade-off: the View is tightly coupled to its
 * ViewModel (that's expected in MVVM), but the ViewModel stays independently testable.
 *
 * <h2>Pane visibility pattern</h2>
 *
 * <p>Both {@code visible} and {@code managed} are bound to the same state condition on every pane.
 * {@code visible=false} hides the node but it still occupies its layout space. {@code
 * managed=false} removes it from layout entirely, so sibling nodes collapse into the freed space.
 * Both bindings are required — setting only {@code visible} leaves invisible "ghost" whitespace
 * where the hidden pane was.
 */
@SuppressWarnings("NullAway.Init") // @FXML fields are injected by FXMLLoader after construction
public class ImportViewController {

  // ── FXML injected nodes ───────────────────────────────────────────────────
  // FXMLLoader matches these field names to fx:id attributes in ImportView.fxml.
  // They are null until initialize() is called, which is why NullAway.Init is suppressed.

  /** The idle pane — visible only in IDLE state. Contains file chooser and collection picker. */
  @FXML private VBox idlePane;

  /** The processing pane — visible only in PROCESSING state. Contains spinner and cancel button. */
  @FXML private VBox processingPane;

  /**
   * The review pane — visible only in REVIEW state. Contains the embedded Recipe Editor and a
   * Discard button.
   */
  @FXML private VBox reviewPane;

  /**
   * The error pane — visible only in ERROR state. Contains error message and "Try Again" button.
   */
  @FXML private VBox errorPane;

  /** ComboBox in the idle pane for selecting the target collection before starting import. */
  @FXML private ComboBox<CollectionEntry> collectionComboBox;

  /** Button that opens the OS file chooser dialog to pick a recipe image. */
  @FXML private Button selectFileButton;

  /**
   * Warning label shown in the idle pane when no collection has been selected yet. Visibility is
   * bound in {@link #bindCollectionCombos()} — hidden once the user picks a collection.
   */
  @FXML private Label collectionHintLabel;

  /**
   * Label in the processing pane that shows the current status message (e.g. "Extracting
   * recipe..."). Bound to {@link ImportViewModelImpl#statusMessageProperty()}.
   */
  @FXML private Label statusLabel;

  /** Button that cancels the in-progress OCR and returns to the idle pane. */
  @FXML private Button cancelButton;

  /**
   * Container in the review pane where the embedded RecipeEditorView node is inserted at runtime.
   * Declared in the FXML as an empty VBox with fx:id="editorContainer" — the actual Recipe Editor
   * view is added as a child in {@link #initialize()} so the FXML stays static and layout-friendly.
   */
  @FXML private VBox editorContainer;

  /**
   * The success pane — visible only in SUCCESS state. Shown after the embedded editor saves.
   * Contains a confirmation message and navigation/action buttons.
   */
  @FXML private VBox successPane;

  /**
   * Navigates to the Library view AND resets the Import VM to IDLE. Wired in {@link #bindButtons()}
   * to call both {@link ImportViewModelImpl#returnToLibrary()} and {@link
   * NavigationService#navigateTo}.
   */
  @FXML private Button returnToLibraryButton;

  /**
   * Resets the Import VM to IDLE without navigating away — the user stays on the Import view to
   * import another recipe image immediately.
   */
  @FXML private Button importAnotherButton;

  /**
   * Label in the error pane showing the exception message from the failed OCR call. Bound to {@link
   * ImportViewModelImpl#errorMessageProperty()}.
   */
  @FXML private Label errorLabel;

  /** Button in the error pane that calls {@link ImportViewModelImpl#resetToIdle()}. */
  @FXML private Button retryButton;

  // ── ViewModels & embedded view ────────────────────────────────────────────

  /**
   * The Import ViewModel. Drives pane visibility, OCR state, and collection selection. Injected via
   * constructor — the controller has no knowledge of how it is constructed.
   */
  private final ImportViewModelImpl vm;

  /**
   * The Recipe Editor ViewModel for the embedded editor that appears in the REVIEW pane. A separate
   * instance from the main Recipe Editor ViewModel — this one is pre-populated via {@link
   * RecipeEditorViewModelImpl#loadDraft(Recipe, String)} when OCR completes and the user enters the
   * REVIEW state.
   */
  private final RecipeEditorViewModelImpl editorVm;

  /**
   * The loaded RecipeEditorView FXML node, constructed in {@link
   * app.cookyourbooks.gui.CookYourBooksGuiApp} and passed in here. Embedded inside {@link
   * #editorContainer} during {@link #initialize()}. Passing it as a constructor argument (rather
   * than loading it here) keeps controller construction simple and allows CookYourBooksGuiApp to
   * wire the navigation service.
   */
  private final Parent editorView;

  /**
   * Used by the "Return to Library" button to navigate to the Library view after a successful
   * import. Navigation is handled in the View controller (not the ViewModel) because it is a
   * routing concern — the ViewModel should not depend on UI navigation.
   */
  private final NavigationService navigationService;

  /**
   * Creates the controller with the Import ViewModel, embedded editor ViewModel, pre-loaded editor
   * view node, and navigation service.
   *
   * <p>FXMLLoader calls this constructor via the {@code setControllerFactory} lambda in {@link
   * app.cookyourbooks.gui.CookYourBooksGuiApp}. At construction time the {@code @FXML} fields have
   * NOT been injected yet — they are populated by FXMLLoader after the constructor returns, before
   * {@link #initialize()} is called.
   *
   * @param vm the Import ViewModel (needed for stateProperty() and CollectionEntry)
   * @param editorVm the Recipe Editor ViewModel used by the embedded editor in REVIEW state
   * @param editorView the pre-loaded RecipeEditorView FXML node to embed in the review pane
   * @param navigationService used by "Return to Library" to navigate to the Library view
   */
  public ImportViewController(
      ImportViewModelImpl vm,
      RecipeEditorViewModelImpl editorVm,
      Parent editorView,
      NavigationService navigationService) {
    this.vm = vm;
    this.editorVm = editorVm;
    this.editorView = editorView;
    this.navigationService = navigationService;
  }

  // ── FXML lifecycle ────────────────────────────────────────────────────────

  /**
   * Called by FXMLLoader after all {@code @FXML} fields are injected.
   *
   * <p>This is the single entry point for all bindings. Breaking it into private helper methods
   * keeps each concern isolated and easy to navigate during a code review. The order matters only
   * for {@code loadCollections()} — everything else can be in any order.
   */
  @SuppressWarnings("UnusedMethod") // called reflectively by FXMLLoader — not a dead method
  @FXML
  private void initialize() {
    bindPaneVisibility(); // 1. control which pane is shown based on state
    bindLabels(); // 2. status and error text labels
    bindCollectionCombos(); // 3. collection ComboBox in idle pane (shared list)
    bindButtons(); // 4. action handlers for all buttons
    embedEditor(); // 5. insert RecipeEditorView into editorContainer in the review pane
    bindEditorCallbacks(); // 6. wire REVIEW state → loadDraft, and save completion → acceptImport

    // Populate collection ComboBox as soon as the view is ready.
    // loadCollections() reads from the in-memory repository synchronously — safe on FX thread.
    vm.loadCollections();
  }

  // ── Private binding helpers ───────────────────────────────────────────────

  /**
   * Binds each pane's {@code visible} and {@code managed} properties to its corresponding workflow
   * state.
   *
   * <p><strong>Why bind both {@code visible} and {@code managed}?</strong> In JavaFX, setting
   * {@code visible=false} makes the node invisible but it still occupies layout space (the blank
   * area where the pane was remains). Setting {@code managed=false} removes the node from the
   * layout pass entirely, collapsing the space. Both must be bound to the same condition so the
   * pane is both hidden AND layout-removed when inactive.
   *
   * <p>{@link Bindings#equal} creates a {@link BooleanBinding} that is {@code true} whenever the
   * state property equals the given enum value, and updates automatically whenever the state
   * changes — no manual listener needed.
   */
  private void bindPaneVisibility() {
    BooleanBinding isIdle = Bindings.equal(vm.stateProperty(), ImportState.IDLE);
    BooleanBinding isProcessing = Bindings.equal(vm.stateProperty(), ImportState.PROCESSING);
    BooleanBinding isReview = Bindings.equal(vm.stateProperty(), ImportState.REVIEW);
    BooleanBinding isSuccess = Bindings.equal(vm.stateProperty(), ImportState.SUCCESS);
    BooleanBinding isError = Bindings.equal(vm.stateProperty(), ImportState.ERROR);

    // Each pane is visible AND managed only when in its corresponding state.
    idlePane.visibleProperty().bind(isIdle);
    idlePane.managedProperty().bind(isIdle);

    processingPane.visibleProperty().bind(isProcessing);
    processingPane.managedProperty().bind(isProcessing);

    reviewPane.visibleProperty().bind(isReview);
    reviewPane.managedProperty().bind(isReview);

    // SUCCESS pane: shown after the embedded editor saves the recipe.
    successPane.visibleProperty().bind(isSuccess);
    successPane.managedProperty().bind(isSuccess);

    errorPane.visibleProperty().bind(isError);
    errorPane.managedProperty().bind(isError);
  }

  /**
   * Binds the status label and error label to their ViewModel string properties.
   *
   * <p>These are one-way bindings (ViewModel → View) because the user never edits these labels
   * directly. Whenever the ViewModel updates the property, the Label's text changes automatically.
   */
  private void bindLabels() {
    statusLabel.textProperty().bind(vm.statusMessageProperty());
    errorLabel.textProperty().bind(vm.errorMessageProperty());
  }

  /**
   * Sets up the idle-pane collection ComboBox to populate from the ViewModel and notify it when the
   * user makes a selection.
   *
   * <p>The old design had two ComboBoxes (one in idle, one in review) that mirrored each other.
   * With the embedded Recipe Editor, the collection must be selected BEFORE starting import (in the
   * idle pane) because {@code loadDraft()} needs the collection ID at the moment OCR completes. The
   * review-pane ComboBox has been removed — collection selection is only in idle.
   *
   * <p>The {@link StringConverter} tells the ComboBox how to display a {@link CollectionEntry}:
   * show {@code entry.title()} and return {@code null} from {@code fromString} because the ComboBox
   * is not editable (user picks from the list only, no typing).
   */
  @SuppressWarnings("unchecked")
  private void bindCollectionCombos() {
    // Converter: how to display a CollectionEntry in the ComboBox dropdown and selected value.
    StringConverter<CollectionEntry> converter =
        new StringConverter<>() {
          @Override
          public String toString(CollectionEntry entry) {
            return entry == null ? "" : entry.title();
          }

          // fromString is only used when the ComboBox is editable (allows typing).
          // Our ComboBox is not editable, so this is never called — return null.
          @Override
          public @Nullable CollectionEntry fromString(String s) {
            return null;
          }
        };

    // The ObservableList from the ViewModel — populated by vm.loadCollections() called at the
    // end of initialize(). If the list is refreshed later, the ComboBox updates automatically.
    ObservableList<CollectionEntry> collections =
        (ObservableList<CollectionEntry>) vm.availableCollectionsProperty();

    collectionComboBox.setItems(collections);
    collectionComboBox.setConverter(converter);

    // When the user selects a collection, notify the ViewModel so the selection is stored.
    // The stored ID is then used in bindEditorCallbacks() when the REVIEW state is entered,
    // passing it to editorVm.loadDraft(recipe, collectionId).
    collectionComboBox
        .getSelectionModel()
        .selectedItemProperty()
        .addListener(
            (obs, oldVal, newVal) -> {
              if (newVal != null) {
                vm.selectTargetCollection(newVal.id());
              }
            });

    // Disable the file-chooser button and show the hint label until a collection is selected.
    // Both visible and managed are bound so the hint label does not take up space once hidden.
    BooleanBinding noCollectionSelected =
        collectionComboBox.getSelectionModel().selectedItemProperty().isNull();
    selectFileButton.disableProperty().bind(noCollectionSelected);
    collectionHintLabel.visibleProperty().bind(noCollectionSelected);
    collectionHintLabel.managedProperty().bind(noCollectionSelected);
  }

  /**
   * Attaches action handlers to all buttons. Each handler delegates to a ViewModel command — the
   * controller does NOT contain any business logic.
   *
   * <p>The "Choose Image File" button uses a {@link FileChooser} to get a file path from the OS
   * dialog, then passes it to the ViewModel. The FileChooser is created fresh each time the button
   * is clicked (not once at startup) to ensure it reflects the current window.
   *
   * <p>Note: The old review-pane buttons (acceptButton, addIngredientButton,
   * removeIngredientButton, rejectButton) are gone. The embedded Recipe Editor provides its own
   * Save and Back/Discard buttons.
   */
  private void bindButtons() {
    selectFileButton.setOnAction(
        e -> {
          FileChooser fc = new FileChooser();
          fc.setTitle("Select Recipe Image");
          // Only allow image file types that the OCR service can process.
          fc.getExtensionFilters()
              .add(new ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.webp"));
          // showOpenDialog blocks until the user picks a file or cancels.
          // Returns null if the user cancels — guard with null check before starting import.
          File file = fc.showOpenDialog(selectFileButton.getScene().getWindow());
          if (file != null) {
            vm.startImport(file.toPath());
          }
        });

    cancelButton.setOnAction(e -> vm.cancelImport());
    retryButton.setOnAction(e -> vm.resetToIdle());

    // "Return to Library": reset Import VM to IDLE then navigate to the Library view.
    // Navigation is handled here in the controller because it is a View concern — the ViewModel
    // (vm.returnToLibrary) only manages state; the controller calls navigationService.navigateTo.
    returnToLibraryButton.setOnAction(
        e -> {
          vm.returnToLibrary();
          navigationService.navigateTo(NavigationService.View.LIBRARY);
        });

    // "Import Another": reset to IDLE but stay on the Import view for the next image.
    importAnotherButton.setOnAction(e -> vm.returnToLibrary());
  }

  /**
   * Inserts the pre-loaded {@link #editorView} node into the {@link #editorContainer} VBox.
   *
   * <p>The editorContainer is an empty VBox declared in the FXML. Adding the editorView here at
   * runtime (rather than declaring it in FXML) is intentional: the Recipe Editor's FXML was loaded
   * separately in {@link app.cookyourbooks.gui.CookYourBooksGuiApp} with its own controller wired
   * to {@code editorVm}. We cannot declare a nested FXML node with a separate controller inline
   * without using {@code <fx:include>}, which would make wiring more complex and less flexible.
   *
   * <p>{@link Priority#ALWAYS} tells the VBox to give the editor all available vertical space,
   * matching how RecipeEditorView.fxml itself is laid out.
   */
  private void embedEditor() {
    editorContainer.getChildren().add(editorView);
    VBox.setVgrow(editorView, Priority.ALWAYS);
  }

  /**
   * Wires two cross-ViewModel callbacks that coordinate the Import VM and the embedded editor VM:
   *
   * <ol>
   *   <li><strong>REVIEW state → loadDraft</strong>: When the Import VM transitions to REVIEW (OCR
   *       succeeded), this listener builds a {@link Recipe} draft from the OCR data in the Import
   *       VM and calls {@link RecipeEditorViewModelImpl#loadDraft(Recipe, String)} to pre-populate
   *       the embedded editor. The collection ID was already set by the idle-pane ComboBox before
   *       import started.
   *   <li><strong>"Saved successfully." → acceptImport</strong>: When the embedded editor's status
   *       message becomes "Saved successfully." (set by {@code saveDraft()} after {@code
   *       librarianService.saveRecipe()} completes), this listener calls {@link
   *       ImportViewModelImpl#acceptImport()} to transition the Import VM back to IDLE and show the
   *       confirmation message.
   * </ol>
   */
  private void bindEditorCallbacks() {
    // Callback 1: REVIEW entered → pre-populate the embedded editor with the OCR draft.
    vm.stateProperty()
        .addListener(
            (obs, oldState, newState) -> {
              if (newState == ImportState.REVIEW) {
                String collId = vm.getSelectedCollectionId();
                if (collId != null) {
                  // Build a Recipe draft from the OCR data captured in the Import VM.
                  // importedIngredientsProperty() returns ObservableList<SimpleStringProperty>;
                  // each element's .get() value is the ingredient name string.
                  List<Ingredient> ingredientObjs =
                      vm.importedIngredientsProperty().stream()
                          .map(p -> (Ingredient) new VagueIngredient(p.get(), null, null, null))
                          .toList();
                  Recipe draft =
                      new Recipe(
                          null, // no ID yet — this recipe has not been saved
                          vm.importedTitleProperty().get(),
                          null,
                          ingredientObjs,
                          List.copyOf(
                              vm.importedInstructionsProperty()), // instructions extracted by
                          // Gemini OCR
                          List.of()); // conversion rules: none
                  // loadDraft() pre-populates the editor, sets editing=true, isDirty=true so
                  // the Save button is immediately enabled.
                  editorVm.loadDraft(draft, collId);
                }
              }
            });

    // Callback 2: embedded editor Discard/Back pressed on a draft → reject the import.
    // The editor's Back button calls discardChanges(), which on a draft invokes this callback
    // instead of navigating to the Library, so the Import view returns to IDLE.
    editorVm.setOnDraftDiscarded(vm::rejectImport);

    // Callback 3: embedded editor saved → transition Import VM to SUCCESS.
    // "Saved successfully." is set by RecipeEditorViewModelImpl.saveDraft() on success.
    // "Saved successfully." is the exact string set by RecipeEditorViewModelImpl.saveDraft().
    // Using a string sentinel is simple but fragile — if the message ever changes, this breaks.
    // A cleaner design would expose a BooleanProperty "lastSaveSucceeded" on the editor VM,
    // but the string approach avoids adding new API surface to RecipeEditorViewModelImpl.
    editorVm
        .statusMessageProperty()
        .addListener(
            (obs, oldMsg, newMsg) -> {
              if ("Saved successfully.".equals(newMsg)) {
                vm.acceptImport();
              }
            });
  }
}
