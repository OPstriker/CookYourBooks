package app.cookyourbooks.gui.viewmodel;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import javafx.animation.PauseTransition;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import org.jspecify.annotations.Nullable;

import app.cookyourbooks.gui.BackgroundTaskRunner;
import app.cookyourbooks.gui.NavigationService;
import app.cookyourbooks.model.Recipe;
import app.cookyourbooks.model.RecipeCollection;
import app.cookyourbooks.services.LibrarianService;

/**
 * Implementation of {@link SearchViewModel} for the Search &amp; Filter feature.
 *
 * <p>Supports debounced title search, AND-logic ingredient filtering, background thread execution,
 * race-condition protection via a generation counter, and keyboard navigation through results.
 */
public class SearchViewModelImpl implements SearchViewModel {

  /**
   * A single search result entry exposing recipe ID, title, and the collection it belongs to.
   *
   * <p>Public so the View (SearchViewController) can type its ListView without unchecked casts.
   * {@code collectionTitle} is the title of the owning collection, or {@code ""} if the recipe does
   * not belong to any collection in the library.
   */
  public record RecipeEntry(String id, String title, String collectionTitle) {}

  private final LibrarianService librarianService;
  private final NavigationService navigationService;

  private final StringProperty query = new SimpleStringProperty("");
  private final ObservableList<RecipeEntry> results = FXCollections.observableArrayList();
  private final ObservableList<String> ingredientFilters = FXCollections.observableArrayList();
  private final BooleanProperty searching = new SimpleBooleanProperty(false);
  private final StringProperty statusMessage = new SimpleStringProperty("");

  // Tracks which item is selected for keyboard navigation (-1 = none).
  private int selectedIndex = -1;

  // Incremented each time a new search starts; stale callbacks are discarded when they don't match.
  private final AtomicInteger generation = new AtomicInteger(0);

  // Fires runSearch() after the debounce delay elapses without a new setQuery() call.
  private final PauseTransition debounceTimer;

  /**
   * Constructs the ViewModel with injectable services and a configurable debounce delay.
   *
   * @param librarianService provides recipe search and list operations
   * @param navigationService used to navigate to a recipe when the user confirms a selection
   * @param debounceDelay delay after the last keystroke before the search fires; pass {@code
   *     Duration.ofMillis(300)} in production and {@code Duration.ofMillis(0)} in tests
   */
  public SearchViewModelImpl(
      LibrarianService librarianService,
      NavigationService navigationService,
      Duration debounceDelay) {
    this.librarianService = librarianService;
    this.navigationService = navigationService;
    this.debounceTimer = new PauseTransition(javafx.util.Duration.millis(debounceDelay.toMillis()));
    debounceTimer.setOnFinished(e -> runSearch());
  }

  // ── Observable properties ──────────────────────────────────────────────────

  @Override
  public StringProperty queryProperty() {
    return query;
  }

  @Override
  public ObservableList<RecipeEntry> resultsProperty() {
    return results;
  }

  @Override
  public ObservableList<String> ingredientFiltersProperty() {
    return ingredientFilters;
  }

  @Override
  public BooleanProperty searchingProperty() {
    return searching;
  }

  @Override
  public StringProperty statusMessageProperty() {
    return statusMessage;
  }

  // ── Commands ───────────────────────────────────────────────────────────────

  @Override
  public void setQuery(String q) {
    query.set(q);
    debounceTimer.playFromStart();
  }

  @Override
  public void addIngredientFilter(String ingredient) {
    String trimmed = ingredient.trim();
    if (!trimmed.isBlank() && !ingredientFilters.contains(trimmed)) {
      ingredientFilters.add(trimmed);
      runSearch();
    }
  }

  @Override
  public void removeIngredientFilter(String ingredient) {
    ingredientFilters.remove(ingredient);
    runSearch();
  }

  @Override
  public void clearFilters() {
    debounceTimer.stop();
    ingredientFilters.clear();
    query.set("");
    runSearch();
  }

  @Override
  public void selectNextResult() {
    if (results.isEmpty()) {
      return;
    }
    selectedIndex = (selectedIndex + 1) % results.size();
  }

  @Override
  public void selectPreviousResult() {
    if (results.isEmpty()) {
      return;
    }
    selectedIndex = (selectedIndex - 1 + results.size()) % results.size();
  }

  @Override
  public void navigateToSelectedResult() {
    String id = getSelectedResultId();
    if (id != null) {
      navigationService.navigateToRecipe(id);
    }
  }

  @Override
  public void navigateToRecipe(String recipeId) {
    navigationService.navigateToRecipe(recipeId);
  }

  @Override
  public void navigateToLibrary() {
    navigationService.navigateTo(NavigationService.View.LIBRARY);
  }

  // ── Non-JavaFX accessors (for grading tests) ──────────────────────────────

  @Override
  public String getQuery() {
    return query.get();
  }

  @Override
  public List<String> getResultIds() {
    return results.stream().map(RecipeEntry::id).toList();
  }

  @Override
  public List<String> getIngredientFilters() {
    return List.copyOf(ingredientFilters);
  }

  @Override
  public boolean isSearching() {
    return searching.get();
  }

  @Override
  public String getStatusMessage() {
    return statusMessage.get();
  }

  @Override
  public @Nullable String getSelectedResultId() {
    if (selectedIndex < 0 || selectedIndex >= results.size()) {
      return null;
    }
    return results.get(selectedIndex).id();
  }

  // ── Internal ──────────────────────────────────────────────────────────────

  /**
   * Executes a search on a background thread using the current query and ingredient filters.
   *
   * <p>Increments the generation counter before starting so that any result from a previous
   * (now-stale) search is silently discarded in the success/failure callbacks.
   */
  private void runSearch() {
    int myGen = generation.incrementAndGet();
    searching.set(true);
    statusMessage.set("Searching...");

    // Capture current state so background thread reads consistent values.
    String currentQuery = query.get().trim();
    List<String> currentFilters = List.copyOf(ingredientFilters);

    BackgroundTaskRunner.run(
        () -> computeResults(currentQuery, currentFilters),
        entries -> {
          if (generation.get() != myGen) { // discard stale result
            return;
          }
          results.setAll(entries);
          selectedIndex = -1;
          int count = results.size();
          statusMessage.set(
              count == 0 ? "No results found" : count + (count == 1 ? " result" : " results"));
          searching.set(false);
        },
        error -> {
          if (generation.get() != myGen) {
            return;
          }
          statusMessage.set("Search failed: " + error.getMessage());
          searching.set(false);
        });
  }

  /**
   * Performs the actual search computation on the background thread.
   *
   * <p>Strategy:
   *
   * <ol>
   *   <li>Build a recipeId → collectionTitle map from all library collections.
   *   <li>Build the base set: all recipes (S11 empty-query case), or a keyword-union of
   *       title-matched recipes ({@code resolveRecipes}) and ingredient-matched recipes ({@code
   *       searchByIngredient}), deduplicated by ID and ordered title-matches first.
   *   <li>For each ingredient filter, collect the matching recipe IDs.
   *   <li>Retain only recipes whose ID appears in <em>all</em> ingredient filter result sets (AND
   *       logic, S4).
   *   <li>Map each surviving recipe to a {@link RecipeEntry} that carries its collection title.
   * </ol>
   */
  private List<RecipeEntry> computeResults(String currentQuery, List<String> currentFilters) {
    // Build recipeId → collectionTitle lookup on the background thread (safe: read-only).
    Map<String, String> collectionMap = new HashMap<>();
    for (RecipeCollection collection : librarianService.listCollections()) {
      for (Recipe recipe : collection.getRecipes()) {
        collectionMap.put(recipe.getId(), collection.getTitle());
      }
    }

    List<Recipe> base;
    if (currentQuery.isEmpty()) {
      // S11: empty query → start from all recipes so ingredient filters still work.
      base = librarianService.listAllRecipes();
    } else {
      // Keyword search: union title matches and ingredient matches, title-first, deduped by ID.
      // e.g. "chicken" finds "Chicken Soup" (title) AND "Pasta" that uses chicken (ingredient).
      List<Recipe> byTitle = librarianService.resolveRecipes(currentQuery);
      List<Recipe> byIngredient = librarianService.searchByIngredient(currentQuery);
      Set<String> seen = new HashSet<>();
      List<Recipe> union = new ArrayList<>();
      for (Recipe r : byTitle) {
        if (seen.add(r.getId())) {
          union.add(r);
        }
      }
      for (Recipe r : byIngredient) {
        if (seen.add(r.getId())) {
          union.add(r);
        }
      }
      base = union;
    }

    List<Recipe> filtered;
    if (currentFilters.isEmpty()) {
      filtered = base;
    } else {
      // Build one Set<recipeId> per ingredient filter, then intersect.
      List<Set<String>> filterSets = new ArrayList<>();
      for (String filter : currentFilters) {
        Set<String> ids = new HashSet<>();
        librarianService.searchByIngredient(filter).forEach(r -> ids.add(r.getId()));
        filterSets.add(ids);
      }
      filtered =
          base.stream()
              .filter(r -> filterSets.stream().allMatch(set -> set.contains(r.getId())))
              .toList();
    }

    return filtered.stream()
        .map(
            r ->
                new RecipeEntry(r.getId(), r.getTitle(), collectionMap.getOrDefault(r.getId(), "")))
        .toList();
  }
}
