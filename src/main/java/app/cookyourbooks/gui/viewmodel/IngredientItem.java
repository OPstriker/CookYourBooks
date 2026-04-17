package app.cookyourbooks.gui.viewmodel;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

/**
 * Represents a single ingredient row in the Shopping List result screen.
 *
 * <p>Pairs a human-readable display string (e.g. "2 cups flour, sifted") with a {@link
 * BooleanProperty} that tracks whether the user has checked this item off their list.
 *
 * <p>This is intentionally a plain class rather than a record because {@link BooleanProperty} is
 * mutable observable state — records are unsuitable for mutable fields.
 */
public final class IngredientItem {

  private final String displayText;
  private final BooleanProperty checked = new SimpleBooleanProperty(false);

  /**
   * Constructs an ingredient item.
   *
   * @param displayText the text shown next to the checkbox (e.g. from {@code
   *     Ingredient.toString()})
   */
  public IngredientItem(String displayText) {
    this.displayText = displayText;
  }

  /** Returns the display text shown next to the checkbox. */
  public String getDisplayText() {
    return displayText;
  }

  /** Returns the property tracking whether this item has been checked off. */
  public BooleanProperty checkedProperty() {
    return checked;
  }
}
