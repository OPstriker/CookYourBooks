package app.cookyourbooks.gui.view;

import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.VBox;

import app.cookyourbooks.gui.viewmodel.IngredientItem;
import app.cookyourbooks.gui.viewmodel.RecipeSection;
import app.cookyourbooks.gui.viewmodel.ShoppingListResultViewModel;

/**
 * FXML controller for {@code ShoppingListResultView.fxml}.
 *
 * <p>Binds the View to the {@link ShoppingListResultViewModel}: wires the loading spinner, the Back
 * button, and listens to the sections list so it can rebuild the ingredient display whenever {@link
 * ShoppingListResultViewModel#load(java.util.Set)} completes.
 *
 * <h2>Dynamic content</h2>
 *
 * <p>Because the number of recipe sections is not known at compile time, the section VBoxes and
 * ingredient CheckBoxes are created programmatically inside {@link #rebuildSections()} rather than
 * statically in FXML. This mirrors the pattern used in {@code SearchViewController} for its filter
 * chips.
 *
 * <h2>Strikethrough</h2>
 *
 * <p>JavaFX {@code CheckBox} does not automatically apply strikethrough when selected. A listener
 * on each {@link IngredientItem#checkedProperty()} applies an inline style that grays out and
 * strikes through the text when the item is checked.
 *
 * <h2>Section dismiss</h2>
 *
 * <p>Clicking the bold recipe header toggles {@link RecipeSection#dismissedProperty()}. A listener
 * on that property applies a gray strikethrough to the header, the collection subtitle, and every
 * {@link CheckBox} in the section at once.
 */
@SuppressWarnings("NullAway.Init") // FXML fields are injected by FXMLLoader, not the constructor
public class ShoppingListResultViewController {

  @FXML private ProgressIndicator loadingSpinner;
  @FXML private VBox sectionsContainer;
  @FXML private Button backButton;

  private final ShoppingListResultViewModel vm;

  /**
   * Constructs the controller with its ViewModel. Supplied via {@code loader.setController()} in
   * {@code CookYourBooksGuiApp}.
   *
   * @param vm the Shopping List result ViewModel
   */
  public ShoppingListResultViewController(ShoppingListResultViewModel vm) {
    this.vm = vm;
  }

  /**
   * Called by FXMLLoader after all {@code @FXML} fields are injected. Sets up property bindings,
   * the Back button handler, and the listener that rebuilds sections when the VM's list changes.
   */
  @SuppressWarnings("UnusedMethod") // Called reflectively by FXMLLoader
  @FXML
  private void initialize() {
    loadingSpinner.visibleProperty().bind(vm.loadingProperty());
    loadingSpinner.managedProperty().bind(vm.loadingProperty());

    backButton.setOnAction(e -> vm.navigateBack());

    // Rebuild the ingredient display whenever load() finishes populating sections.
    vm.sectionsProperty()
        .addListener((ListChangeListener<RecipeSection>) change -> rebuildSections());
  }

  /**
   * Clears and rebuilds the {@code sectionsContainer} VBox from the current sections in the VM.
   *
   * <p>For each {@link RecipeSection}:
   *
   * <ul>
   *   <li>A bold, clickable {@link Label} is added as the recipe header. Clicking it toggles
   *       {@link RecipeSection#dismissedProperty()}.
   *   <li>A subtitle {@link Label} shows "From: [collectionName]" in a muted style.
   *   <li>A bordered inner {@link VBox} holds one {@link CheckBox} per {@link IngredientItem}.
   *   <li>Each checkbox is bidirectionally bound to {@link IngredientItem#checkedProperty()}.
   *   <li>A listener on {@code checkedProperty()} applies a gray strikethrough style when checked.
   *   <li>A listener on {@link RecipeSection#dismissedProperty()} grays out and strikes through
   *       the header, subtitle, and every checkbox when the section is dismissed.
   * </ul>
   *
   * <p>This method runs on the FX Application Thread (it is triggered by an ObservableList
   * listener), so creating JavaFX nodes here is safe.
   */
  private void rebuildSections() {
    sectionsContainer.getChildren().clear();

    for (RecipeSection section : vm.sectionsProperty()) {
      // Bold, hand-cursor header — clicking toggles the dismissed state for the whole section.
      Label header = new Label(section.getRecipeName());
      header.setStyle(
          "-fx-font-weight: bold; -fx-font-size: 14px; -fx-cursor: hand;");
      header.setOnMouseClicked(
          e -> section.dismissedProperty().set(!section.dismissedProperty().get()));

      // Subtitle showing which collection this recipe belongs to.
      Label subtitle = new Label("From: " + section.getCollectionName());
      subtitle.setStyle("-fx-font-size: 11px; -fx-text-fill: #888888;");

      VBox ingredientBox = new VBox(2);
      ingredientBox.setStyle("-fx-border-color: #cccccc; -fx-border-radius: 4; -fx-padding: 8;");

      for (IngredientItem item : section.getIngredients()) {
        CheckBox cb = new CheckBox(item.getDisplayText());
        cb.selectedProperty().bindBidirectional(item.checkedProperty());
        item.checkedProperty()
            .addListener(
                (obs, wasChecked, isNow) ->
                    cb.setStyle(isNow ? "-fx-text-fill: #999999; -fx-strikethrough: true;" : ""));
        ingredientBox.getChildren().add(cb);
      }

      // When the section is dismissed, gray out the header, subtitle, and all checkboxes at once.
      section
          .dismissedProperty()
          .addListener(
              (obs, was, isNow) -> {
                header.setStyle(
                    isNow
                        ? "-fx-font-weight: bold; -fx-font-size: 14px; -fx-cursor: hand;"
                            + " -fx-text-fill: #999999; -fx-strikethrough: true;"
                        : "-fx-font-weight: bold; -fx-font-size: 14px; -fx-cursor: hand;");
                subtitle.setStyle(
                    isNow
                        ? "-fx-font-size: 11px; -fx-text-fill: #cccccc;"
                        : "-fx-font-size: 11px; -fx-text-fill: #888888;");
                for (var node : ingredientBox.getChildren()) {
                  if (node instanceof CheckBox cb) {
                    cb.setDisable(isNow);
                    cb.setStyle(
                        isNow ? "-fx-text-fill: #999999; -fx-strikethrough: true;" : "");
                  }
                }
              });

      sectionsContainer.getChildren().add(new VBox(6, header, subtitle, ingredientBox));
    }
  }
}
