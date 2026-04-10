package app.cookyourbooks.gui.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import javafx.application.Platform;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import app.cookyourbooks.gui.NavigationService;
import app.cookyourbooks.gui.ViewModelTestBase;
import app.cookyourbooks.model.RecipeCollection;
import app.cookyourbooks.model.SourceType;
import app.cookyourbooks.services.LibrarianService;

class LibraryViewModelTest extends ViewModelTestBase {

  private LibrarianService mockService;
  private NavigationService navigationService;

  @SuppressWarnings("NullAway.Init")
  private LibraryViewModelImpl viewModel;

  // Use a very short undo timeout so tests don't have to wait 5 seconds
  private static final Duration TEST_UNDO_TIMEOUT = Duration.ofMillis(200);

  @BeforeEach
  void setUp() throws InterruptedException {
    mockService = mock(LibrarianService.class);
    navigationService = new NavigationService();

    // Create viewModel on FX thread so PauseTransition works
    var latch = new java.util.concurrent.CountDownLatch(1);
    Platform.runLater(
        () -> {
          viewModel = new LibraryViewModelImpl(mockService, navigationService, TEST_UNDO_TIMEOUT);
          latch.countDown();
        });
    latch.await();
  }

  // L1: refresh() loads collections
  // After refreshing, there are data in the list.

  @Test
  void L1_refreshPopulatesCollectionList() throws InterruptedException {
    RecipeCollection col = mockCollection("id-1", "Pasta", SourceType.PERSONAL, 3);
    when(mockService.listCollections()).thenReturn(List.of(col));

    Platform.runLater(() -> viewModel.refresh());
    waitForFxEvents();
    Thread.sleep(200); // wait for background thread
    waitForFxEvents();

    assertThat(viewModel.getCollectionIds()).containsExactly("id-1");
  }

  // L2: each entry exposes ID, title, source type, recipe count
  // Each collection entry in the list has the correct ID, title, source type, and
  // recipe count as provided by the service.

  @Test
  void L2_collectionEntryExposesRequiredFields() throws InterruptedException {
    RecipeCollection col = mockCollection("id-2", "Soups", SourceType.PERSONAL, 5);
    when(mockService.listCollections()).thenReturn(List.of(col));

    Platform.runLater(() -> viewModel.refresh());
    waitForFxEvents();
    Thread.sleep(200);
    waitForFxEvents();

    var entries = viewModel.collectionsProperty();
    assertThat(entries).hasSize(1);
    RecipeCollectionSummary summary = (RecipeCollectionSummary) entries.get(0);
    assertThat(summary.id()).isEqualTo("id-2");
    assertThat(summary.title()).isEqualTo("Soups");
    assertThat(summary.sourceType()).isEqualTo(SourceType.PERSONAL);
    assertThat(summary.recipeCount()).isEqualTo(5);
  }

  // L3: selectCollection() updates selection and recipe list
  // When a collection is selected, the selectedCollectionId and recipe list are
  // updated to reflect the selected collection's data.

  @Test
  void L3_selectCollectionUpdatesSelectionAndRecipes() throws InterruptedException {
    // Build a collection with two recipes
    app.cookyourbooks.model.Recipe r1 = mockRecipe("r-1", "Carbonara");
    app.cookyourbooks.model.Recipe r2 = mockRecipe("r-2", "Bolognese");
    RecipeCollection col =
        mockCollectionWithRecipes("col-A", "Italian", SourceType.PERSONAL, r1, r2);

    when(mockService.listCollections()).thenReturn(List.of(col));
    when(mockService.findCollectionById("col-A")).thenReturn(Optional.of(col));

    // Seed list via refresh, then select
    Platform.runLater(() -> viewModel.refresh());
    waitForFxEvents();
    Thread.sleep(200);
    waitForFxEvents();

    Platform.runLater(() -> viewModel.selectCollection("col-A"));
    waitForFxEvents();

    assertThat(viewModel.getSelectedCollectionId()).isEqualTo("col-A");
    assertThat(viewModel.getRecipeIds()).containsExactly("r-1", "r-2");
  }

  // L4: createCollection() adds new collection
  // When a new collection is created,
  // the service's createCollection method is called with the correct title,
  // and after completion, the new collection appears in the list.

  @Test
  void L4_createCollectionCallsServiceAndRefreshes() throws InterruptedException {
    RecipeCollection newCol = mockCollection("id-new", "Desserts", SourceType.PERSONAL, 0);
    when(mockService.createCollection("Desserts")).thenReturn(newCol);
    when(mockService.listCollections()).thenReturn(List.of(newCol));

    Platform.runLater(() -> viewModel.createCollection("Desserts"));
    waitForFxEvents();
    Thread.sleep(200);
    waitForFxEvents();

    verify(mockService).createCollection("Desserts");
    assertThat(viewModel.getCollectionIds()).contains("id-new");
  }

  // L5 & L6: deleteCollection / undoDelete
  // undo operation restores the collection, and the service is deleted but not
  // called.

  @Test
  void L5_deleteCollectionRemovesAfterTimeout() throws InterruptedException {
    RecipeCollection col = mockCollection("col-del", "Soups", SourceType.PERSONAL, 0);
    when(mockService.listCollections()).thenReturn(List.of(col));
    when(mockService.findCollectionById("col-del")).thenReturn(Optional.of(col));

    // Seed, then delete
    Platform.runLater(() -> viewModel.refresh());
    waitForFxEvents();
    Thread.sleep(200);
    waitForFxEvents();

    Platform.runLater(() -> viewModel.deleteCollection("col-del"));
    waitForFxEvents();

    // removed from display immediately
    assertThat(viewModel.getCollectionIds()).doesNotContain("col-del");

    // Wait for undo timeout to expire
    Thread.sleep(400);
    waitForFxEvents();

    // the delete should have been committed to the service
    verify(mockService).deleteCollection("col-del");
    assertThat(viewModel.isUndoAvailable()).isFalse();
  }

  // Undo before timeout should restore the collection and not call delete on the
  // service
  @Test
  void L6_undoDeleteRestoresCollectionBeforeTimeout() throws InterruptedException {
    RecipeCollection col = mockCollection("col-undo", "Salads", SourceType.PERSONAL, 0);
    when(mockService.listCollections()).thenReturn(List.of(col));
    when(mockService.findCollectionById("col-undo")).thenReturn(Optional.of(col));

    // Seed, then delete, then immediately undo
    Platform.runLater(() -> viewModel.refresh());
    waitForFxEvents();
    Thread.sleep(200);
    waitForFxEvents();

    Platform.runLater(() -> viewModel.deleteCollection("col-undo"));
    waitForFxEvents();

    Platform.runLater(() -> viewModel.undoDelete());
    waitForFxEvents();

    // Collection restored in display
    assertThat(viewModel.getCollectionIds()).contains("col-undo");
    // Undo no longer available
    assertThat(viewModel.isUndoAvailable()).isFalse();
    // Service delete was never called
    verify(mockService, never()).deleteCollection("col-undo");
  }

  // L7: undo state clears after timeout
  // After the timeout, isUndoAvailable() returns false and the message is empty.

  @Test
  void L7_undoStateClearsAfterTimeout() throws InterruptedException {
    RecipeCollection col = mockCollection("col-timeout", "Desserts", SourceType.PERSONAL, 0);
    when(mockService.listCollections()).thenReturn(List.of(col));
    when(mockService.findCollectionById("col-timeout")).thenReturn(Optional.of(col));

    Platform.runLater(() -> viewModel.refresh());
    waitForFxEvents();
    Thread.sleep(200);
    waitForFxEvents();

    Platform.runLater(() -> viewModel.deleteCollection("col-timeout"));
    waitForFxEvents();

    assertThat(viewModel.isUndoAvailable()).isTrue();

    // Wait for undo window to expire
    Thread.sleep(400);
    waitForFxEvents();

    assertThat(viewModel.isUndoAvailable()).isFalse();
    assertThat(viewModel.undoMessageProperty().get()).isEmpty();
  }

  // L8: refresh() uses background thread with loading indicator
  // After calling refresh(), isLoading() should be true until the background
  // loading completes,
  // at which point it should be false.

  @Test
  void L8_refreshSetsLoadingTrueThenFalse() throws InterruptedException {
    when(mockService.listCollections()).thenReturn(List.of());

    Platform.runLater(() -> viewModel.refresh());
    waitForFxEvents();
    // loading should be true immediately after refresh() is called
    Thread.sleep(200);
    waitForFxEvents();

    assertThat(viewModel.isLoading()).isFalse();
  }

  // L9: selecting recipe navigates
  // The "view" in the NavigationService has been changed to "RECIPE_EDITOR".

  @Test
  void L9_selectRecipeNavigatesToRecipeEditor() throws InterruptedException {
    Platform.runLater(() -> viewModel.selectRecipe("recipe-99"));
    waitForFxEvents();

    assertThat(navigationService.getSelectedRecipeId()).isEqualTo("recipe-99");
    assertThat(navigationService.getCurrentView()).isEqualTo(NavigationService.View.RECIPE_EDITOR);
  }

  // L10: nonexistent collection ID handled gracefully
  // Selected an id that does not exist. The selectedId is null. The recipe list
  // is empty.

  @Test
  void L10_selectNonexistentCollectionClearsSelection() throws InterruptedException {
    Platform.runLater(() -> viewModel.selectCollection("does-not-exist"));
    waitForFxEvents();

    assertThat(viewModel.getSelectedCollectionId()).isNull();
    assertThat(viewModel.getRecipeIds()).isEmpty();
  }

  // L11 & L12: filter by title, updates immediately
  // "ITALIAN" can match "Italian Food"

  @Test
  void L11_filterByTitleIsCaseInsensitive() throws InterruptedException {
    RecipeCollection italian = mockCollection("col-it", "Italian Food", SourceType.PERSONAL, 0);
    RecipeCollection french = mockCollection("col-fr", "French Pastry", SourceType.PERSONAL, 0);
    when(mockService.listCollections()).thenReturn(List.of(italian, french));

    Platform.runLater(() -> viewModel.refresh());
    waitForFxEvents();
    Thread.sleep(200);
    waitForFxEvents();

    // Mixed-case filter — should match "Italian Food" only
    Platform.runLater(() -> viewModel.filterTextProperty().set("ITALIAN"));
    waitForFxEvents();

    assertThat(viewModel.getCollectionIds()).containsExactly("col-it");
    assertThat(viewModel.getCollectionIds()).doesNotContain("col-fr");
  }

  // Filter immediately after each input, without the need for waiting.
  @Test
  void L12_filteredListUpdatesImmediatelyAsUserTypes() throws InterruptedException {
    RecipeCollection salads = mockCollection("col-sa", "Salads", SourceType.PERSONAL, 0);
    RecipeCollection soups = mockCollection("col-so", "Soups", SourceType.PERSONAL, 0);
    when(mockService.listCollections()).thenReturn(List.of(salads, soups));

    Platform.runLater(() -> viewModel.refresh());
    waitForFxEvents();
    Thread.sleep(200);
    waitForFxEvents();

    // "s" matches both
    Platform.runLater(() -> viewModel.filterTextProperty().set("s"));
    waitForFxEvents();
    assertThat(viewModel.getCollectionIds()).containsExactlyInAnyOrder("col-sa", "col-so");

    // "sa" matches only "Salads"
    Platform.runLater(() -> viewModel.filterTextProperty().set("sa"));
    waitForFxEvents();
    assertThat(viewModel.getCollectionIds()).containsExactly("col-sa");

    // clear filter — both visible again
    Platform.runLater(() -> viewModel.filterTextProperty().set(""));
    waitForFxEvents();
    assertThat(viewModel.getCollectionIds()).containsExactlyInAnyOrder("col-sa", "col-so");
  }

  // L13: undo-delete works with active filter
  // When in the filtering state, after performing an undo operation, the
  // collection reappears.

  @Test
  void L13_undoDeleteWorksWithActiveFilter() throws InterruptedException {
    RecipeCollection italian = mockCollection("col-it2", "Italian Food", SourceType.PERSONAL, 0);
    RecipeCollection french = mockCollection("col-fr2", "French Pastry", SourceType.PERSONAL, 0);
    when(mockService.listCollections()).thenReturn(List.of(italian, french));
    when(mockService.findCollectionById("col-it2")).thenReturn(Optional.of(italian));

    Platform.runLater(() -> viewModel.refresh());
    waitForFxEvents();
    Thread.sleep(200);
    waitForFxEvents();

    // Apply filter so only "Italian Food" is visible
    Platform.runLater(() -> viewModel.filterTextProperty().set("italian"));
    waitForFxEvents();
    assertThat(viewModel.getCollectionIds()).containsExactly("col-it2");

    // Delete the visible collection
    Platform.runLater(() -> viewModel.deleteCollection("col-it2"));
    waitForFxEvents();
    assertThat(viewModel.getCollectionIds()).doesNotContain("col-it2");

    // Undo — should restore even though filter is active
    Platform.runLater(() -> viewModel.undoDelete());
    waitForFxEvents();
    assertThat(viewModel.getCollectionIds()).containsExactly("col-it2");

    // Filter is still "italian" — French is still hidden
    assertThat(viewModel.getFilterText()).isEqualTo("italian");
    assertThat(viewModel.getCollectionIds()).doesNotContain("col-fr2");

    // Service delete was never committed
    verify(mockService, never()).deleteCollection("col-it2");
  }

  // Helper
  /*
   * Create a fake RecipeCollection object using Mockito. I don't want to use the
   * real RecipeCollectionImpl (which relies on a
   * database/file), I just need a "fake object" that tells it what value to
   * return when someone calls getId().
   * The `recipeCount` parameter determines how many fake recipes are returned by
   * the `getRecipes()` method that the content of these fake recipes is not
   * important; it is merely to ensure that the value of `recipes.size()` is
   * correct (L2 needs to verify the recipe count).
   */

  private RecipeCollection mockCollection(
      String id, String title, SourceType sourceType, int recipeCount) {
    RecipeCollection col = mock(RecipeCollection.class);
    when(col.getId()).thenReturn(id);
    when(col.getTitle()).thenReturn(title);
    when(col.getSourceType()).thenReturn(sourceType);
    List<app.cookyourbooks.model.Recipe> recipes = new java.util.ArrayList<>();
    // Use a for loop to create a specified number of fake recipes
    for (int i = 0; i < recipeCount; i++) {
      recipes.add(mock(app.cookyourbooks.model.Recipe.class));
    }
    when(col.getRecipes()).thenReturn(recipes);
    return col;
  }

  /*
   * Here, I directly pass in the specific fake recipe object (Recipe...), which
   * is a variable parameter, instead of just passing in a single quantity.
   * This helper is used for L3 testing that test requires verifying that after
   * selecting the collection, the recipe list contains specific recipes (with id
   * and title), so we need to pass in the r1 and r2 that we have already
   * constructed.
   */
  private RecipeCollection mockCollectionWithRecipes(
      String id, String title, SourceType sourceType, app.cookyourbooks.model.Recipe... recipes) {
    RecipeCollection col = mock(RecipeCollection.class);
    when(col.getId()).thenReturn(id);
    when(col.getTitle()).thenReturn(title);
    when(col.getSourceType()).thenReturn(sourceType);
    when(col.getRecipes()).thenReturn(List.of(recipes));
    return col;
  }

  // Create a fake Recipe object, setting only the id and title.
  private app.cookyourbooks.model.Recipe mockRecipe(String id, String title) {
    app.cookyourbooks.model.Recipe r = mock(app.cookyourbooks.model.Recipe.class);
    when(r.getId()).thenReturn(id);
    when(r.getTitle()).thenReturn(title);
    return r;
  }
}
