package app.cookyourbooks.gui.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import javafx.application.Platform;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import app.cookyourbooks.gui.NavigationService;
import app.cookyourbooks.gui.ViewModelTestBase;
import app.cookyourbooks.model.Ingredient;
import app.cookyourbooks.model.Recipe;
import app.cookyourbooks.model.RecipeCollection;
import app.cookyourbooks.services.LibrarianService;

@SuppressWarnings(
    "NullAway.Init") // vm is set in @BeforeEach via Platform.runLater, not the constructor
class ShoppingListResultViewModelImplTest extends ViewModelTestBase {

  private LibrarianService mockService;
  private NavigationService navigationService;
  private ShoppingListResultViewModelImpl vm;

  @BeforeEach
  void setUp() throws InterruptedException {
    mockService = mock(LibrarianService.class);
    navigationService = new NavigationService();

    // Default: no collections — tests that care about collection names override this.
    when(mockService.listCollections()).thenReturn(List.of());

    var latch = new java.util.concurrent.CountDownLatch(1);
    Platform.runLater(
        () -> {
          vm = new ShoppingListResultViewModelImpl(mockService, navigationService);
          latch.countDown();
        });
    latch.await();
  }

  // SL1: load() with matching IDs populates section titles in order

  @Test
  void SL1_loadWithMatchingIdsSectionTitlesPopulated() throws InterruptedException {
    Recipe pasta = mockRecipe("id-1", "Garlic Butter Pasta", List.of());
    Recipe chicken = mockRecipe("id-2", "Lemon Herb Chicken", List.of());
    when(mockService.listAllRecipes()).thenReturn(List.of(pasta, chicken));

    Platform.runLater(() -> vm.load(Set.of("id-1", "id-2")));
    waitForFxEvents();
    Thread.sleep(200);
    waitForFxEvents();

    assertThat(vm.getSectionTitles())
        .containsExactlyInAnyOrder("Garlic Butter Pasta", "Lemon Herb Chicken");
  }

  // SL2: load() with IDs that don't exist results in empty sections and a status message

  @Test
  void SL2_loadWithUnknownIdsEmptySectionsAndStatusSet() throws InterruptedException {
    Recipe pasta = mockRecipe("id-1", "Garlic Butter Pasta", List.of());
    when(mockService.listAllRecipes()).thenReturn(List.of(pasta));

    Platform.runLater(() -> vm.load(Set.of("id-999")));
    waitForFxEvents();
    Thread.sleep(200);
    waitForFxEvents();

    assertThat(vm.getSectionTitles()).isEmpty();
    assertThat(vm.statusMessageProperty().get()).isEqualTo("No recipes found.");
  }

  // SL3: each section's ingredient count matches the recipe's ingredient list size

  @Test
  void SL3_ingredientCountMatchesRecipe() throws InterruptedException {
    Ingredient i1 = mockIngredient("garlic");
    Ingredient i2 = mockIngredient("butter");
    Ingredient i3 = mockIngredient("spaghetti");
    Recipe pasta = mockRecipe("id-1", "Garlic Butter Pasta", List.of(i1, i2, i3));
    when(mockService.listAllRecipes()).thenReturn(List.of(pasta));

    Platform.runLater(() -> vm.load(Set.of("id-1")));
    waitForFxEvents();
    Thread.sleep(200);
    waitForFxEvents();

    assertThat(vm.sectionsProperty()).hasSize(1);
    assertThat(vm.sectionsProperty().get(0).getIngredients()).hasSize(3);
  }

  // SL4: every IngredientItem starts unchecked

  @Test
  void SL4_ingredientItemsStartUnchecked() throws InterruptedException {
    Ingredient i1 = mockIngredient("garlic");
    Ingredient i2 = mockIngredient("butter");
    Recipe pasta = mockRecipe("id-1", "Garlic Butter Pasta", List.of(i1, i2));
    when(mockService.listAllRecipes()).thenReturn(List.of(pasta));

    Platform.runLater(() -> vm.load(Set.of("id-1")));
    waitForFxEvents();
    Thread.sleep(200);
    waitForFxEvents();

    for (IngredientItem item : vm.sectionsProperty().get(0).getIngredients()) {
      assertThat(item.checkedProperty().get()).isFalse();
    }
  }

  // SL5: isLoading() is true immediately after load() and false after completion

  @Test
  void SL5_isLoadingTrueThenFalse() throws InterruptedException {
    when(mockService.listAllRecipes()).thenReturn(List.of());

    // Capture loading state right after scheduling load() on FX thread
    var loadingDuringTask = new boolean[1];
    var latch = new java.util.concurrent.CountDownLatch(1);
    Platform.runLater(
        () -> {
          vm.load(Set.of("id-1"));
          loadingDuringTask[0] = vm.isLoading();
          latch.countDown();
        });
    latch.await();

    assertThat(loadingDuringTask[0]).isTrue();

    Thread.sleep(200);
    waitForFxEvents();

    assertThat(vm.isLoading()).isFalse();
  }

  // SL6: navigateBack() sets navigation to LIBRARY

  @Test
  void SL6_navigateBackSetsCurrentViewToLibrary() throws InterruptedException {
    Platform.runLater(() -> vm.navigateBack());
    waitForFxEvents();

    assertThat(navigationService.getCurrentView()).isEqualTo(NavigationService.View.LIBRARY);
  }

  // SL7: calling load() a second time clears sections from the first call

  @Test
  void SL7_secondLoadClearsPreviousSections() throws InterruptedException {
    Recipe pasta = mockRecipe("id-1", "Garlic Butter Pasta", List.of());
    Recipe soup = mockRecipe("id-2", "Tomato Basil Soup", List.of());
    when(mockService.listAllRecipes()).thenReturn(List.of(pasta, soup));

    // First load
    Platform.runLater(() -> vm.load(Set.of("id-1")));
    waitForFxEvents();
    Thread.sleep(200);
    waitForFxEvents();
    assertThat(vm.getSectionTitles()).containsExactly("Garlic Butter Pasta");

    // Second load with different ID
    Platform.runLater(() -> vm.load(Set.of("id-2")));
    waitForFxEvents();
    Thread.sleep(200);
    waitForFxEvents();
    assertThat(vm.getSectionTitles()).containsExactly("Tomato Basil Soup");
  }

  // SL8: load() resolves collection name from listCollections()

  @Test
  void SL8_collectionNameResolvedFromListCollections() throws InterruptedException {
    Recipe pasta = mockRecipe("id-1", "Garlic Butter Pasta", List.of());
    RecipeCollection col = mockCollection("My Favorites", List.of(pasta));
    when(mockService.listAllRecipes()).thenReturn(List.of(pasta));
    when(mockService.listCollections()).thenReturn(List.of(col));

    Platform.runLater(() -> vm.load(Set.of("id-1")));
    waitForFxEvents();
    Thread.sleep(200);
    waitForFxEvents();

    assertThat(vm.sectionsProperty()).hasSize(1);
    assertThat(vm.sectionsProperty().get(0).getCollectionName()).isEqualTo("My Favorites");
  }

  // SL9: recipe not in any collection falls back to "Unknown Collection"

  @Test
  void SL9_recipeNotInAnyCollectionFallsBackToUnknown() throws InterruptedException {
    Recipe pasta = mockRecipe("id-1", "Garlic Butter Pasta", List.of());
    // listCollections() returns empty (set in setUp) — pasta is in no collection
    when(mockService.listAllRecipes()).thenReturn(List.of(pasta));

    Platform.runLater(() -> vm.load(Set.of("id-1")));
    waitForFxEvents();
    Thread.sleep(200);
    waitForFxEvents();

    assertThat(vm.sectionsProperty()).hasSize(1);
    assertThat(vm.sectionsProperty().get(0).getCollectionName()).isEqualTo("Unknown Collection");
  }

  // ── Helpers ──────────────────────────────────────────────────────────────

  private static Recipe mockRecipe(String id, String title, List<Ingredient> ingredients) {
    Recipe recipe = mock(Recipe.class);
    when(recipe.getId()).thenReturn(id);
    when(recipe.getTitle()).thenReturn(title);
    when(recipe.getIngredients()).thenReturn(ingredients);
    return recipe;
  }

  private static Ingredient mockIngredient(String name) {
    Ingredient ingredient = mock(Ingredient.class);
    when(ingredient.getName()).thenReturn(name);
    when(ingredient.toString()).thenReturn(name);
    return ingredient;
  }

  private static RecipeCollection mockCollection(String title, List<Recipe> recipes) {
    RecipeCollection col = mock(RecipeCollection.class);
    when(col.getTitle()).thenReturn(title);
    when(col.getRecipes()).thenReturn(recipes);
    return col;
  }
}
