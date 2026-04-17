package app.cookyourbooks.gui.viewmodel;

import java.util.List;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * Represents one recipe's section in the Shopping List result screen.
 *
 * <p>Groups the recipe's title (shown as a bold header), the name of the collection it belongs to
 * (shown as a subtitle), and the list of {@link IngredientItem}s (shown as checkboxes).
 *
 * <p>The {@link #dismissedProperty()} tracks whether the user has clicked the recipe header to
 * cross off the entire section. When dismissed, the header, subtitle, and all ingredient rows are
 * grayed out with strikethrough — matching the pattern of {@link IngredientItem#checkedProperty()}
 * but at the section level.
 */
public final class RecipeSection {

  private final String recipeName;
  private final String collectionName;
  private final ObservableList<IngredientItem> ingredients;
  private final BooleanProperty dismissed = new SimpleBooleanProperty(false);

  /**
   * Constructs a recipe section.
   *
   * @param recipeName the recipe title shown as the bold section header
   * @param collectionName the name of the collection this recipe belongs to
   * @param ingredients the list of ingredient items for this recipe
   */
  public RecipeSection(String recipeName, String collectionName, List<IngredientItem> ingredients) {
    this.recipeName = recipeName;
    this.collectionName = collectionName;
    this.ingredients = FXCollections.observableArrayList(ingredients);
  }

  /** Returns the recipe name used as the bold section header. */
  public String getRecipeName() {
    return recipeName;
  }

  /** Returns the name of the collection this recipe belongs to. */
  public String getCollectionName() {
    return collectionName;
  }

  /** Returns the observable list of ingredient items for this section. */
  public ObservableList<IngredientItem> getIngredients() {
    return ingredients;
  }

  /**
   * Returns the property tracking whether this section has been dismissed by clicking the recipe
   * header. When {@code true}, the entire section (header, subtitle, and all ingredients) is
   * displayed with strikethrough and gray styling.
   */
  public BooleanProperty dismissedProperty() {
    return dismissed;
  }
}
