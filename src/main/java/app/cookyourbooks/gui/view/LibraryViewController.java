package app.cookyourbooks.gui.view;

import javafx.beans.property.BooleanProperty;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.HBox;

import app.cookyourbooks.gui.viewmodel.LibraryViewModel;
import app.cookyourbooks.gui.viewmodel.RecipeCollectionSummary;
import app.cookyourbooks.gui.viewmodel.RecipeSummary;
import app.cookyourbooks.gui.viewmodel.ShoppingListViewModel;

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
  @FXML private Button confirmShoppingListButton;
  @FXML private Button cancelShoppingListButton;
  @FXML private HBox normalRecipeButtons;
  @FXML private HBox undoBar;
  @FXML private HBox shoppingListConfirmBar;
  @FXML private Label undoLabel;
  @FXML private Label shoppingListHintLabel;

  // ── ViewModel ──

  private final LibraryViewModel vm;
  private final ShoppingListViewModel shoppingListVm;

  /**
   * Constructs the controller with its ViewModel.
   *
   * @param vm the Library ViewModel
   */
  public LibraryViewController(LibraryViewModel vm, ShoppingListViewModel shoppingListVm) {
    this.vm = vm;
    this.shoppingListVm = shoppingListVm;
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
    setupShoppingListMode();

    vm.refresh();

    // Command+S opens shopping list selection mode
    filterField.getScene(); // scene not available yet in initialize
    // Use a scene listener instead
    filterField
        .sceneProperty()
        .addListener(
            (obs, oldScene, newScene) -> {
              if (newScene != null) {
                newScene.setOnKeyPressed(
                    event -> {
                      if (event.getCode() == javafx.scene.input.KeyCode.S
                          && event.isShortcutDown()) {
                        if (!shoppingListVm.activeProperty().get()) {
                          shoppingListVm.enter();
                        }
                        event.consume();
                      }
                    });
              }
            });
  }

  // ── Private setup helpers ──

  private void setupShoppingListMode() {
    BooleanProperty active = shoppingListVm.activeProperty();

    // Show/hide the hint label and confirm bar
    shoppingListHintLabel.visibleProperty().bind(active);
    shoppingListHintLabel.managedProperty().bind(active);
    shoppingListConfirmBar.visibleProperty().bind(active);
    shoppingListConfirmBar.managedProperty().bind(active);

    // Swap the recipe list cell factory when mode changes
    normalRecipeButtons.visibleProperty().bind(active.not());
    normalRecipeButtons.managedProperty().bind(active.not());
    active.addListener((obs, wasActive, isActive) -> refreshRecipeCellFactory(isActive));

    recipeListView.setOnKeyPressed(
        event -> {
          if (event.getCode() == javafx.scene.input.KeyCode.ENTER) {
            RecipeSummary selected = recipeListView.getSelectionModel().getSelectedItem();
            if (selected != null && shoppingListVm.activeProperty().get()) {
              if (shoppingListVm.selectedRecipeIds().contains(selected.id())) {
                shoppingListVm.selectedRecipeIds().remove(selected.id());
              } else {
                shoppingListVm.selectedRecipeIds().add(selected.id());
              }
              recipeListView.refresh();
            }
            event.consume();
          } else if (event.getCode() == javafx.scene.input.KeyCode.DELETE
              || event.getCode() == javafx.scene.input.KeyCode.BACK_SPACE) {
            RecipeSummary selected = recipeListView.getSelectionModel().getSelectedItem();
            if (selected != null && shoppingListVm.activeProperty().get()) {
              shoppingListVm.selectedRecipeIds().remove(selected.id());
              recipeListView.refresh();
            }
            event.consume();
          }
        });
    recipeListView.setOnKeyPressed(
        event -> {
          if (event.getCode() == javafx.scene.input.KeyCode.ENTER && event.isShortcutDown()) {
            // Command+Enter — confirm shopping list
            if (shoppingListVm.activeProperty().get()) {
              onConfirmShoppingList();
            }
            event.consume();
          } else if ((event.getCode() == javafx.scene.input.KeyCode.DELETE
                  || event.getCode() == javafx.scene.input.KeyCode.BACK_SPACE)
              && event.isShortcutDown()) {
            // Command+Delete — cancel shopping list
            if (shoppingListVm.activeProperty().get()) {
              shoppingListVm.discard();
            }
            event.consume();
          } else if (event.getCode() == javafx.scene.input.KeyCode.ENTER) {
            RecipeSummary selected = recipeListView.getSelectionModel().getSelectedItem();
            if (selected != null && shoppingListVm.activeProperty().get()) {
              if (shoppingListVm.selectedRecipeIds().contains(selected.id())) {
                shoppingListVm.selectedRecipeIds().remove(selected.id());
              } else {
                shoppingListVm.selectedRecipeIds().add(selected.id());
              }
              recipeListView.refresh();
            }
            event.consume();
          } else if (event.getCode() == javafx.scene.input.KeyCode.DELETE
              || event.getCode() == javafx.scene.input.KeyCode.BACK_SPACE) {
            RecipeSummary selected = recipeListView.getSelectionModel().getSelectedItem();
            if (selected != null && shoppingListVm.activeProperty().get()) {
              shoppingListVm.selectedRecipeIds().remove(selected.id());
              recipeListView.refresh();
            }
            event.consume();
          }
        });
  }

  @SuppressWarnings("UnusedMethod")
  @FXML
  private void onCancelShoppingList() {
    shoppingListVm.discard();
  }

  @SuppressWarnings("unchecked")
  private void refreshRecipeCellFactory(boolean selectionMode) {
    if (!selectionMode) {
      // Normal mode: plain title cell (same as before)
      recipeListView.setCellFactory(
          lv ->
              new ListCell<>() {
                @Override
                protected void updateItem(RecipeSummary item, boolean empty) {
                  super.updateItem(item, empty);
                  setText(empty || item == null ? null : item.title());
                  setGraphic(null);
                }
              });
    } else {
      // Selection mode: checkbox cell
      recipeListView.setCellFactory(
          lv ->
              new ListCell<>() {
                private final javafx.scene.control.CheckBox checkBox =
                    new javafx.scene.control.CheckBox();

                @Override
                protected void updateItem(RecipeSummary item, boolean empty) {
                  super.updateItem(item, empty);
                  if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                  }
                  checkBox.setText(item.title());
                  // Sync checkbox state with the VM's selected set
                  checkBox.setSelected(shoppingListVm.selectedRecipeIds().contains(item.id()));
                  checkBox.setOnAction(
                      e -> {
                        if (checkBox.isSelected()) {
                          shoppingListVm.selectedRecipeIds().add(item.id());
                        } else {
                          shoppingListVm.selectedRecipeIds().remove(item.id());
                        }
                      });
                  setGraphic(checkBox);
                  setText(null);
                }
              });
    }
  }

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
    // TODO: wire export logic
  }

  @FXML
  private void onConfirmShoppingList() {
    // Confirmation dialog with Discard option
    Alert alert =
        new Alert(
            Alert.AlertType.CONFIRMATION,
            "Add "
                + shoppingListVm.selectedRecipeIds().size()
                + " recipe(s) to your shopping list?",
            ButtonType.OK,
            ButtonType.CANCEL);
    alert.setTitle("Confirm Shopping List");
    alert.setHeaderText(null);

    // Rename the Cancel button to "Discard" per your requirement
    alert.getButtonTypes().stream()
        .filter(bt -> bt == ButtonType.CANCEL)
        .findFirst()
        .ifPresent(
            bt -> {
              ((javafx.scene.control.Button) alert.getDialogPane().lookupButton(bt))
                  .setText("Discard");
            });

    alert
        .showAndWait()
        .ifPresent(
            result -> {
              if (result == ButtonType.OK) {
                shoppingListVm.confirm(); // hands IDs to partner's screen
              } else {
                shoppingListVm.discard(); // clears selection, exits mode
              }
            });
  }
}
