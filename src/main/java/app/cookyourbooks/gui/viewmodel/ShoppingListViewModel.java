package app.cookyourbooks.gui.viewmodel;

import javafx.beans.property.BooleanProperty;
import javafx.collections.ObservableSet;

public interface ShoppingListViewModel {
  /** Whether selection mode is currently active. */
  BooleanProperty activeProperty();

  /** The set of recipe IDs the user has checked. */
  ObservableSet<String> selectedRecipeIds();

  void enter(); // called when 🛒 is clicked

  void discard(); // called when Discard is pressed in the dialog

  void confirm(); // called when the user confirms — hands off IDs, exits mode
}
