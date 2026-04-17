package app.cookyourbooks.gui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javafx.application.Platform;

import org.junit.jupiter.api.Test;

import app.cookyourbooks.gui.viewmodel.ShoppingListResultViewModelImpl;
import app.cookyourbooks.gui.viewmodel.ShoppingListViewModelImpl;
import app.cookyourbooks.model.Ingredient;
import app.cookyourbooks.model.Recipe;
import app.cookyourbooks.services.LibrarianService;

/**
 * Integration tests for the shopping list callback chain.
 *
 * <p>These tests verify that the full wiring between {@link ShoppingListViewModelImpl} (recipe
 * selection) and {@link ShoppingListResultViewModelImpl} (result display) works correctly without
 * requiring a visible UI stage — the same lightweight approach used by {@code
 * LibraryViewIntegration Test}.
 *
 * <p>IT-SL1: confirming a selection populates sections and navigates to SHOPPING_LIST.<br>
 * IT-SL2: pressing the cart button when a list exists navigates directly to SHOPPING_LIST.<br>
 * IT-SL3: pressing the cart button when no list exists stays in selection mode (LIBRARY).
 */
class ShoppingListResultIntegrationTest extends ViewModelTestBase {

  // ── Helpers ──────────────────────────────────────────────────────────────

  private static Recipe mockRecipe(String id, String title) {
    Recipe recipe = mock(Recipe.class);
    when(recipe.getId()).thenReturn(id);
    when(recipe.getTitle()).thenReturn(title);
    Ingredient ingredient = mock(Ingredient.class);
    when(ingredient.toString()).thenReturn("some ingredient");
    when(recipe.getIngredients()).thenReturn(List.of(ingredient));
    return recipe;
  }

  /**
   * Builds the wired pair of ViewModels that mirrors what {@code CookYourBooksGuiApp} does:
   * resultVm is created first so the lambda can capture it, then shoppingListVm uses the real
   * onConfirm callback.
   */
  private record Wiring(
      ShoppingListResultViewModelImpl resultVm,
      ShoppingListViewModelImpl shoppingListVm,
      NavigationService navigationService) {}

  private static Wiring buildWiring(LibrarianService service) {
    var nav = new NavigationService();
    var resultVm = new ShoppingListResultViewModelImpl(service, nav);
    var shoppingListVm =
        new ShoppingListViewModelImpl(
            selectedIds -> {
              resultVm.load(new HashSet<>(selectedIds));
              nav.navigateTo(NavigationService.View.SHOPPING_LIST);
            });
    return new Wiring(resultVm, shoppingListVm, nav);
  }

  // ── IT-SL1: confirm populates sections and navigates to SHOPPING_LIST ─────

  @Test
  void ITSL1_confirmPopulatesSectionsAndNavigatesToShoppingList() throws InterruptedException {
    LibrarianService service = mock(LibrarianService.class);
    Recipe pasta = mockRecipe("id-1", "Garlic Butter Pasta");
    Recipe chicken = mockRecipe("id-2", "Lemon Herb Chicken");
    when(service.listAllRecipes()).thenReturn(List.of(pasta, chicken));

    Wiring w = buildWiring(service);

    // Simulate user entering selection mode, checking two recipes, and confirming
    Platform.runLater(
        () -> {
          w.shoppingListVm().enter();
          w.shoppingListVm().selectedRecipeIds().addAll(Set.of("id-1", "id-2"));
          w.shoppingListVm().confirm();
        });
    waitForFxEvents();
    Thread.sleep(300); // wait for background fetch
    waitForFxEvents();

    assertThat(w.resultVm().getSectionTitles())
        .containsExactlyInAnyOrder("Garlic Butter Pasta", "Lemon Herb Chicken");
    assertThat(w.navigationService().getCurrentView())
        .isEqualTo(NavigationService.View.SHOPPING_LIST);
  }

  // ── IT-SL2: cart button goes to existing list when sections are non-empty ─

  @Test
  void ITSL2_cartButtonNavigatesToExistingListWhenSectionsNonEmpty() throws InterruptedException {
    LibrarianService service = mock(LibrarianService.class);
    Recipe pasta = mockRecipe("id-1", "Garlic Butter Pasta");
    when(service.listAllRecipes()).thenReturn(List.of(pasta));

    Wiring w = buildWiring(service);

    // Load a list first
    Platform.runLater(
        () -> {
          w.shoppingListVm().enter();
          w.shoppingListVm().selectedRecipeIds().add("id-1");
          w.shoppingListVm().confirm();
        });
    waitForFxEvents();
    Thread.sleep(300);
    waitForFxEvents();

    // Simulate the cart button logic: sections non-empty → go to SHOPPING_LIST
    Platform.runLater(
        () -> {
          if (!w.resultVm().sectionsProperty().isEmpty()) {
            w.navigationService().navigateTo(NavigationService.View.SHOPPING_LIST);
          } else {
            w.shoppingListVm().enter();
            w.navigationService().navigateTo(NavigationService.View.LIBRARY);
          }
        });
    waitForFxEvents();

    assertThat(w.navigationService().getCurrentView())
        .isEqualTo(NavigationService.View.SHOPPING_LIST);
  }

  // ── IT-SL3: cart button enters selection mode when no list exists ──────────

  @Test
  void ITSL3_cartButtonEntersSelectionModeWhenNoListExists() throws InterruptedException {
    LibrarianService service = mock(LibrarianService.class);
    when(service.listAllRecipes()).thenReturn(List.of());

    Wiring w = buildWiring(service);

    // Simulate the cart button logic: sections empty → enter selection mode
    Platform.runLater(
        () -> {
          if (!w.resultVm().sectionsProperty().isEmpty()) {
            w.navigationService().navigateTo(NavigationService.View.SHOPPING_LIST);
          } else {
            w.shoppingListVm().enter();
            w.navigationService().navigateTo(NavigationService.View.LIBRARY);
          }
        });
    waitForFxEvents();

    assertThat(w.navigationService().getCurrentView()).isEqualTo(NavigationService.View.LIBRARY);
    assertThat(w.shoppingListVm().activeProperty().get()).isTrue();
  }
}
