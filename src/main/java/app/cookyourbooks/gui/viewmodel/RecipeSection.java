package app.cookyourbooks.gui.viewmodel;

import java.util.List;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * Represents one recipe's section in the Shopping List result screen.
 *
 * <p>Groups the recipe's title (shown as a bold header) with its list of {@link IngredientItem}s
 * (shown as checkboxes). The {@link ObservableList} allows the controller to react to any future
 * dynamic additions consistent with the rest of the codebase.
 */
public final class RecipeSection {

  private final String recipeName;
  private final ObservableList<IngredientItem> ingredients;

  /**
   * Constructs a recipe section.
   *
   * @param recipeName the recipe title shown as the bold header
   * @param ingredients the list of ingredient items for this recipe
   */
  public RecipeSection(String recipeName, List<IngredientItem> ingredients) {
    this.recipeName = recipeName;
    this.ingredients = FXCollections.observableArrayList(ingredients);
  }

  /** Returns the recipe name used as the bold section header. */
  public String getRecipeName() {
    return recipeName;
  }

  /** Returns the observable list of ingredient items for this section. */
  public ObservableList<IngredientItem> getIngredients() {
    return ingredients;
  }
}
