package app.cookyourbooks.gui.viewmodel;

import java.util.List;
import java.util.Optional;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;

import org.jspecify.annotations.Nullable;

import app.cookyourbooks.gui.BackgroundTaskRunner;
import app.cookyourbooks.model.Ingredient;
import app.cookyourbooks.model.Instruction;
import app.cookyourbooks.model.Recipe;
import app.cookyourbooks.model.VagueIngredient;
import app.cookyourbooks.repository.RecipeRepository;
import app.cookyourbooks.services.LibrarianService;

public class RecipeEditorViewModelImpl implements RecipeEditorViewModel {

  private final RecipeRepository recipeRepository;

  // Optional: only non-null when this instance is used for import drafts.
  // Needed because saving a brand-new recipe requires both repo persistence AND
  // collection assignment — LibrarianService.saveRecipe() handles both in one call.
  private final @Nullable LibrarianService librarianService;

  // Stores the target collection ID when saving an OCR-imported draft.
  // Set by loadDraft(), cleared after saveDraft() succeeds.
  private @Nullable String draftCollectionId;
  private List<Instruction> draftInstructions = List.of();

  // Optional callback invoked after discardChanges() completes on a draft.
  // Set by ImportViewController so the Back/Discard button returns to the import IDLE state
  // instead of navigating to the Library. Null when the editor is used standalone.
  private @Nullable Runnable onDraftDiscarded;

  private @Nullable String recipeId;
  private @Nullable Recipe loadedRecipe;
  private boolean suppressDirty = false;

  private final StringProperty title = new SimpleStringProperty("");
  private final ObservableList<String> ingredients = FXCollections.observableArrayList();
  private final ObservableList<String> instructions = FXCollections.observableArrayList();
  private final BooleanProperty editing = new SimpleBooleanProperty(false);
  private final BooleanProperty isDirty = new SimpleBooleanProperty(false);
  private final BooleanProperty isValid = new SimpleBooleanProperty(false);
  private final BooleanProperty isSaving = new SimpleBooleanProperty(false);
  private final StringProperty statusMessage = new SimpleStringProperty("");

  /**
   * Constructor for the main Recipe Editor. Does not support saving to a collection — use this when
   * editing an already-persisted recipe via {@link #loadRecipe(String)}.
   *
   * @param recipeRepository the repository used to load and save recipes
   */
  public RecipeEditorViewModelImpl(RecipeRepository recipeRepository) {
    this(recipeRepository, null);
  }

  /**
   * Constructor for the embedded import editor. Accepts an optional {@link LibrarianService} so
   * that {@link #saveDraft()} can call {@code saveRecipe(recipe, collectionId)}, which both
   * persists the recipe and assigns it to the target collection in one operation.
   *
   * @param recipeRepository the repository used to load and save recipes
   * @param librarianService the librarian service used to save new recipes to a collection, or
   *     {@code null} when collection assignment is not needed
   */
  public RecipeEditorViewModelImpl(
      RecipeRepository recipeRepository, @Nullable LibrarianService librarianService) {
    this.recipeRepository = recipeRepository;
    this.librarianService = librarianService;

    // If title or ingredients change, mark as dirty unless suppressDirty is active.
    title.addListener(
        (obs, oldVal, newVal) -> {
          if (!suppressDirty) {
            isDirty.set(true);
          }
          isValid.set(newVal != null && !newVal.isBlank());
        });
    ingredients.addListener(
        (ListChangeListener<String>)
            c -> {
              if (!suppressDirty) {
                isDirty.set(true);
              }
            });
  }

  /**
   * Returns the editable title property, bound bidirectionally to the title TextField in the View.
   *
   * @return the title {@link StringProperty}
   */
  @Override
  public StringProperty titleProperty() {
    return title;
  }

  /**
   * Returns the observable list of ingredient name strings, bound to the ingredients ListView.
   *
   * @return the ingredients {@link ObservableList}
   */
  @Override
  public ObservableList<String> ingredientsProperty() {
    return ingredients;
  }

  /**
   * Returns the observable list of instruction strings, bound to the instructions ListView. Not
   * part of the {@link RecipeEditorViewModel} interface — exposed for the controller.
   *
   * @return the instructions {@link ObservableList}
   */
  public ObservableList<String> instructionsProperty() {
    return instructions;
  }

  /**
   * Returns the editing mode property. When {@code true}, the title field and ingredient controls
   * are editable.
   *
   * @return the editing {@link BooleanProperty}
   */
  @Override
  public BooleanProperty editingProperty() {
    return editing;
  }

  /**
   * Returns the dirty state property. {@code true} when there are unsaved changes.
   *
   * @return the isDirty {@link BooleanProperty}
   */
  @Override
  public BooleanProperty isDirtyProperty() {
    return isDirty;
  }

  /**
   * Returns the validity property. {@code true} when the current title is non-blank.
   *
   * @return the isValid {@link BooleanProperty}
   */
  @Override
  public BooleanProperty isValidProperty() {
    return isValid;
  }

  /**
   * Returns the saving indicator property. {@code true} while a background save is in progress. The
   * View uses this to disable controls and show "Saving..." on the button.
   *
   * @return the isSaving {@link BooleanProperty}
   */
  @Override
  public BooleanProperty isSavingProperty() {
    return isSaving;
  }

  /**
   * Returns the status message property, bound to the status label in the View. Displays success or
   * error messages after a save attempt.
   *
   * @return the statusMessage {@link StringProperty}
   */
  @Override
  public StringProperty statusMessageProperty() {
    return statusMessage;
  }

  /**
   * Loads a persisted recipe by ID from the repository, populating the title and ingredient list.
   * Uses {@code suppressDirty} to prevent the programmatic property updates from marking the form
   * dirty. Exits edit mode after loading.
   *
   * @param recipeId the ID of the recipe to load
   */
  @Override
  public void loadRecipe(String recipeId) {
    statusMessage.set("");
    Optional<Recipe> result = recipeRepository.findById(recipeId);
    if (result.isEmpty()) {
      statusMessage.set("Recipe not found: " + recipeId);
      return;
    }

    Recipe recipe = result.get();
    loadedRecipe = recipe;
    this.recipeId = recipe.getId();

    suppressDirty = true;
    title.set(recipe.getTitle());
    ingredients.setAll(recipe.getIngredients().stream().map(Object::toString).toList());
    instructions.setAll(recipe.getInstructions().stream().map(Object::toString).toList());
    isDirty.set(false);
    suppressDirty = false;

    isValid.set(!recipe.getTitle().isBlank());
    statusMessage.set("");
    editing.set(false);
    statusMessage.set("");
  }

  /**
   * Pre-populates the editor from an OCR-extracted draft recipe that has not yet been saved. Unlike
   * {@link #loadRecipe(String)}, this accepts a transient {@link Recipe} with no repository ID.
   * Stores the {@code collectionId} so that {@link #save()} can call {@code
   * librarianService.saveRecipe()} to persist and assign the collection in one call. Sets {@code
   * isDirty = true} immediately so the Save button is enabled from the start. Opens the editor
   * directly in edit mode so the user can begin reviewing right away.
   *
   * @param draft the OCR-extracted recipe (id will be null)
   * @param collectionId the ID of the collection to save into when the user confirms
   */
  public void loadDraft(Recipe draft, String collectionId) {
    loadedRecipe = null;
    recipeId = null;
    draftCollectionId = collectionId;
    draftInstructions = List.copyOf(draft.getInstructions());

    suppressDirty = true;
    title.set(draft.getTitle());
    ingredients.setAll(draft.getIngredients().stream().map(Object::toString).toList());
    instructions.setAll(draft.getInstructions().stream().map(Object::toString).toList());
    isDirty.set(true);
    suppressDirty = false;

    isValid.set(draft.getTitle() != null && !draft.getTitle().isBlank());
    editing.set(true);
    statusMessage.set("Review and edit before saving");
  }

  /**
   * Sets a callback to invoke after {@link #discardChanges()} completes on a draft. Used by {@link
   * app.cookyourbooks.gui.view.ImportViewController} so that discarding a draft returns the Import
   * View to IDLE rather than navigating to the Library.
   *
   * @param callback the runnable to invoke after discard, or {@code null} to clear
   */
  public void setOnDraftDiscarded(@Nullable Runnable callback) {
    this.onDraftDiscarded = callback;
  }

  /**
   * Toggles between view mode and edit mode. Clears the status message when entering edit mode so
   * stale success/error messages from a previous save are not shown during editing.
   */
  @Override
  public void toggleEditMode() {
    boolean enteringEditMode = !editing.get();
    if (enteringEditMode) {
      statusMessage.set("");
    }
    editing.set(!editing.get());
  }

  /**
   * Adds a new ingredient with the given name to the end of the ingredient list. Ignores blank or
   * null names.
   *
   * @param name the ingredient name to add
   */
  @Override
  public void addIngredient(String name) {
    if (name == null || name.isBlank()) {
      return;
    }
    ingredients.add(name);
  }

  /**
   * Removes the ingredient at the specified index from the ingredient list. No-op if the index is
   * out of bounds.
   *
   * @param index the zero-based index of the ingredient to remove
   */
  @Override
  public void removeIngredient(int index) {
    if (index >= 0 && index < ingredients.size()) {
      ingredients.remove(index);
    }
  }

  /**
   * Discards all unsaved changes and exits edit mode. For draft recipes (not yet persisted), clears
   * all fields and invokes the {@link #onDraftDiscarded} callback if set. For persisted recipes,
   * restores the title and ingredients to the last loaded state.
   */
  @Override
  public void discardChanges() {
    if (loadedRecipe == null) {
      suppressDirty = true;
      title.set("");
      ingredients.clear();
      instructions.clear();
      suppressDirty = false;
      isDirty.set(false);
      draftCollectionId = null;
      editing.set(false);
      if (onDraftDiscarded != null) {
        onDraftDiscarded.run();
      }
      return;
    }
    suppressDirty = true;
    title.set(loadedRecipe.getTitle());
    ingredients.setAll(loadedRecipe.getIngredients().stream().map(Object::toString).toList());
    suppressDirty = false;
    isDirty.set(false);
    editing.set(false);
  }

  /**
   * Persists the current edits to the repository on a background thread. No-op if the recipe is not
   * dirty (E10) or the title is blank. For draft recipes ({@code recipeId == null}), delegates to
   * {@link #saveDraft()}. On success: exits edit mode, clears dirty, shows "Saved successfully." On
   * failure: stays in edit mode, preserves dirty state, shows an error message.
   */
  @Override
  public void save() {
    if (!isDirty()) return;
    if (!isValid()) {
      statusMessage.set("Title cannot be blank.");
      return;
    }

    if (recipeId == null && draftCollectionId != null && librarianService != null) {
      saveDraft();
      return;
    }

    if (loadedRecipe == null) return;

    isSaving.set(true);

    Recipe toSave =
        new Recipe(
            recipeId,
            title.get(),
            loadedRecipe.getServings(),
            ingredients.stream()
                .map(name -> new VagueIngredient(name, null, null, null))
                .collect(java.util.stream.Collectors.toList()),
            loadedRecipe.getInstructions(),
            loadedRecipe.getConversionRules());

    var unused =
        BackgroundTaskRunner.run(
            () -> {
              recipeRepository.save(toSave);
              return toSave;
            },
            saved -> {
              loadedRecipe = saved;
              isSaving.set(false);
              isDirty.set(false);
              editing.set(false);
              statusMessage.set("Saved successfully.");
            },
            error -> {
              isSaving.set(false);
              editing.set(true);
              isDirty.set(true);
              statusMessage.set("Save failed: " + error.getMessage());
            });
  }

  /**
   * Saves a brand-new OCR-imported recipe to the library using {@code
   * librarianService.saveRecipe()}, which handles both repository persistence and collection
   * membership in a single call. Called by {@link #save()} when {@code recipeId == null} and a
   * {@code draftCollectionId} is set. Clears {@code draftCollectionId} on success so subsequent
   * saves use the existing-recipe path.
   */
  private void saveDraft() {
    isSaving.set(true);
    final String collId = draftCollectionId;
    final LibrarianService svc = librarianService;
    if (svc == null || collId == null) {
      return;
    }

    List<Ingredient> ingredientObjs =
        ingredients.stream()
            .map(name -> (Ingredient) new VagueIngredient(name, null, null, null))
            .collect(java.util.stream.Collectors.toList());
    Recipe toSave =
        new Recipe(null, title.get(), null, ingredientObjs, draftInstructions, List.of());

    var unused =
        BackgroundTaskRunner.run(
            () -> {
              svc.saveRecipe(toSave, collId);
              return toSave;
            },
            saved -> {
              loadedRecipe = saved;
              draftCollectionId = null;
              draftInstructions = List.of();
              isSaving.set(false);
              isDirty.set(false);
              editing.set(false);
              statusMessage.set("Saved successfully.");
            },
            error -> {
              isSaving.set(false);
              statusMessage.set("Save failed: " + error.getMessage());
            });
  }

  /**
   * Returns the ID of the currently loaded recipe, or {@code null} if no recipe is loaded or if the
   * editor contains an unsaved draft.
   *
   * @return the recipe ID, or {@code null}
   */
  @Override
  public @Nullable String getRecipeId() {
    return recipeId;
  }

  /**
   * Returns the current value of the title field.
   *
   * @return the title string
   */
  @Override
  public String getTitle() {
    return title.get();
  }

  /**
   * Returns the number of ingredients currently in the list.
   *
   * @return ingredient count
   */
  @Override
  public int getIngredientCount() {
    return ingredients.size();
  }

  /**
   * Returns a snapshot of all ingredient name strings currently in the list.
   *
   * @return list of ingredient names
   */
  @Override
  public List<String> getIngredientNames() {
    return ingredients.stream().toList();
  }

  /**
   * Returns whether the editor is currently in edit mode.
   *
   * @return {@code true} if editing
   */
  @Override
  public boolean isEditing() {
    return editing.get();
  }

  /**
   * Returns whether there are unsaved changes.
   *
   * @return {@code true} if dirty
   */
  @Override
  public boolean isDirty() {
    return isDirty.get();
  }

  /**
   * Returns whether the current edits are valid (i.e. the title is non-blank).
   *
   * @return {@code true} if valid
   */
  @Override
  public boolean isValid() {
    return isValid.get();
  }

  /**
   * Returns whether a save operation is currently in progress on a background thread.
   *
   * @return {@code true} if saving
   */
  @Override
  public boolean isSaving() {
    return isSaving.get();
  }

  /**
   * Returns the current status or error message displayed in the View.
   *
   * @return the status message string
   */
  @Override
  public String getStatusMessage() {
    return statusMessage.get();
  }

  /**
   * Moves the ingredient at the given index one position up in the list. No-op if the index is 0 or
   * out of bounds.
   *
   * @param index the zero-based index of the ingredient to move up
   */
  @Override
  public void moveIngredientUp(int index) {
    if (index <= 0 || index >= ingredients.size()) {
      return;
    }
    String item = ingredients.remove(index);
    ingredients.add(index - 1, item);
  }

  /**
   * Moves the ingredient at the given index one position down in the list. No-op if the index is at
   * the last position or out of bounds.
   *
   * @param index the zero-based index of the ingredient to move down
   */
  @Override
  public void moveIngredientDown(int index) {
    if (index < 0 || index >= ingredients.size() - 1) {
      return;
    }
    String item = ingredients.remove(index);
    ingredients.add(index + 1, item);
  }

  /**
   * Clears the status message. Called by the controller when navigating away so stale messages
   * don't appear when the user returns to the editor.
   */
  public void clearStatus() {
    statusMessage.set("");
  }
}
