package app.cookyourbooks.gui.view;

import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextInputDialog;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;

import app.cookyourbooks.gui.viewmodel.LibraryViewModel;
import app.cookyourbooks.gui.viewmodel.RecipeCollectionSummary;
import app.cookyourbooks.gui.viewmodel.RecipeSummary;

/**
 * Controller for the Library View feature ({@code LibraryView.fxml}).
 *
 * <p>Binds the FXML controls to the {@link LibraryViewModel} observable properties and forwards
 * user actions (create, delete, undo, filter, select) to the ViewModel.
 */
@SuppressWarnings("NullAway.Init") // FXML fields are injected by FXMLLoader, not the constructor
public class LibraryViewController {

  // ── FXML-injected controls ──

  @FXML private javafx.scene.control.TextField filterField;
  @FXML private ProgressIndicator loadingIndicator;
  @FXML private ListView<RecipeCollectionSummary> collectionListView;
  @FXML private ListView<RecipeSummary> recipeListView;
  @FXML private Button deleteButton;
  @FXML private Button openRecipeButton;
  @FXML private Button exportButton;
  @FXML private Button deleteRecipeButton;
  @FXML private HBox undoBar;
  @FXML private Label undoLabel;

  // ── ViewModel ──

  private final LibraryViewModel vm;

  /**
   * Constructs the controller with its ViewModel.
   *
   * @param vm the Library ViewModel
   */
  public LibraryViewController(LibraryViewModel vm) {
    this.vm = vm;
  }

  // ── Initialization ──

  /** Called by FXMLLoader after all @FXML fields are injected. Sets up all bindings. */
  @SuppressWarnings("UnusedMethod") // called reflectively by FXMLLoader
  @FXML
  private void initialize() {
    setupFilterBinding();
    setupCollectionList();
    setupRecipeList();
    setupLoadingIndicator();
    setupUndoBar();
    setupExportShortcut();

    vm.refresh();
  }

  // ── Private setup helpers ──

  private void setupFilterBinding() {
    // Bidirectional: typing in the field updates vm.filterTextProperty(), and vice versa
    filterField.textProperty().bindBidirectional(vm.filterTextProperty());
  }

  @SuppressWarnings("unchecked")
  private void setupCollectionList() {
    // The interface returns ObservableList<?> for flexibility; we know the impl uses
    // RecipeCollectionSummary.
    collectionListView.setItems((ObservableList<RecipeCollectionSummary>) vm.collectionsProperty());

    // Custom cell: show title + recipe count
    collectionListView.setCellFactory(
        lv ->
            new ListCell<>() {
              @Override
              protected void updateItem(RecipeCollectionSummary item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                  setText(null);
                } else {
                  setText(item.title() + "  ·  " + item.recipeCount() + " recipes");
                }
              }
            });

    // Forward selection to ViewModel
    collectionListView
        .getSelectionModel()
        .selectedItemProperty()
        .addListener(
            (obs, oldVal, newVal) -> {
              if (newVal != null) {
                vm.selectCollection(newVal.id());
              }
            });

    // Delete button is only enabled when a collection is selected
    deleteButton
        .disableProperty()
        .bind(collectionListView.getSelectionModel().selectedItemProperty().isNull());
  }

  @SuppressWarnings("unchecked")
  private void setupRecipeList() {
    // Same rationale: interface uses ObservableList<?>, impl uses RecipeSummary.
    recipeListView.setItems((ObservableList<RecipeSummary>) vm.recipesProperty());

    recipeListView.setCellFactory(
        lv ->
            new ListCell<>() {
              @Override
              protected void updateItem(RecipeSummary item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.title());
              }
            });

    // Enable the Open Recipe, Export, and Delete Recipe buttons only when a recipe is selected
    openRecipeButton
        .disableProperty()
        .bind(recipeListView.getSelectionModel().selectedItemProperty().isNull());
    exportButton
        .disableProperty()
        .bind(recipeListView.getSelectionModel().selectedItemProperty().isNull());
    deleteRecipeButton
        .disableProperty()
        .bind(recipeListView.getSelectionModel().selectedItemProperty().isNull());
  }

  private void setupExportShortcut() {
    // Register Ctrl+Shift+E as a keyboard accelerator for the Export PDF button.
    // The scene is not yet available at initialize() time, so we listen for it to be set.
    exportButton
        .sceneProperty()
        .addListener(
            (obs, oldScene, newScene) -> {
              if (newScene != null) {
                newScene
                    .getAccelerators()
                    .put(
                        new KeyCodeCombination(
                            KeyCode.E, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN),
                        () -> {
                          if (!exportButton.isDisabled()) {
                            exportButton.fire();
                          }
                        });
              }
            });
  }

  private void setupLoadingIndicator() {
    loadingIndicator.visibleProperty().bind(vm.loadingProperty());
    loadingIndicator.managedProperty().bind(vm.loadingProperty());
  }

  private void setupUndoBar() {
    // managed=false when invisible so it doesn't take up space in the layout
    undoBar.visibleProperty().bind(vm.undoAvailableProperty());
    undoBar.managedProperty().bind(vm.undoAvailableProperty());
    undoLabel.textProperty().bind(vm.undoMessageProperty());
  }

  // ── FXML event handlers ──

  @SuppressWarnings("UnusedMethod") // called reflectively by FXMLLoader
  @FXML
  private void onCreateCollection() {
    var dialog = new TextInputDialog();
    dialog.setTitle("New Collection");
    dialog.setHeaderText(null);
    dialog.setContentText("Collection name:");
    dialog.showAndWait().ifPresent(vm::createCollection);
  }

  @SuppressWarnings("UnusedMethod") // called reflectively by FXMLLoader
  @FXML
  private void onDeleteCollection() {
    var selected = collectionListView.getSelectionModel().getSelectedItem();
    if (selected != null) {
      vm.deleteCollection(selected.id());
    }
  }

  @SuppressWarnings("UnusedMethod") // called reflectively by FXMLLoader
  @FXML
  private void onUndo() {
    vm.undoDelete();
  }

  @SuppressWarnings("UnusedMethod") // called reflectively by FXMLLoader
  @FXML
  private void onOpenRecipe() {
    var selected = recipeListView.getSelectionModel().getSelectedItem();
    if (selected != null) {
      vm.selectRecipe(selected.id());
    }
  }

  @SuppressWarnings("UnusedMethod") // called reflectively by FXMLLoader
  @FXML
  private void onDeleteRecipe() {
    var selected = recipeListView.getSelectionModel().getSelectedItem();
    if (selected != null) {
      vm.deleteRecipe(selected.id());
    }
  }

  @SuppressWarnings("UnusedMethod") // called reflectively by FXMLLoader
  @FXML
  private void onExportRecipe() {
    RecipeSummary selected = recipeListView.getSelectionModel().getSelectedItem();
    if (selected == null) {
      return; // button should already be disabled, but guard defensively
    }
<<<<<<< merge-v3

    // FileChooser must run on the JavaFX Application Thread — onExportRecipe is always
    // called from a button click, so we are already on the right thread here.
    FileChooser fileChooser = new FileChooser();
    fileChooser.setTitle("Export Recipe as PDF");
    fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
    fileChooser.setInitialFileName(selected.title() + ".pdf");

    // showSaveDialog returns null if the user cancels
    java.io.File file = fileChooser.showSaveDialog(exportButton.getScene().getWindow());
    if (file == null) {
      return; // user cancelled — nothing to do
    }

    vm.exportRecipe(selected.id(), file.toPath());

    // Confirm to the user that the export was started (the actual write is async)
    Alert alert = new Alert(Alert.AlertType.INFORMATION);
    alert.setTitle("Export PDF");
    alert.setHeaderText(null);
    alert.setContentText("\"" + selected.title() + "\" is being exported to:\n" + file.getPath());
    alert.showAndWait();
  }
=======
>>>>>>> main

    // FileChooser must run on the JavaFX Application Thread — onExportRecipe is always
    // called from a button click, so we are already on the right thread here.
    FileChooser fileChooser = new FileChooser();
    fileChooser.setTitle("Export Recipe as PDF");
    fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
    fileChooser.setInitialFileName(selected.title() + ".pdf");

    // showSaveDialog returns null if the user cancels
    java.io.File file = fileChooser.showSaveDialog(exportButton.getScene().getWindow());
    if (file == null) {
      return; // user cancelled — nothing to do
    }

    vm.exportRecipe(selected.id(), file.toPath());

    // Confirm to the user that the export was started (the actual write is async)
    Alert alert = new Alert(Alert.AlertType.INFORMATION);
    alert.setTitle("Export PDF");
    alert.setHeaderText(null);
    alert.setContentText("\"" + selected.title() + "\" is being exported to:\n" + file.getPath());
    alert.showAndWait();
  }
}
