package app.cookyourbooks.gui.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import app.cookyourbooks.gui.NavigationService;
import app.cookyourbooks.gui.ViewModelTestBase;
import app.cookyourbooks.model.Recipe;
import app.cookyourbooks.model.RecipeCollection;
import app.cookyourbooks.services.LibrarianService;

/**
 * Unit tests for {@link SearchViewModelImpl}, covering all S1–S11 requirements.
 *
 * <p>Uses {@code Duration.ZERO} for the debounce delay so tests do not need to wait 300 ms.
 * Background tasks are allowed to complete by calling {@link #waitForFxEvents()} (from {@link
 * ViewModelTestBase}) after triggering a search.
 */
class SearchViewModelTest extends ViewModelTestBase {

  private LibrarianService librarianService;
  private NavigationService navigationService;
  private SearchViewModelImpl vm;

  /** Convenience: build a Recipe stub with the given id and title. */
  private static Recipe recipe(String id, String title) {
    Recipe r = mock(Recipe.class);
    when(r.getId()).thenReturn(id);
    when(r.getTitle()).thenReturn(title);
    return r;
  }

  /**
   * Convenience: build a RecipeCollection stub that contains the given recipes and reports the
   * given title.
   */
  private static RecipeCollection collection(String title, Recipe... recipes) {
    RecipeCollection c = mock(RecipeCollection.class);
    when(c.getTitle()).thenReturn(title);
    when(c.getRecipes()).thenReturn(List.of(recipes));
    return c;
  }

  @BeforeEach
  void setUp() {
    librarianService = mock(LibrarianService.class);
    navigationService = new NavigationService();
    // Default: no collections — tests that need collection data override this per-test.
    when(librarianService.listCollections()).thenReturn(List.of());
    // Default: keyword ingredient search returns nothing — tests override as needed.
    when(librarianService.searchByIngredient(org.mockito.ArgumentMatchers.anyString()))
        .thenReturn(List.of());
    // Duration.ZERO → PauseTransition fires almost immediately; no real wait needed.
    vm = new SearchViewModelImpl(librarianService, navigationService, Duration.ZERO);
  }

  // ── S1: Setting the search query triggers a search and populates results ───

  @Test
  void setQuery_triggersSearchAndPopulatesResults() throws InterruptedException {
    Recipe pasta = recipe("r1", "Pasta Primavera");
    when(librarianService.resolveRecipes("pasta")).thenReturn(List.of(pasta));

    vm.setQuery("pasta");
    waitForFxEvents(); // PauseTransition fires → runSearch() → background thread starts
    Thread.sleep(100); // background task completes
    waitForFxEvents(); // onSuccess runs on FX thread

    assertThat(vm.getResultIds()).containsExactly("r1");
  }

  // ── S2: Title search returns matching recipes via resolveRecipes() ─────────

  @Test
  void titleSearch_usesResolveRecipes() throws InterruptedException {
    Recipe cookie = recipe("r2", "Chocolate Chip Cookies");
    when(librarianService.resolveRecipes("cookie")).thenReturn(List.of(cookie));

    vm.setQuery("cookie");
    waitForFxEvents();
    Thread.sleep(100);
    waitForFxEvents();

    assertThat(vm.getResultIds()).containsExactly("r2");
  }

  // ── S3: Adding an ingredient filter narrows results via searchByIngredient() ─

  @Test
  void addIngredientFilter_narrowsResults() throws InterruptedException {
    Recipe pastaWithFlour = recipe("r1", "Pasta");
    Recipe soupNoFlour = recipe("r2", "Tomato Soup");
    when(librarianService.listAllRecipes()).thenReturn(List.of(pastaWithFlour, soupNoFlour));
    when(librarianService.searchByIngredient("flour")).thenReturn(List.of(pastaWithFlour));

    vm.addIngredientFilter("flour");
    Thread.sleep(100);
    waitForFxEvents();

    assertThat(vm.getResultIds()).containsExactly("r1");
  }

  // ── S4: Multiple ingredient filters use AND logic (intersection) ───────────

  @Test
  void multipleIngredientFilters_useAndLogic() throws InterruptedException {
    Recipe both = recipe("r1", "Both-ingredient dish");
    Recipe onlyEgg = recipe("r2", "Only egg dish");
    Recipe onlyFlour = recipe("r3", "Only flour dish");
    when(librarianService.listAllRecipes()).thenReturn(List.of(both, onlyEgg, onlyFlour));
    when(librarianService.searchByIngredient("egg")).thenReturn(List.of(both, onlyEgg));
    when(librarianService.searchByIngredient("flour")).thenReturn(List.of(both, onlyFlour));

    vm.addIngredientFilter("egg");
    Thread.sleep(100);
    waitForFxEvents();

    vm.addIngredientFilter("flour");
    Thread.sleep(100);
    waitForFxEvents();

    // Only "both" has both egg AND flour
    assertThat(vm.getResultIds()).containsExactly("r1");
  }

  // ── S5: Clearing filters/query resets results ──────────────────────────────

  @Test
  void clearFilters_resetsResults() throws InterruptedException {
    Recipe all1 = recipe("r1", "Recipe 1");
    Recipe all2 = recipe("r2", "Recipe 2");
    when(librarianService.listAllRecipes()).thenReturn(List.of(all1, all2));
    when(librarianService.searchByIngredient("egg")).thenReturn(List.of(all1));

    vm.addIngredientFilter("egg");
    Thread.sleep(100);
    waitForFxEvents();

    // Confirm filtering is active
    assertThat(vm.getResultIds()).containsExactly("r1");

    // Clear resets to all recipes
    vm.clearFilters();
    Thread.sleep(100);
    waitForFxEvents();

    assertThat(vm.getResultIds()).containsExactlyInAnyOrder("r1", "r2");
    assertThat(vm.getIngredientFilters()).isEmpty();
    assertThat(vm.getQuery()).isEmpty();
  }

  // ── S6: Search runs on a background thread; isSearching is true while running ─

  @Test
  void search_isSearchingTrue_whileRunning() throws InterruptedException {
    // Use a slow mock to keep the background task alive long enough to observe isSearching=true.
    when(librarianService.resolveRecipes("slow"))
        .thenAnswer(
            inv -> {
              Thread.sleep(200);
              return List.of();
            });

    vm.setQuery("slow");
    waitForFxEvents(); // PauseTransition fires, runSearch() called, task starts, isSearching=true

    assertThat(vm.isSearching()).isTrue();

    Thread.sleep(300); // task finishes
    waitForFxEvents();

    assertThat(vm.isSearching()).isFalse();
  }

  // ── S7: Search is debounced (300 ms delay after last keystroke before firing) ─

  @Test
  void setQuery_debounce_doesNotFireImmediately() throws InterruptedException {
    // Use a non-zero debounce delay for this specific test.
    SearchViewModelImpl vmWithDelay =
        new SearchViewModelImpl(librarianService, navigationService, Duration.ofMillis(150));
    when(librarianService.resolveRecipes("x")).thenReturn(List.of());

    vmWithDelay.setQuery("x");
    // Do NOT wait; check that results have not yet been populated
    waitForFxEvents(); // only flushes the FX queue, does NOT wait 150 ms

    // The debounce timer hasn't fired yet, so no search has been triggered.
    assertThat(vmWithDelay.getResultIds()).isEmpty();
    assertThat(vmWithDelay.isSearching()).isFalse();

    // Wait for debounce + task
    Thread.sleep(300);
    waitForFxEvents();

    // Now the search has completed (empty list is fine — we just verify it ran)
    assertThat(vmWithDelay.isSearching()).isFalse();
  }

  // ── S8: selectNextResult / selectPreviousResult cycle through results ───────

  @Test
  void selectNextResult_selectPreviousResult_cycleResults() throws InterruptedException {
    Recipe r1 = recipe("r1", "Alpha");
    Recipe r2 = recipe("r2", "Beta");
    Recipe r3 = recipe("r3", "Gamma");
    when(librarianService.resolveRecipes("a")).thenReturn(List.of(r1, r2, r3));

    vm.setQuery("a");
    Thread.sleep(100);
    waitForFxEvents();

    assertThat(vm.getSelectedResultId()).isNull(); // nothing selected yet

    vm.selectNextResult();
    assertThat(vm.getSelectedResultId()).isEqualTo("r1");

    vm.selectNextResult();
    assertThat(vm.getSelectedResultId()).isEqualTo("r2");

    vm.selectNextResult();
    assertThat(vm.getSelectedResultId()).isEqualTo("r3");

    // Wraps around
    vm.selectNextResult();
    assertThat(vm.getSelectedResultId()).isEqualTo("r1");

    // Backward
    vm.selectPreviousResult();
    assertThat(vm.getSelectedResultId()).isEqualTo("r3");
  }

  // ── S9: navigateToSelectedResult provides the selected recipe ID ───────────

  @Test
  void navigateToSelectedResult_callsNavigationService() throws InterruptedException {
    Recipe target = recipe("r1", "Target Recipe");
    when(librarianService.resolveRecipes("target")).thenReturn(List.of(target));

    vm.setQuery("target");
    Thread.sleep(100);
    waitForFxEvents();

    vm.selectNextResult(); // select r1
    vm.navigateToSelectedResult();

    // NavigationService should now point to the recipe editor with r1's id.
    assertThat(navigationService.getSelectedRecipeId()).isEqualTo("r1");
    assertThat(navigationService.getCurrentView()).isEqualTo(NavigationService.View.RECIPE_EDITOR);
  }

  // ── Click navigation: navigateToRecipe() opens the Recipe Editor directly ──

  @Test
  void navigateToRecipe_opensRecipeEditor() {
    // Simulates the user clicking a result in the ListView — the View calls
    // navigateToRecipe(id) with the clicked item's ID directly, bypassing the
    // keyboard-navigation selectedIndex.
    vm.navigateToRecipe("r42");

    assertThat(navigationService.getSelectedRecipeId()).isEqualTo("r42");
    assertThat(navigationService.getCurrentView()).isEqualTo(NavigationService.View.RECIPE_EDITOR);
  }

  // ── S10: Status message reflects result count ──────────────────────────────

  @Test
  void statusMessage_reflectsResultCount() throws InterruptedException {
    Recipe r1 = recipe("r1", "One");
    Recipe r2 = recipe("r2", "Two");
    when(librarianService.resolveRecipes("two")).thenReturn(List.of(r1, r2));
    when(librarianService.resolveRecipes("none")).thenReturn(List.of());
    when(librarianService.resolveRecipes("one")).thenReturn(List.of(r1));

    // Two results
    vm.setQuery("two");
    Thread.sleep(100);
    waitForFxEvents();
    assertThat(vm.getStatusMessage()).isEqualTo("2 results");

    // No results
    vm.setQuery("none");
    Thread.sleep(100);
    waitForFxEvents();
    assertThat(vm.getStatusMessage()).isEqualTo("No results found");

    // One result (singular)
    vm.setQuery("one");
    Thread.sleep(100);
    waitForFxEvents();
    assertThat(vm.getStatusMessage()).isEqualTo("1 result");
  }

  // ── S11: Empty query with no filters returns all recipes ──────────────────

  @Test
  void emptyQueryNoFilters_returnsAllRecipes() throws InterruptedException {
    Recipe r1 = recipe("r1", "Apple Pie");
    Recipe r2 = recipe("r2", "Banana Bread");
    when(librarianService.listAllRecipes()).thenReturn(List.of(r1, r2));

    vm.clearFilters(); // empty query + no filters → listAllRecipes()
    Thread.sleep(100);
    waitForFxEvents();

    assertThat(vm.getResultIds()).containsExactlyInAnyOrder("r1", "r2");
  }

  // ── Collection context: RecipeEntry carries the owning collection title ────

  @Test
  void searchResults_includeCollectionTitle() throws InterruptedException {
    Recipe pasta = recipe("r1", "Pasta Primavera");
    Recipe soup = recipe("r2", "Tomato Soup");
    RecipeCollection italian = collection("Italian Classics", pasta);
    RecipeCollection soups = collection("Soups & Stews", soup);

    when(librarianService.resolveRecipes("pasta")).thenReturn(List.of(pasta));
    when(librarianService.listCollections()).thenReturn(List.of(italian, soups));

    vm.setQuery("pasta");
    waitForFxEvents();
    Thread.sleep(100);
    waitForFxEvents();

    assertThat(vm.getResultIds()).containsExactly("r1");
    // The result entry should carry the collection title from the library.
    SearchViewModelImpl.RecipeEntry entry = vm.resultsProperty().get(0);
    assertThat(entry.collectionTitle()).isEqualTo("Italian Classics");
  }

  @Test
  void searchResults_collectionTitleEmptyWhenRecipeNotInAnyCollection()
      throws InterruptedException {
    Recipe orphan = recipe("r99", "Mystery Dish");
    // Collection exists but does NOT contain r99.
    RecipeCollection empty = collection("Empty Collection");
    when(librarianService.resolveRecipes("mystery")).thenReturn(List.of(orphan));
    when(librarianService.listCollections()).thenReturn(List.of(empty));

    vm.setQuery("mystery");
    waitForFxEvents();
    Thread.sleep(100);
    waitForFxEvents();

    SearchViewModelImpl.RecipeEntry entry = vm.resultsProperty().get(0);
    assertThat(entry.collectionTitle()).isEmpty();
  }

  // ── Keyword search: query matches by ingredient as well as title ───────────

  @Test
  void keywordSearch_findsRecipesByIngredientWhenNotInTitle() throws InterruptedException {
    // "Pasta Bake" does not have "chicken" in its title, but has chicken as an ingredient.
    Recipe pastaBake = recipe("r1", "Pasta Bake");
    // "Chicken Soup" matches by title.
    Recipe chickenSoup = recipe("r2", "Chicken Soup");

    when(librarianService.resolveRecipes("chicken")).thenReturn(List.of(chickenSoup));
    when(librarianService.searchByIngredient("chicken"))
        .thenReturn(List.of(pastaBake, chickenSoup));

    vm.setQuery("chicken");
    waitForFxEvents();
    Thread.sleep(100);
    waitForFxEvents();

    // Both recipes appear — title match first, then ingredient-only match appended.
    assertThat(vm.getResultIds()).containsExactly("r2", "r1");
  }

  @Test
  void keywordSearch_deduplicatesRecipesThatMatchBothTitleAndIngredient()
      throws InterruptedException {
    // "Chicken Stir Fry" appears in both title and ingredient results — should appear only once.
    Recipe stirFry = recipe("r1", "Chicken Stir Fry");

    when(librarianService.resolveRecipes("chicken")).thenReturn(List.of(stirFry));
    when(librarianService.searchByIngredient("chicken")).thenReturn(List.of(stirFry));

    vm.setQuery("chicken");
    waitForFxEvents();
    Thread.sleep(100);
    waitForFxEvents();

    assertThat(vm.getResultIds()).containsExactly("r1");
  }
}
