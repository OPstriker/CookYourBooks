package app.cookyourbooks.gui.viewmodel;

import java.util.function.Consumer;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;

import org.jspecify.annotations.Nullable;

public class ShoppingListViewModelImpl implements ShoppingListViewModel {

  private final BooleanProperty active = new SimpleBooleanProperty(false);
  private final ObservableSet<String> selectedRecipeIds = FXCollections.observableSet();

  /**
   * Called when the user confirms. Your partner's screen wiring goes here. Pass null for now;
   * CookYourBooksGuiApp will inject the real callback later.
   */
  @Nullable private Consumer<ObservableSet<String>> onConfirm;

  public ShoppingListViewModelImpl(@Nullable Consumer<ObservableSet<String>> onConfirm) {
    this.onConfirm = onConfirm;
  }

  @Override
  public BooleanProperty activeProperty() {
    return active;
  }

  @Override
  public ObservableSet<String> selectedRecipeIds() {
    return selectedRecipeIds;
  }

  @Override
  public void enter() {
    selectedRecipeIds.clear();
    active.set(true);
  }

  @Override
  public void discard() {
    selectedRecipeIds.clear();
    active.set(false);
  }

  @Override
  public void confirm() {
    if (onConfirm != null) {
      onConfirm.accept(selectedRecipeIds);
    }
    active.set(false);
  }
}
