package app.cookyourbooks.gui.view;

import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

import app.cookyourbooks.gui.viewmodel.SearchViewModelImpl;
import app.cookyourbooks.gui.viewmodel.SearchViewModelImpl.RecipeEntry;

/**
 * FXML controller for {@code SearchView.fxml}.
 *
 * <p>Binds the View to the {@link SearchViewModelImpl}: wires observable property bindings,
 * ingredient-filter chip rendering, keyboard navigation, and the add-filter action.
 *
 * <h2>Keyboard navigation</h2>
 *
 * <p>While the search field has focus:
 *
 * <ul>
 *   <li>{@code ↓} / {@code ↑} — move the selection in the results list
 *   <li>{@code Enter} — navigate to the selected recipe in the editor
 * </ul>
 */
@SuppressWarnings("NullAway.Init") // FXML fields are injected by FXMLLoader, not the constructor
public class SearchViewController {

  @FXML private TextField searchField;
  @FXML private ProgressIndicator searchSpinner;
  @FXML private TextField ingredientInput;
  @FXML private Button addFilterButton;
  @FXML private Button clearAllButton;
  @FXML private FlowPane filterChipsPane;
  @FXML private Label statusLabel;
  @FXML private ListView<RecipeEntry> resultsList;

  private final SearchViewModelImpl viewModel;

  /**
   * Constructs the controller with its ViewModel. Called by the controller factory in {@code
   * CookYourBooksGuiApp}.
   *
   * @param viewModel the Search &amp; Filter ViewModel
   */
  public SearchViewController(SearchViewModelImpl viewModel) {
    this.viewModel = viewModel;
  }

  /**
   * Called by FXMLLoader after all {@code @FXML} fields are injected. Sets up all property bindings
   * and event handlers.
   */
  @SuppressWarnings("UnusedMethod") // Called reflectively by FXMLLoader
  @FXML
  private void initialize() {
    // ── Results list ──────────────────────────────────────────────────────────
    resultsList.setItems(viewModel.resultsProperty());
    resultsList.setCellFactory(lv -> new RecipeEntryCell());

    // Single click on a result → open it in the Recipe Editor.
    // The ListView's selection model tracks which item was clicked; we read it directly
    // and call navigateToRecipe() rather than going through the keyboard-nav selectedIndex.
    resultsList.setOnMouseClicked(
        event -> {
          RecipeEntry selected = resultsList.getSelectionModel().getSelectedItem();
          if (selected != null) {
            viewModel.navigateToRecipe(selected.id());
          }
        });

    // ── Status label & spinner ────────────────────────────────────────────────
    statusLabel.textProperty().bind(viewModel.statusMessageProperty());
    searchSpinner.visibleProperty().bind(viewModel.searchingProperty());
    searchSpinner.managedProperty().bind(viewModel.searchingProperty());

    // ── Search field → ViewModel (drives debounce timer) ─────────────────────
    searchField.textProperty().addListener((obs, oldVal, newVal) -> viewModel.setQuery(newVal));

    // ── Ingredient filter chips ───────────────────────────────────────────────
    viewModel
        .ingredientFiltersProperty()
        .addListener((ListChangeListener<String>) change -> rebuildFilterChips());

    // ── Initial load: show all recipes as soon as the view opens ─────────────
    viewModel.clearFilters();

    // ── Clear-all button ─────────────────────────────────────────────────────
    clearAllButton.setOnAction(e -> viewModel.clearFilters());

    // ── Add-filter button ─────────────────────────────────────────────────────
    addFilterButton.setOnAction(
        e -> {
          String text = ingredientInput.getText().trim();
          if (!text.isBlank()) {
            viewModel.addIngredientFilter(text);
            ingredientInput.clear();
          }
        });

    // Allow pressing Enter in the ingredient input to add the filter.
    ingredientInput.setOnAction(
        e -> {
          String text = ingredientInput.getText().trim();
          if (!text.isBlank()) {
            viewModel.addIngredientFilter(text);
            ingredientInput.clear();
          }
        });

    // ── Keyboard navigation while search field has focus ──────────────────────
    searchField.setOnKeyPressed(
        event -> {
          switch (event.getCode()) {
            case DOWN -> {
              viewModel.selectNextResult();
              syncListSelection();
              event.consume();
            }
            case UP -> {
              viewModel.selectPreviousResult();
              syncListSelection();
              event.consume();
            }
            case ENTER -> {
              viewModel.navigateToSelectedResult();
              event.consume();
            }
            default -> {
              // other keys handled normally
            }
          }
        });
  }

  /**
   * Rebuilds the ingredient-filter chip buttons whenever the filter list changes.
   *
   * <p>Each chip is a small button labelled {@code "× <term>"}. Clicking it removes that filter.
   */
  private void rebuildFilterChips() {
    filterChipsPane.getChildren().clear();
    for (String filter : viewModel.ingredientFiltersProperty()) {
      Button chip = new Button("× " + filter);
      chip.getStyleClass().add("search-chip");
      chip.setOnAction(e -> viewModel.removeIngredientFilter(filter));
      filterChipsPane.getChildren().add(chip);
    }
  }

  /**
   * Scrolls the results ListView to the currently selected result and selects it, so the user can
   * see which item the ↑/↓ navigation has highlighted.
   */
  private void syncListSelection() {
    String selectedId = viewModel.getSelectedResultId();
    if (selectedId == null) {
      resultsList.getSelectionModel().clearSelection();
      return;
    }
    var items = resultsList.getItems();
    for (int i = 0; i < items.size(); i++) {
      if (items.get(i).id().equals(selectedId)) {
        resultsList.getSelectionModel().select(i);
        resultsList.scrollTo(i);
        return;
      }
    }
  }

  /**
   * Two-line custom list cell for search results.
   *
   * <p>The top line shows the recipe title in bold; the bottom line shows {@code "in <collection>"}
   * in a smaller gray font, matching the spec's "collection context" requirement. If the recipe
   * does not belong to any collection the second line is hidden.
   */
  private static final class RecipeEntryCell extends ListCell<RecipeEntry> {

    private final Label titleLabel = new Label();
    private final Label collectionLabel = new Label();
    private final VBox cellBox;

    RecipeEntryCell() {
      titleLabel.setStyle("-fx-font-weight: bold;");
      collectionLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #666666;");
      cellBox = new VBox(2, titleLabel, collectionLabel);
    }

    @Override
    protected void updateItem(@org.jspecify.annotations.Nullable RecipeEntry item, boolean empty) {
      super.updateItem(item, empty);
      if (empty || item == null) {
        setGraphic(null);
      } else {
        titleLabel.setText(item.title());
        String coll = item.collectionTitle();
        collectionLabel.setText(coll.isBlank() ? "" : "in " + coll);
        collectionLabel.setVisible(!coll.isBlank());
        collectionLabel.setManaged(!coll.isBlank());
        setGraphic(cellBox);
      }
    }
  }
}
