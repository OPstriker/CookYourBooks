package app.cookyourbooks.gui.viewmodel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import app.cookyourbooks.gui.BackgroundTaskRunner;
import app.cookyourbooks.gui.NavigationService;
import app.cookyourbooks.model.Recipe;
import app.cookyourbooks.model.RecipeCollection;
import app.cookyourbooks.services.LibrarianService;

/**
 * Implementation of {@link ShoppingListResultViewModel}.
 *
 * <p>When {@link #load(Set)} is called with the set of selected recipe IDs, it runs a background
 * task (via {@link BackgroundTaskRunner}) that:
 *
 * <ol>
 *   <li>Fetches all recipes from the library via {@link LibrarianService#listAllRecipes()}
 *   <li>Filters to only the recipes whose IDs are in the provided set
 *   <li>Builds one {@link RecipeSection} per recipe, each containing one {@link IngredientItem} per
 *       ingredient (using {@code ingredient.toString()} as the display text)
 * </ol>
 *
 * <p>All updates to {@link #sectionsProperty()} and other observable properties happen on the FX
 * Application Thread, delivered by {@link BackgroundTaskRunner}'s {@code onSuccess} callback.
 */
public class ShoppingListResultViewModelImpl implements ShoppingListResultViewModel {

  private final LibrarianService librarianService;
  private final NavigationService navigationService;

  private final ObservableList<RecipeSection> sections = FXCollections.observableArrayList();
  private final BooleanProperty loading = new SimpleBooleanProperty(false);
  private final StringProperty statusMessage = new SimpleStringProperty("");

  /**
   * Constructs the ViewModel.
   *
   * @param librarianService used to fetch all recipes and filter by ID
   * @param navigationService used to navigate back to the Library
   */
  public ShoppingListResultViewModelImpl(
      LibrarianService librarianService, NavigationService navigationService) {
    this.librarianService = librarianService;
    this.navigationService = navigationService;
  }

  @Override
  public ObservableList<RecipeSection> sectionsProperty() {
    return sections;
  }

  @Override
  public BooleanProperty loadingProperty() {
    return loading;
  }

  @Override
  public StringProperty statusMessageProperty() {
    return statusMessage;
  }

  @Override
  public void load(Set<String> recipeIds) {
    sections.clear();
    statusMessage.set("");
    loading.set(true);

    BackgroundTaskRunner.run(
        () -> buildSections(recipeIds),
        result -> {
          sections.setAll(result);
          loading.set(false);
          if (result.isEmpty()) {
            statusMessage.set("No recipes found.");
          }
        },
        error -> {
          statusMessage.set("Error loading shopping list: " + error.getMessage());
          loading.set(false);
        });
  }

  /**
   * Runs on the background thread: fetches all recipes, resolves their collection names, filters by
   * ID, and builds sections.
   *
   * <p>The collection lookup iterates {@link LibrarianService#listCollections()} once and builds a
   * {@code recipeId → collectionTitle} map, avoiding repeated service calls per recipe.
   *
   * @param recipeIds the set of recipe IDs to include
   * @return one {@link RecipeSection} per matched recipe, preserving encounter order
   */
  private List<RecipeSection> buildSections(Set<String> recipeIds) {
    // Build recipeId → collectionTitle map in one pass over all collections.
    Map<String, String> recipeIdToCollection = new HashMap<>();
    for (RecipeCollection col : librarianService.listCollections()) {
      for (Recipe r : col.getRecipes()) {
        recipeIdToCollection.put(r.getId(), col.getTitle());
      }
    }

    List<RecipeSection> result = new ArrayList<>();
    for (Recipe recipe : librarianService.listAllRecipes()) {
      if (!recipeIds.contains(recipe.getId())) {
        continue;
      }
      List<IngredientItem> items = new ArrayList<>();
      for (var ingredient : recipe.getIngredients()) {
        items.add(new IngredientItem(ingredient.toString()));
      }
      String collectionName =
          recipeIdToCollection.getOrDefault(recipe.getId(), "Unknown Collection");
      result.add(new RecipeSection(recipe.getTitle(), collectionName, items));
    }
    return result;
  }

  @Override
  public void navigateBack() {
    navigationService.navigateTo(NavigationService.View.LIBRARY);
  }

  @Override
  public List<String> getSectionTitles() {
    return sections.stream().map(RecipeSection::getRecipeName).toList();
  }

  @Override
  public boolean isLoading() {
    return loading.get();
  }
}
