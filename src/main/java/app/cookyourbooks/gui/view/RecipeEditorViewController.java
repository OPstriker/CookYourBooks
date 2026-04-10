package app.cookyourbooks.gui.view;

import javafx.beans.binding.Bindings;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

import app.cookyourbooks.gui.NavigationService;
import app.cookyourbooks.gui.NavigationService.View;
import app.cookyourbooks.gui.viewmodel.RecipeEditorViewModel;
import app.cookyourbooks.gui.viewmodel.RecipeEditorViewModelImpl;

/**
 * FXML controller for {@code RecipeEditorView.fxml}.
 *
 * <p>Binds the View's UI controls to the {@link RecipeEditorViewModel} and handles user
 * interactions such as toggling edit mode, saving, discarding changes, adding/removing ingredients,
 * and navigating back to the Library. Follows the MVVM pattern: all state lives in the ViewModel;
 * the controller only wires bindings and dispatches commands.
 */
@SuppressWarnings("NullAway.Init")
public class RecipeEditorViewController {

  @FXML private TextField titleField;
  @FXML private Label statusLabel;
  @FXML private ListView<String> ingredientsList;
  @FXML private ListView<String> instructionsList;
  @FXML private Button editButton;
  @FXML private Button addIngredientButton;
  @FXML private Button backButton;

  private final RecipeEditorViewModel viewModel;
  private final NavigationService navigationService;

  /**
   * Constructs the controller with its required dependencies. Both are injected via {@code
   * setControllerFactory()} in the application wiring, following the dependency injection pattern
   * used throughout the app.
   *
   * @param viewModel the ViewModel that manages all editor state and commands
   * @param navigationService the shared navigation service used to navigate back to the Library
   */
  public RecipeEditorViewController(
      RecipeEditorViewModel viewModel, NavigationService navigationService) {
    this.viewModel = viewModel;
    this.navigationService = navigationService;
  }

  /**
   * Called automatically by {@link javafx.fxml.FXMLLoader} after all {@code @FXML} fields have been
   * injected. Sets up all property bindings between the View controls and the ViewModel, configures
   * the ingredient list cell factory, and wires button action handlers.
   */
  @SuppressWarnings({"unchecked", "UnusedMethod"})
  @FXML
  private void initialize() {
    // Bidirectional binding: edits in the TextField update the ViewModel title,
    // and ViewModel title changes update the TextField.
    titleField.textProperty().bindBidirectional(viewModel.titleProperty());
    titleField.editableProperty().bind(viewModel.editingProperty());

    // Bind the status label to the ViewModel's status message.
    // Color the text red for error messages, green for success messages.
    statusLabel.textProperty().bind(viewModel.statusMessageProperty());
    viewModel
        .statusMessageProperty()
        .addListener(
            (obs, oldVal, newVal) -> {
              if ((newVal != null && newVal.contains("failed"))
                  || (newVal != null && newVal.contains("blank"))) {
                statusLabel.setStyle("-fx-text-fill: red; -fx-font-size: 12px;");
              } else {
                statusLabel.setStyle("-fx-text-fill: green; -fx-font-size: 12px;");
              }
            });

    // Instructions list is read-only — no editing controls needed.
    instructionsList.setItems(((RecipeEditorViewModelImpl) viewModel).instructionsProperty());

    // Bind the ingredients ListView to the ViewModel's observable list.
    ingredientsList.setItems((ObservableList<String>) viewModel.ingredientsProperty());

    // Custom cell factory: each ingredient row shows the name plus ✕ / ↑ / ↓ buttons.
    // Buttons are only visible in edit mode (bound to editingProperty).
    ingredientsList.setCellFactory(
        lv ->
            new ListCell<>() {
              private final Button xBtn = new Button("✕");
              private final Button upBtn = new Button("↑");
              private final Button downBtn = new Button("↓");

              // Apply CSS style classes once at cell construction time (cells are recycled).
              {
                xBtn.getStyleClass().add("editor-btn-danger-sm");
                upBtn.getStyleClass().add("editor-btn-icon-sm");
                downBtn.getStyleClass().add("editor-btn-icon-sm");
              }

              private final Label text = new Label();
              private final HBox box = new HBox(8, text, upBtn, downBtn, xBtn);

              {
                HBox.setHgrow(text, Priority.ALWAYS);

                // Show action buttons only when in edit mode.
                xBtn.visibleProperty().bind(viewModel.editingProperty());
                xBtn.managedProperty().bind(viewModel.editingProperty());
                upBtn.visibleProperty().bind(viewModel.editingProperty());
                upBtn.managedProperty().bind(viewModel.editingProperty());
                downBtn.visibleProperty().bind(viewModel.editingProperty());
                downBtn.managedProperty().bind(viewModel.editingProperty());

                // Wire remove and reorder actions to ViewModel commands.
                xBtn.setOnAction(
                    e -> {
                      if (!isEmpty()) viewModel.removeIngredient(getIndex());
                    });
                upBtn.setOnAction(
                    e -> {
                      if (!isEmpty()) viewModel.moveIngredientUp(getIndex());
                    });
                downBtn.setOnAction(
                    e -> {
                      if (!isEmpty()) viewModel.moveIngredientDown(getIndex());
                    });
              }

              /**
               * Updates the cell's display when JavaFX recycles it for a new list item. Clears the
               * graphic for empty cells; sets the ingredient name label otherwise.
               *
               * @param item the ingredient name string, or {@code null} for empty cells
               * @param empty {@code true} if this cell does not correspond to a list item
               */
              @Override
              protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                  setGraphic(null);
                } else {
                  text.setText(item);
                  setGraphic(box);
                }
              }
            });

    // Edit/Done button: toggles edit mode. When in edit mode and dirty, prompts to save.
    // Button text cycles: "Edit" → "Done" → "Edit". Shows "Saving..." while a save is running.
    editButton.setOnAction(
        e -> {
          if (viewModel.isEditing()) {
            if (!viewModel.isDirty()) {
              // No changes — silently exit edit mode.
              viewModel.toggleEditMode();
              return;
            }
            // In edit mode with unsaved changes — prompt the user to save.
            Alert alert =
                new Alert(
                    Alert.AlertType.CONFIRMATION,
                    "Save changes?",
                    ButtonType.OK,
                    ButtonType.CANCEL);
            alert
                .showAndWait()
                .filter(r -> r == ButtonType.OK)
                .ifPresent(
                    r -> {
                      if (viewModel.isValid()) {
                        viewModel.save();
                      } else {
                        viewModel.statusMessageProperty().set("Title cannot be blank.");
                      }
                    });
          } else {
            // In view mode — enter edit mode.
            viewModel.toggleEditMode();
          }
        });

    // Bind button text and disabled state to ViewModel properties.
    editButton
        .textProperty()
        .bind(
            Bindings.when(viewModel.isSavingProperty())
                .then("Saving...")
                .otherwise(
                    Bindings.when(viewModel.editingProperty()).then("Done").otherwise("Edit")));
    editButton.disableProperty().bind(viewModel.isSavingProperty());

    // Disable all editing controls while a background save is in progress.
    addIngredientButton.disableProperty().bind(viewModel.isSavingProperty());
    titleField.disableProperty().bind(viewModel.isSavingProperty());
    ingredientsList.disableProperty().bind(viewModel.isSavingProperty());
    backButton.disableProperty().bind(viewModel.isSavingProperty());

    // "Add Ingredient" button is only visible in edit mode.
    addIngredientButton.visibleProperty().bind(viewModel.editingProperty());
    addIngredientButton.managedProperty().bind(viewModel.editingProperty());

    // Show a text input dialog to collect the new ingredient name.
    addIngredientButton.setOnAction(
        e -> {
          javafx.scene.control.TextInputDialog dialog = new javafx.scene.control.TextInputDialog();
          dialog.setTitle("Add Ingredient");
          dialog.setHeaderText(null);
          dialog.setContentText("Ingredient name:");
          dialog.showAndWait().ifPresent(name -> viewModel.addIngredient(name));
        });

    bindBackButton();
  }

  /**
   * Wires the Back/Discard button's action handler and text binding.
   *
   * <p>In edit mode: prompts the user to confirm discarding unsaved changes before calling {@link
   * RecipeEditorViewModel#discardChanges()}. If not dirty, discards silently. In view mode: clears
   * the status message and navigates back to the Library view.
   *
   * <p>Button text switches between "Discard" (edit mode) and "Back" (view mode), bound to {@link
   * RecipeEditorViewModel#editingProperty()}.
   */
  private void bindBackButton() {
    backButton.setOnAction(
        e -> {
          if (viewModel.isEditing()) {
            if (viewModel.isDirty()) {
              // Unsaved changes — ask the user before discarding.
              Alert alert =
                  new Alert(
                      Alert.AlertType.CONFIRMATION,
                      "Discard unsaved changes?",
                      ButtonType.OK,
                      ButtonType.CANCEL);
              alert
                  .showAndWait()
                  .filter(r -> r == ButtonType.OK)
                  .ifPresent(r -> viewModel.discardChanges());
            } else {
              // No unsaved changes — exit edit mode silently.
              viewModel.discardChanges();
            }
          } else {
            // In view mode — navigate back to the Library.
            viewModel.statusMessageProperty().set("");
            navigationService.navigateTo(View.LIBRARY);
          }
        });

    backButton
        .textProperty()
        .bind(Bindings.when(viewModel.editingProperty()).then("Discard").otherwise("Back"));
  }
}
