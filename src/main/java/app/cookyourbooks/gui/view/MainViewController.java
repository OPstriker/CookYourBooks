package app.cookyourbooks.gui.view;

import java.util.EnumMap;
import java.util.Map;

import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

import app.cookyourbooks.gui.NavigationService;
import app.cookyourbooks.gui.NavigationService.View;
import app.cookyourbooks.gui.viewmodel.ShoppingListResultViewModel;
import app.cookyourbooks.gui.viewmodel.ShoppingListViewModel;

/**
 * Controller for the main application layout ({@code MainView.fxml}).
 *
 * <p>Manages the sidebar navigation buttons and swaps the content area when the user navigates
 * between features. Each feature's view is provided via {@link #setViewNode(View, Node)} during
 * application startup.
 *
 * <h2>How navigation works</h2>
 *
 * <ol>
 *   <li>The user clicks a sidebar button (e.g., "Library")
 *   <li>The button handler calls {@link NavigationService#navigateTo(View)}
 *   <li>The navigation listener in this controller swaps the content area to show the corresponding
 *       feature view
 * </ol>
 */
@SuppressWarnings(
    "NullAway.Init") // FXML fields are injected by the FXMLLoader, not the constructor
public class MainViewController {

  @FXML private StackPane contentArea;
  @FXML private Button homeButton; // navigates to Library; hidden on Home and Library views
  @FXML private Button shoppingListButton; // opens shopping list; TODO: wire later
  @FXML private Button darkModeButton; // toggles dark mode; TODO: wire later
  @FXML private Button topImportButton; // navigates to Import; visible only in Library view
  @FXML private Button searchButton; // navigates to Search; visible only in Library view

  private final NavigationService navigationService;
  private final ShoppingListViewModel shoppingListVm;
  private final ShoppingListResultViewModel resultVm;
  private final Map<View, Node> viewNodes = new EnumMap<>(View.class);

  /**
   * Constructs the main view controller.
   *
   * @param navigationService the shared navigation service
   * @param shoppingListVm the Shopping List selection ViewModel
   * @param resultVm the Shopping List result ViewModel (used to check if a list already exists)
   */
  public MainViewController(
      NavigationService navigationService,
      ShoppingListViewModel shoppingListVm,
      ShoppingListResultViewModel resultVm) {
    this.navigationService = navigationService;
    this.shoppingListVm = shoppingListVm;
    this.resultVm = resultVm;
  }

  /**
   * Registers a feature view's root node for a given navigation view.
   *
   * <p>Call this during app startup for each feature that has been implemented. Views that are not
   * registered will show a placeholder when navigated to.
   *
   * @param view the navigation view
   * @param node the root node of the feature's FXML view
   */
  public void setViewNode(View view, Node node) {
    viewNodes.put(view, node);
  }

  /** Called by FXML after the layout is loaded. Sets up button handlers and navigation listener. */
  @SuppressWarnings("UnusedMethod") // Called reflectively by FXMLLoader
  @FXML
  private void initialize() {
    // Back button navigates to Library from any inner view.
    homeButton.setOnAction(e -> navigationService.navigateTo(View.LIBRARY));

    // Import and Search are only visible/functional from the Library view.
    topImportButton.setOnAction(e -> navigationService.navigateTo(View.IMPORT));
    searchButton.setOnAction(e -> navigationService.navigateTo(View.SEARCH));

    // If a shopping list already exists, go straight to it.
    // Otherwise, enter selection mode so the user can pick recipes.
    shoppingListButton.setOnAction(
        e -> {
          if (!resultVm.sectionsProperty().isEmpty()) {
            navigationService.navigateTo(View.SHOPPING_LIST);
          } else {
            shoppingListVm.enter();
            navigationService.navigateTo(View.LIBRARY);
          }
        });

    // Listen for navigation changes and swap the content area.
    navigationService
        .currentViewProperty()
        .addListener((obs, oldView, newView) -> showView(newView));

    // Show the initial view.
    showView(navigationService.getCurrentView());
  }

  private void showView(View view) {
    contentArea.getChildren().clear();
    Node node = viewNodes.get(view);
    if (node != null) {
      contentArea.getChildren().add(node);
    } else {
      // Placeholder for features not yet implemented.
      Label placeholder = new Label(view.name() + " — not yet implemented");
      placeholder.setStyle("-fx-text-fill: #888; -fx-font-size: 16;");
      contentArea.getChildren().add(placeholder);
    }

    // Back button: shown on inner views (not on Home or Library).
    boolean showBack = view != View.HOME && view != View.LIBRARY;
    homeButton.setVisible(showBack);
    homeButton.setManaged(showBack);

    // Import and Search buttons: only shown on the Library view.
    boolean onLibrary = view == View.LIBRARY;
    topImportButton.setVisible(onLibrary);
    topImportButton.setManaged(onLibrary);
    searchButton.setVisible(onLibrary);
    searchButton.setManaged(onLibrary);
    shoppingListButton.setVisible(onLibrary);
    shoppingListButton.setManaged(onLibrary);
    darkModeButton.setVisible(onLibrary);
    darkModeButton.setManaged(onLibrary);
  }
}
