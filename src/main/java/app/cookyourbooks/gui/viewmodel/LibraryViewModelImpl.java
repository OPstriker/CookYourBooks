package app.cookyourbooks.gui.viewmodel;

import java.time.Duration;
import java.util.List;

import javafx.animation.PauseTransition;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;

import org.jspecify.annotations.Nullable;

import app.cookyourbooks.gui.BackgroundTaskRunner;
import app.cookyourbooks.gui.NavigationService;
import app.cookyourbooks.model.RecipeCollection;
import app.cookyourbooks.services.LibrarianService;

/** Implementation of {@link LibraryViewModel}. */
public class LibraryViewModelImpl implements LibraryViewModel {

  private final LibrarianService librarianService;
  private final NavigationService navigationService;
  private final Duration undoTimeout;

  // Backing data

  /** Full (unfiltered) collection list — source of truth */
  private final ObservableList<RecipeCollectionSummary> allCollections =
      FXCollections.observableArrayList();

  /** Filtered view of allCollections driven by filterText. */
  private final FilteredList<RecipeCollectionSummary> filteredCollections =
      new FilteredList<>(allCollections);

  /** List of recipes in the currently selected collection. */
  private final ObservableList<RecipeSummary> recipes = FXCollections.observableArrayList();

  // Properties

  private final StringProperty filterText = new SimpleStringProperty("");
  private final BooleanProperty loading = new SimpleBooleanProperty(false);
  private final BooleanProperty undoAvailable = new SimpleBooleanProperty(false);
  private final StringProperty undoMessage = new SimpleStringProperty("");

  // Undo-delete state

  /**
   * The "to be deleted" collection is stored here and will wait for the undo window to close after
   * 5 seconds.
   */
  @Nullable private RecipeCollection pendingDeleteCollection = null;

  @Nullable private RecipeSummary pendingDeleteRecipe = null;

  @Nullable private PauseTransition undoTimer = null;

  // Selection state

  /** The timer in JavaFX automatically triggers the actual deletion after 5 seconds. */
  @Nullable private String selectedCollectionId = null;

  // Constructor and initialization

  public LibraryViewModelImpl(
      LibrarianService
          librarianService, // L1:Provide the operations of adding, deleting and querying for the
      // collection.
      NavigationService navigationService, // L9: Used to jump to the Recipe Editor page
      Duration
          undoTimeout) { // L10: Undo window duration (5 seconds for production, 200 milliseconds
    // for
    // testing)
    this.librarianService = librarianService;
    this.navigationService = navigationService;
    this.undoTimeout = undoTimeout;

    // Bind filter predicate to filterText property
    // **Whenever the filterText changes, update the filtering conditions of
    // filteredCollections.*/
    filterText.addListener(
        (obs, oldVal, newVal) -> {
          String lower = newVal == null ? "" : newVal.toLowerCase();
          if (lower.isBlank()) {
            filteredCollections.setPredicate(null);
          } else {
            filteredCollections.setPredicate(c -> c.title().toLowerCase().contains(lower));
          }
        });
  }

  // Observable properties

  @Override
  public ObservableList<RecipeCollectionSummary> collectionsProperty() {
    return filteredCollections; // The filtered collection list
  }

  @Override
  public StringProperty filterTextProperty() {
    return filterText; // Filter the value of the text input box
  }

  @Override
  public ObservableList<RecipeSummary> recipesProperty() {
    return recipes; // The list of recipes currently selected for the collection
  }

  @Override
  public BooleanProperty loadingProperty() {
    return loading; // Is that loading
  }

  @Override
  public BooleanProperty undoAvailableProperty() {
    return undoAvailable; // is the undo button available to use
  }

  @Override
  public StringProperty undoMessageProperty() {
    return undoMessage; // The message to show in the undo bar e.g. "Deleted: My Collection"
  }

  // Commands

  /*
   * L1: Load all collections from the service and populate the list.
   * L8: The listCollections() function runs in a background thread.
   * During this process, loading is set to true, and it is reset to false upon
   * completion.
   * The loaded data is converted into RecipeCollectionSummary and stored in
   * allCollections.
   */

  @Override
  public void refresh() {
    loading.set(true);
    BackgroundTaskRunner.run(
        librarianService::listCollections, // Execution of background thread
        collections -> { // Successful callback, executed on the FX thread
          allCollections.setAll(
              collections.stream()
                  .map(
                      c ->
                          new RecipeCollectionSummary(
                              c.getId(), c.getTitle(), c.getSourceType(), c.getRecipes().size()))
                  .toList());
          loading.set(false);
        },
        error -> {
          loading.set(false); // Failure Callback
        });
  }

  /*
   * First, search for this id within allCollections:
   * - Unable to find → Clear selectedCollectionId and recipes (L10)
   * - Found → Set selectedCollectionId,
   * then call findCollectionById to obtain the complete object and fill in the
   * recipe list (L3)
   */
  @Override
  public void selectCollection(String collectionId) {
    var match = allCollections.stream().filter(c -> c.id().equals(collectionId)).findFirst();
    if (match.isEmpty()) {
      // clear selection gracefully
      selectedCollectionId = null;
      recipes.clear();
      return;
    }

    selectedCollectionId = collectionId;
    recipes.clear();

    librarianService
        .findCollectionById(collectionId)
        .ifPresent(
            col ->
                recipes.setAll(
                    col.getRecipes().stream()
                        .map(r -> new RecipeSummary(r.getId(), r.getTitle()))
                        .toList()));
  }

  /*
   * Call the service to create a new collection, then call the refresh() method
   * to reload the list and make the newly created collection appear on the
   * interface.(L4)
   */
  @Override
  public void createCollection(String title) {
    if (title == null || title.isBlank()) {
      return;
    }
    librarianService.createCollection(title);
    refresh();
  }

  /*
   * 1. If there is still a "to be deleted" item in the queue, submit the previous
   * deletion first
   * 2. Immediately remove from allCollections (providing
   * immediate feedback to the user).
   * 3. If the item to be deleted is the currently selected one, clear the recipe
   * list.
   * 4. Save the collection to pendingDeleteCollection and set the undo status.
   * 5. Start the PauseTransition (5-second timer), and upon expiration,
   * call commitPendingDelete().
   */
  @Override
  public void deleteCollection(String collectionId) {
    // Cancel any existing undo timer
    if (undoTimer != null) {
      undoTimer.stop();
      commitPendingDelete();
    }

    var match = librarianService.findCollectionById(collectionId).orElse(null);
    if (match == null) {
      return;
    }

    // Remove from display immediately (optimistic removal)
    allCollections.removeIf(c -> c.id().equals(collectionId));
    if (collectionId.equals(selectedCollectionId)) {
      selectedCollectionId = null;
      recipes.clear();
    }

    // Set up undo window
    pendingDeleteCollection = match;
    undoAvailable.set(true);
    undoMessage.set("Deleted: " + match.getTitle());

    undoTimer = new PauseTransition(javafx.util.Duration.millis(undoTimeout.toMillis()));
    undoTimer.setOnFinished(
        e -> {
          commitPendingDelete();
        });
    undoTimer.play();
  }

  @Override
  public void undoDelete() {
    if (!undoAvailable.get()) {
      return;
    }

    if (undoTimer != null) {
      undoTimer.stop();
      undoTimer = null;
    }

    if (pendingDeleteCollection != null) {
      RecipeCollection restored = pendingDeleteCollection;
      allCollections.add(
          new RecipeCollectionSummary(
              restored.getId(),
              restored.getTitle(),
              restored.getSourceType(),
              restored.getRecipes().size()));
      pendingDeleteCollection = null;
    } else if (pendingDeleteRecipe != null) {
      recipes.add(pendingDeleteRecipe);
      adjustCollectionRecipeCount(+1);
      pendingDeleteRecipe = null;
    }

    undoAvailable.set(false);
    undoMessage.set("");
  }

  /*
   * Call the NavigationService, set the selectedRecipeId and switch the current
   * view to RECIPE_EDITOR.
   */
  @Override
  public void selectRecipe(String recipeId) {
    // L9: provide recipe ID for navigation
    navigationService.navigateToRecipe(recipeId);
  }

  /*
   * Same undo pattern as deleteCollection:
   * 1. Commit any existing pending delete first.
   * 2. Optimistically remove from the recipe list.
   * 3. Save to pendingDeleteRecipe and start the 5-second timer.
   * 4. If undo is not called, commitPendingDelete() calls librarianService.deleteRecipe().
   */
  @Override
  public void deleteRecipe(String recipeId) {
    if (undoTimer != null) {
      undoTimer.stop();
      commitPendingDelete();
    }

    RecipeSummary match =
        recipes.stream().filter(r -> r.id().equals(recipeId)).findFirst().orElse(null);
    if (match == null) {
      return;
    }

    recipes.removeIf(r -> r.id().equals(recipeId));
    adjustCollectionRecipeCount(-1);

    pendingDeleteRecipe = match;
    undoAvailable.set(true);
    undoMessage.set("Deleted: " + match.title());

    undoTimer = new PauseTransition(javafx.util.Duration.millis(undoTimeout.toMillis()));
    undoTimer.setOnFinished(e -> commitPendingDelete());
    undoTimer.play();
  }

  // Non-JavaFX accessors (for grading test)

  /* Returns a list of all collection IDs in the filtered list. */
  @Override
  public List<String> getCollectionIds() {
    return filteredCollections.stream().map(RecipeCollectionSummary::id).toList();
  }

  /*
   * Returns the ID of the currently selected collection, or null if none is
   * selected.
   */
  @Override
  public @Nullable String getSelectedCollectionId() {
    return selectedCollectionId;
  }

  /* Returns a list of all recipe IDs in the current view. */
  @Override
  public List<String> getRecipeIds() {
    return recipes.stream().map(RecipeSummary::id).toList();
  }

  /* Returns true if the view is currently loading data, false otherwise. */
  @Override
  public boolean isLoading() {
    return loading.get();
  }

  /* Returns true if an undo operation is available, false otherwise. */
  @Override
  public boolean isUndoAvailable() {
    return undoAvailable.get();
  }

  /* Returns the text currently used for filtering collections. */
  @Override
  public String getFilterText() {
    return filterText.get() == null ? "" : filterText.get();
  }

  // Private helpers

  /*
   * The actual deletion of the service layer is carried out, and all undo states
   * are cleared.
   * This is called when the timer expires or during "continuous deletion".
   */
  private void commitPendingDelete() {
    if (pendingDeleteCollection != null) {
      librarianService.deleteCollection(pendingDeleteCollection.getId());
      pendingDeleteCollection = null;
    } else if (pendingDeleteRecipe != null) {
      librarianService.deleteRecipe(pendingDeleteRecipe.id());
      pendingDeleteRecipe = null;
    }
    undoAvailable.set(false);
    undoMessage.set("");
    undoTimer = null;
  }

  /*
   * Navigates to the Search & Filter view.
   */
  @Override
  public void navigateToSearch() {
    navigationService.navigateTo(NavigationService.View.SEARCH);
  }

  /*
   * Adjusts the recipe count displayed in the collection summary by delta (+1 or -1).
   * Called when a recipe is optimistically removed or restored during undo.
   */
  private void adjustCollectionRecipeCount(int delta) {
    if (selectedCollectionId == null) {
      return;
    }
    for (int i = 0; i < allCollections.size(); i++) {
      RecipeCollectionSummary c = allCollections.get(i);
      if (c.id().equals(selectedCollectionId)) {
        allCollections.set(
            i,
            new RecipeCollectionSummary(
                c.id(), c.title(), c.sourceType(), c.recipeCount() + delta));
        return;
      }
    }
  }
}
