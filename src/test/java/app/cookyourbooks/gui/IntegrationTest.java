package app.cookyourbooks.gui;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import app.cookyourbooks.CybLibrary;
import app.cookyourbooks.gui.view.ImportViewController;
import app.cookyourbooks.gui.view.LibraryViewController;
import app.cookyourbooks.gui.view.MainViewController;
import app.cookyourbooks.gui.view.RecipeEditorViewController;
import app.cookyourbooks.gui.view.SearchViewController;
import app.cookyourbooks.gui.viewmodel.ImportViewModelImpl;
import app.cookyourbooks.gui.viewmodel.LibraryViewModelImpl;
import app.cookyourbooks.gui.viewmodel.RecipeEditorViewModelImpl;
import app.cookyourbooks.gui.viewmodel.SearchViewModelImpl;
import app.cookyourbooks.model.Recipe;
import app.cookyourbooks.model.RecipeCollection;
import app.cookyourbooks.model.SourceType;
import app.cookyourbooks.services.LibrarianService;
import app.cookyourbooks.services.LibrarianServiceImpl;
import app.cookyourbooks.services.ocr.FakeRecipeOcrService;

/**
 * Integration tests for the Library View, verifying that the FXML bindings, UI interactions, and
 * ViewModel state all work together correctly end-to-end.
 *
 * <p>IT1–IT5 load only LibraryView.fxml with a mock service. IT6 and IT7 load the full integrated
 * app (real MainView + all features wired) to verify genuine cross-feature behaviour.
 */
@SuppressWarnings("NullAway.Init") // fields set by @Start before any test runs
@ExtendWith(ApplicationExtension.class)
class IntegrationTest {

  // Short undo timeout so tests don't wait 5 seconds
  private static final Duration TEST_TIMEOUT = Duration.ofMillis(500);

  // ── Shared across all tests ───────────────────────────────────────────────
  private LibrarianService mockService;
  private NavigationService navigationService;
  private LibraryViewModelImpl vm;
  private ImportViewModelImpl importVm;

  // ── Used only by IT6 / IT7 (full app wiring) ─────────────────────────────
  private LibraryViewModelImpl fullLibraryVm;
  private ImportViewModelImpl fullImportVm;
  private NavigationService fullNavService;

  // Test data
  private Recipe recipe1;
  private Recipe recipe2;
  private Recipe recipe3;
  private RecipeCollection col1;
  private RecipeCollection col2;

  @SuppressWarnings("UnusedMethod") // called reflectively by TestFX
  @Start
  private void start(Stage stage) throws Exception {
    mockService = mock(LibrarianService.class);
    navigationService = new NavigationService();

    // Build test data
    recipe1 = mockRecipe("r-1", "Scrambled Eggs");
    recipe2 = mockRecipe("r-2", "Guacamole");
    recipe3 = mockRecipe("r-3", "Mapo Tofu");
    col1 = mockCollectionWithRecipes("col-1", "My Cookbook", SourceType.PERSONAL, recipe1, recipe2);
    col2 = mockCollectionWithRecipes("col-2", "Italian Food", SourceType.PERSONAL, recipe3);

    when(mockService.listCollections()).thenReturn(List.of(col1, col2));
    when(mockService.findCollectionById("col-1")).thenReturn(Optional.of(col1));
    when(mockService.findCollectionById("col-2")).thenReturn(Optional.of(col2));
    when(mockService.resolveRecipes("Scrambled")).thenReturn(List.of(recipe1));
    when(mockService.listAllRecipes()).thenReturn(List.of(recipe1, recipe2, recipe3));

    vm = new LibraryViewModelImpl(mockService, navigationService, TEST_TIMEOUT);
    importVm = new ImportViewModelImpl(new FakeRecipeOcrService(300), mockService);

    javafx.fxml.FXMLLoader loader =
        new javafx.fxml.FXMLLoader(getClass().getResource("/fxml/LibraryView.fxml"));
    loader.setControllerFactory(clazz -> new LibraryViewController(vm));
    Parent root = loader.load();

    stage.setScene(new Scene(root, 900, 600));
    stage.show();
  }

  // ── IT1–IT5: Library View unit-level integration tests ───────────────────

  /** IT1: Collection list loads and displays in the UI after startup. */
  @Test
  void IT1_collectionListPopulatesOnLoad(FxRobot robot) throws InterruptedException {
    Thread.sleep(300);
    WaitForAsyncUtils.waitForFxEvents();

    ListView<?> listView = robot.lookup("#collectionListView").queryAs(ListView.class);
    assertThat(listView.getItems()).hasSize(2);
    assertThat(vm.getCollectionIds()).containsExactlyInAnyOrder("col-1", "col-2");
  }

  /** IT2: Selecting a collection via UI populates the recipe list. */
  @Test
  void IT2_selectingCollectionPopulatesRecipeList(FxRobot robot) throws InterruptedException {
    Thread.sleep(300);
    WaitForAsyncUtils.waitForFxEvents();

    robot.interact(() -> vm.selectCollection("col-1"));
    WaitForAsyncUtils.waitForFxEvents();

    ListView<?> recipeListView = robot.lookup("#recipeListView").queryAs(ListView.class);
    assertThat(recipeListView.getItems()).hasSize(2);
    assertThat(vm.getRecipeIds()).containsExactly("r-1", "r-2");
  }

  /**
   * IT3: Selecting a recipe then clicking "Open Recipe" navigates to the Recipe Editor.
   *
   * <p>Cross-feature: Library View → Recipe Editor via NavigationService.
   */
  @Test
  void IT3_openRecipeButtonNavigatesToRecipeEditor(FxRobot robot) throws InterruptedException {
    Thread.sleep(300);
    WaitForAsyncUtils.waitForFxEvents();

    robot.interact(() -> vm.selectCollection("col-1"));
    WaitForAsyncUtils.waitForFxEvents();

    robot.interact(() -> vm.selectRecipe("r-1"));
    WaitForAsyncUtils.waitForFxEvents();

    robot.clickOn("#openRecipeButton");
    WaitForAsyncUtils.waitForFxEvents();

    assertThat(navigationService.getCurrentView()).isEqualTo(NavigationService.View.RECIPE_EDITOR);
    assertThat(navigationService.getSelectedRecipeId()).isEqualTo("r-1");
  }

  /**
   * IT4: Clicking "Delete" on a selected collection shows the undo bar; clicking Undo restores it.
   */
  @Test
  void IT4_deleteCollectionShowsUndoBarAndUndoRestoresCollection(FxRobot robot)
      throws InterruptedException {
    Thread.sleep(300);
    WaitForAsyncUtils.waitForFxEvents();

    robot.interact(
        () -> {
          @SuppressWarnings("rawtypes")
          ListView colList = robot.lookup("#collectionListView").queryAs(ListView.class);
          colList.getSelectionModel().select(0);
        });
    WaitForAsyncUtils.waitForFxEvents();

    robot.clickOn("#deleteButton");
    WaitForAsyncUtils.waitForFxEvents();

    assertThat(vm.getCollectionIds()).hasSize(1);

    HBox undoBar = robot.lookup("#undoBar").queryAs(HBox.class);
    assertThat(undoBar.isVisible()).isTrue();

    Label undoLabel = robot.lookup("#undoLabel").queryAs(Label.class);
    assertThat(undoLabel.getText()).isNotBlank();

    robot.interact(() -> vm.undoDelete());
    WaitForAsyncUtils.waitForFxEvents();

    assertThat(vm.getCollectionIds()).hasSize(2);
    assertThat(undoBar.isVisible()).isFalse();

    verify(mockService, never()).deleteCollection("col-1");
  }

  /** IT5: Typing in the filter field narrows the collection list in real-time. */
  @Test
  void IT5_filterFieldNarrowsCollectionListInRealTime(FxRobot robot) throws InterruptedException {
    Thread.sleep(300);
    WaitForAsyncUtils.waitForFxEvents();

    assertThat(vm.getCollectionIds()).containsExactlyInAnyOrder("col-1", "col-2");

    robot.clickOn("#filterField");
    robot.write("italian");
    WaitForAsyncUtils.waitForFxEvents();

    assertThat(vm.getCollectionIds()).containsExactly("col-2");
    assertThat(vm.getCollectionIds()).doesNotContain("col-1");

    robot.eraseText("italian".length());
    WaitForAsyncUtils.waitForFxEvents();

    assertThat(vm.getCollectionIds()).containsExactlyInAnyOrder("col-1", "col-2");
  }

  // ── IT6 / IT7: full integrated app ───────────────────────────────────────

  /**
   * Wires the complete application (same pattern as CookYourBooksGuiApp) and shows it in a new
   * Stage. Called by IT6 and IT7 before their assertions.
   */
  private Stage buildFullApp() throws Exception {
    CybLibrary library = CybLibrary.load(Path.of("cyb-library.json"));
    var librarianService =
        new LibrarianServiceImpl(
            library.getRecipeRepository(), library.getCollectionRepository(), library);

    fullNavService = new NavigationService();
    var mainController = new MainViewController(fullNavService);

    // Library View
    fullLibraryVm =
        new LibraryViewModelImpl(librarianService, fullNavService, Duration.ofSeconds(5));
    javafx.fxml.FXMLLoader libraryLoader =
        new javafx.fxml.FXMLLoader(getClass().getResource("/fxml/LibraryView.fxml"));
    libraryLoader.setControllerFactory(clazz -> new LibraryViewController(fullLibraryVm));
    Parent libraryView = libraryLoader.load();
    mainController.setViewNode(NavigationService.View.LIBRARY, libraryView);

    // Recipe Editor
    var recipeEditorVm = new RecipeEditorViewModelImpl(library.getRecipeRepository());
    javafx.fxml.FXMLLoader recipeLoader =
        new javafx.fxml.FXMLLoader(getClass().getResource("/fxml/RecipeEditorView.fxml"));
    recipeLoader.setControllerFactory(
        clazz -> new RecipeEditorViewController(recipeEditorVm, fullNavService));
    fullNavService
        .selectedRecipeIdProperty()
        .addListener(
            (obs, oldId, newId) -> {
              if (newId != null) recipeEditorVm.loadRecipe(newId);
            });
    mainController.setViewNode(NavigationService.View.RECIPE_EDITOR, recipeLoader.load());

    // Import Interface (use FakeRecipeOcrService so no real API key needed for IT7)
    fullImportVm = new ImportViewModelImpl(new FakeRecipeOcrService(300), librarianService);
    var importEditorVm =
        new RecipeEditorViewModelImpl(library.getRecipeRepository(), librarianService);
    javafx.fxml.FXMLLoader importEditorLoader =
        new javafx.fxml.FXMLLoader(getClass().getResource("/fxml/RecipeEditorView.fxml"));
    importEditorLoader.setControllerFactory(
        clazz -> new RecipeEditorViewController(importEditorVm, fullNavService));
    Parent importEditorView = importEditorLoader.load();
    javafx.fxml.FXMLLoader importLoader =
        new javafx.fxml.FXMLLoader(getClass().getResource("/fxml/ImportView.fxml"));
    importLoader.setControllerFactory(
        clazz ->
            new ImportViewController(
                fullImportVm, importEditorVm, importEditorView, fullNavService));
    mainController.setViewNode(NavigationService.View.IMPORT, importLoader.load());

    // Search & Filter
    var searchVm =
        new SearchViewModelImpl(librarianService, fullNavService, Duration.ofMillis(300));
    javafx.fxml.FXMLLoader searchLoader =
        new javafx.fxml.FXMLLoader(getClass().getResource("/fxml/SearchView.fxml"));
    searchLoader.setController(new SearchViewController(searchVm));
    mainController.setViewNode(NavigationService.View.SEARCH, searchLoader.load());

    // Auto-refresh Library on navigation
    fullNavService
        .currentViewProperty()
        .addListener(
            (obs, oldView, newView) -> {
              if (newView == NavigationService.View.LIBRARY) fullLibraryVm.refresh();
              if (newView == NavigationService.View.SEARCH) searchVm.clearFilters();
              if (newView == NavigationService.View.IMPORT) fullImportVm.loadCollections();
            });

    // Main layout
    javafx.fxml.FXMLLoader mainLoader =
        new javafx.fxml.FXMLLoader(getClass().getResource("/fxml/MainView.fxml"));
    mainLoader.setController(mainController);
    Parent root = mainLoader.load();

    Stage fullStage = new Stage();
    fullStage.setScene(new Scene(root, 960, 640));
    fullStage.show();
    return fullStage;
  }

  /**
   * IT6: Clicking the real Search button in the integrated app navigates to Search & Filter.
   *
   * <p>Cross-feature: loads the full MainView, clicks #searchButton in the top bar, and asserts
   * NavigationService switches to SEARCH — verifying Library View → Search & Filter integration.
   */
  @Test
  void IT6_searchButtonInFullAppNavigatesToSearch(FxRobot robot) throws Exception {
    robot.interact(
        () -> {
          try {
            buildFullApp();
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
    Thread.sleep(300);
    WaitForAsyncUtils.waitForFxEvents();

    // Navigate to Library first so the Search button becomes visible
    robot.interact(() -> fullNavService.navigateTo(NavigationService.View.LIBRARY));
    WaitForAsyncUtils.waitForFxEvents();

    robot.clickOn("#searchButton");
    WaitForAsyncUtils.waitForFxEvents();

    assertThat(fullNavService.getCurrentView()).isEqualTo(NavigationService.View.SEARCH);
  }

  /**
   * IT7: After a successful import, returnToLibrary() navigates to the Library and the Library
   * ViewModel reflects the real collection from cyb-library.json.
   *
   * <p>Cross-feature: Import Interface → Library View. Verifies that after completing the full
   * import flow the Library shows the known "test" collection (id 537c4365-...) from the real data
   * file, confirming the two features share the same underlying data store.
   */
  @Test
  void IT7_afterImportReturnToLibraryShowsCollection(FxRobot robot) throws Exception {
    robot.interact(
        () -> {
          try {
            buildFullApp();
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
    Thread.sleep(300);
    WaitForAsyncUtils.waitForFxEvents();

    robot.interact(
        () -> fullImportVm.selectTargetCollection("537c4365-b108-436a-923c-65a19ece4fc4"));
    robot.interact(() -> fullImportVm.startImport(Path.of("test.jpg")));
    Thread.sleep(600);
    WaitForAsyncUtils.waitForFxEvents();

    assertThat(fullImportVm.getState()).isEqualTo("review");

    robot.interact(() -> fullImportVm.acceptImport());
    WaitForAsyncUtils.waitForFxEvents();

    robot.interact(() -> fullImportVm.returnToLibrary());
    robot.interact(() -> fullNavService.navigateTo(NavigationService.View.LIBRARY));
    Thread.sleep(300);
    WaitForAsyncUtils.waitForFxEvents();

    assertThat(fullNavService.getCurrentView()).isEqualTo(NavigationService.View.LIBRARY);

    robot.interact(() -> fullLibraryVm.selectCollection("537c4365-b108-436a-923c-65a19ece4fc4"));
    WaitForAsyncUtils.waitForFxEvents();

    assertThat(fullLibraryVm.getRecipeIds()).isNotEmpty();
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  private RecipeCollection mockCollectionWithRecipes(
      String id, String title, SourceType sourceType, Recipe... recipes) {
    RecipeCollection col = mock(RecipeCollection.class);
    when(col.getId()).thenReturn(id);
    when(col.getTitle()).thenReturn(title);
    when(col.getSourceType()).thenReturn(sourceType);
    when(col.getRecipes()).thenReturn(List.of(recipes));
    return col;
  }

  private Recipe mockRecipe(String id, String title) {
    Recipe r = mock(Recipe.class);
    when(r.getId()).thenReturn(id);
    when(r.getTitle()).thenReturn(title);
    return r;
  }
}
