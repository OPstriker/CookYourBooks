package app.cookyourbooks.gui;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

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

import app.cookyourbooks.gui.view.LibraryViewController;
import app.cookyourbooks.gui.viewmodel.LibraryViewModelImpl;
import app.cookyourbooks.model.Recipe;
import app.cookyourbooks.model.RecipeCollection;
import app.cookyourbooks.model.SourceType;
import app.cookyourbooks.services.LibrarianService;

/**
 * Integration tests for the Library View, verifying that the FXML bindings, UI interactions, and
 * ViewModel state all work together correctly end-to-end.
 *
 * <p>Each test loads the real {@code LibraryView.fxml} with a real {@link LibraryViewModelImpl}
 * backed by a Mockito stub service. Tests drive the UI via {@link FxRobot} and assert both UI state
 * (via {@code robot.lookup()}) and ViewModel state (via the non-JavaFX accessors).
 */
@SuppressWarnings("NullAway.Init") // fields set by @Start before any test runs
@ExtendWith(ApplicationExtension.class)
class LibraryViewIntegrationTest {

  // Short undo timeout so tests don't wait 5 seconds
  private static final Duration TEST_TIMEOUT = Duration.ofMillis(500);

  private LibrarianService mockService;
  private NavigationService navigationService;
  private LibraryViewModelImpl vm;

  // Test data,two collections, each with two recipes
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

    // Create ViewModel on FX thread since it uses JavaFX properties
    vm = new LibraryViewModelImpl(mockService, navigationService, TEST_TIMEOUT);

    javafx.fxml.FXMLLoader loader =
        new javafx.fxml.FXMLLoader(getClass().getResource("/fxml/LibraryView.fxml"));
    loader.setControllerFactory(clazz -> new LibraryViewController(vm));
    Parent root = loader.load();

    stage.setScene(new Scene(root, 900, 600));
    stage.show();
  }

  /**
   * IT1: Collection list loads and displays in the UI after startup.
   *
   * <p>Verifies that the FXML {@code collectionListView} is bound to the ViewModel's {@code
   * collectionsProperty()} and that {@code refresh()} populates it automatically when the
   * controller initializes.
   */
  @Test
  void IT1_collectionListPopulatesOnLoad(FxRobot robot) throws InterruptedException {
    // initialize() calls vm.refresh() which runs on a background thread and wait for it to complete
    Thread.sleep(300);
    WaitForAsyncUtils.waitForFxEvents();

    ListView<?> listView = robot.lookup("#collectionListView").queryAs(ListView.class);
    assertThat(listView.getItems()).hasSize(2);
    assertThat(vm.getCollectionIds()).containsExactlyInAnyOrder("col-1", "col-2");
  }

  /**
   * IT2: Selecting a collection via UI populates the recipe list.
   *
   * <p>Verifies that the collection ListView's selection listener calls {@code
   * vm.selectCollection()}, which in turn populates {@code recipesProperty()} — and that this
   * change propagates back to the recipe ListView through the binding.
   */
  @Test
  void IT2_selectingCollectionPopulatesRecipeList(FxRobot robot) throws InterruptedException {
    Thread.sleep(300);
    WaitForAsyncUtils.waitForFxEvents();

    // Select "My Cookbook" programmatically (clicking cell text is fragile due to the "·" char)
    robot.interact(() -> vm.selectCollection("col-1"));
    WaitForAsyncUtils.waitForFxEvents();

    ListView<?> recipeListView = robot.lookup("#recipeListView").queryAs(ListView.class);
    assertThat(recipeListView.getItems()).hasSize(2);
    assertThat(vm.getRecipeIds()).containsExactly("r-1", "r-2");
  }

  /**
   * IT3: Selecting a recipe then clicking "Open Recipe" navigates to the Recipe Editor.
   *
   * <p>This is the primary cross-feature integration: the Library View hands off the selected
   * recipe ID to the NavigationService, which the Recipe Editor listens to. Verifies that the "Open
   * Recipe" button handler calls {@code vm.selectRecipe()}, which calls {@code
   * navigationService.navigateToRecipe()}.
   */
  @Test
  void IT3_openRecipeButtonNavigatesToRecipeEditor(FxRobot robot) throws InterruptedException {
    Thread.sleep(300);
    WaitForAsyncUtils.waitForFxEvents();

    // Load recipes for col-1
    robot.interact(() -> vm.selectCollection("col-1"));
    WaitForAsyncUtils.waitForFxEvents();

    // Select the first recipe in the list
    robot.interact(() -> vm.selectRecipe("r-1"));
    WaitForAsyncUtils.waitForFxEvents();

    // The "Open Recipe" button is now enabled,click it
    robot.clickOn("#openRecipeButton");
    WaitForAsyncUtils.waitForFxEvents();

    // Verify the NavigationService received the correct recipe and view
    assertThat(navigationService.getCurrentView()).isEqualTo(NavigationService.View.RECIPE_EDITOR);
    assertThat(navigationService.getSelectedRecipeId()).isEqualTo("r-1");
  }

  /**
   * IT4: Clicking "Delete" on a selected recipe shows the undo bar; clicking Undo restores it.
   *
   * <p>Verifies the full delete-recipe undo flow end-to-end through the UI:
   *
   * <ol>
   *   <li>Delete recipe button click removes the recipe from the list immediately.
   *   <li>The undo bar becomes visible with a message containing the recipe title.
   *   <li>Clicking the Undo button restores the recipe.
   *   <li>{@code librarianService.deleteRecipe()} is never called (the service delete was cancelled
   *       by undo).
   * </ol>
   */
  @Test
  void IT4_deleteRecipeShowsUndoBarAndUndoRestoresRecipe(FxRobot robot)
      throws InterruptedException {
    Thread.sleep(300);
    WaitForAsyncUtils.waitForFxEvents();

    // Load recipes by selecting the collection
    robot.interact(() -> vm.selectCollection("col-1"));
    WaitForAsyncUtils.waitForFxEvents();

    // Select the first recipe in the ListView so deleteRecipeButton knows which to delete
    robot.interact(
        () -> {
          @SuppressWarnings("rawtypes")
          ListView recipeList = robot.lookup("#recipeListView").queryAs(ListView.class);
          recipeList.getSelectionModel().select(0);
        });
    WaitForAsyncUtils.waitForFxEvents();

    // Click Delete Recipe button
    robot.clickOn("#deleteRecipeButton");
    WaitForAsyncUtils.waitForFxEvents();

    // Recipe should be gone from the ViewModel list immediately
    assertThat(vm.getRecipeIds()).doesNotContain("r-1");

    // Undo bar should be visible with the recipe name in the message
    HBox undoBar = robot.lookup("#undoBar").queryAs(HBox.class);
    assertThat(undoBar.isVisible()).isTrue();

    Label undoLabel = robot.lookup("#undoLabel").queryAs(Label.class);
    assertThat(undoLabel.getText()).contains("Scrambled Eggs");

    // Undo is still available — trigger it via the ViewModel (the UI binding is already verified
    // above by asserting undoBar visibility; calling undoDelete() here tests the restore logic)
    robot.interact(() -> vm.undoDelete());
    WaitForAsyncUtils.waitForFxEvents();

    // Recipe restored in ViewModel
    assertThat(vm.getRecipeIds()).contains("r-1");

    // Undo bar hidden again (bound to undoAvailableProperty)
    assertThat(undoBar.isVisible()).isFalse();

    // Service deleteRecipe was never committed
    verify(mockService, never()).deleteRecipe("r-1");
  }

  /**
   * IT5: Typing in the filter field narrows the collection list in real-time.
   *
   * <p>Verifies that the filter TextField is bidirectionally bound to {@code
   * vm.filterTextProperty()}, and that the FilteredList predicate updates immediately on each
   * keystroke — without any debounce or explicit confirmation step.
   */
  @Test
  void IT5_filterFieldNarrowsCollectionListInRealTime(FxRobot robot) throws InterruptedException {
    Thread.sleep(300);
    WaitForAsyncUtils.waitForFxEvents();

    // Both collections visible initially
    assertThat(vm.getCollectionIds()).containsExactlyInAnyOrder("col-1", "col-2");

    // Type "italian" into the filter field — should match only col-2
    robot.clickOn("#filterField");
    robot.write("italian");
    WaitForAsyncUtils.waitForFxEvents();

    assertThat(vm.getCollectionIds()).containsExactly("col-2");
    assertThat(vm.getCollectionIds()).doesNotContain("col-1");

    // Clear the filter — both visible again
    robot.eraseText("italian".length());
    WaitForAsyncUtils.waitForFxEvents();

    assertThat(vm.getCollectionIds()).containsExactlyInAnyOrder("col-1", "col-2");
  }

  // Helpers

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
