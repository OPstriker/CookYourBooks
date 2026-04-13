package app.cookyourbooks.gui.viewmodel;

import java.util.List;
import java.util.Set;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ObservableList;

/**
 * ViewModel contract for the Shopping List result screen.
 *
 * <p>Exposes the list of recipe sections (each with a title and checkable ingredient rows),
 * loading state, and navigation back to the Library.
 *
 * <p>The {@link #load(Set)} method is called by {@code CookYourBooksGuiApp}'s {@code onConfirm}
 * callback with the set of recipe IDs the user selected. It triggers a background fetch that
 * resolves IDs to {@code Recipe} objects and populates {@link #sectionsProperty()}.
 */
public interface ShoppingListResultViewModel {

  /** The list of recipe sections to display. Populated asynchronously by {@link #load(Set)}. */
  ObservableList<RecipeSection> sectionsProperty();

  /** True while the background fetch triggered by {@link #load(Set)} is running. */
  BooleanProperty loadingProperty();

  /** Empty normally; set to an error or "no results" message when appropriate. */
  StringProperty statusMessageProperty();

  /**
   * Resolves the given recipe IDs to recipes and populates {@link #sectionsProperty()}.
   *
   * <p>Runs the lookup on a background thread; updates observable state on the FX thread when
   * complete.
   *
   * @param recipeIds the IDs of the recipes selected by the user
   */
  void load(Set<String> recipeIds);

  /** Navigates back to the Library view. */
  void navigateBack();

  // ── Test accessors (no JavaFX binding required in unit tests) ──────────────

  /** Returns the recipe titles of all sections, in order. */
  List<String> getSectionTitles();

  /** Returns true while the background load is running. */
  boolean isLoading();
}
