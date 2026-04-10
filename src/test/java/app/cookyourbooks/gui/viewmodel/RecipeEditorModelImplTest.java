package app.cookyourbooks.gui.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import app.cookyourbooks.gui.ViewModelTestBase;
import app.cookyourbooks.model.Instruction;
import app.cookyourbooks.model.Recipe;
import app.cookyourbooks.model.VagueIngredient;
import app.cookyourbooks.repository.RecipeRepository;

class RecipeEditorViewModelTest extends ViewModelTestBase {

  private RecipeRepository mockRepo;
  private RecipeEditorViewModelImpl vm;

  // A simple recipe used across multiple tests
  private Recipe sampleRecipe;

  @BeforeEach
  void setUp() {
    mockRepo = mock(RecipeRepository.class);
    vm = new RecipeEditorViewModelImpl(mockRepo);

    sampleRecipe =
        new Recipe(
            "recipe-1",
            "Pancakes",
            null,
            List.of(
                new VagueIngredient("Flour", null, null, null),
                new VagueIngredient("Eggs", null, null, null)),
            List.of(new Instruction(1, "Mix and cook", List.of())),
            List.of());

    when(mockRepo.findById("recipe-1")).thenReturn(Optional.of(sampleRecipe));
    when(mockRepo.findById("bad-id")).thenReturn(Optional.empty());
  }

  // ── E1: loadRecipe populates title and ingredient list ───────────────────

  @Test
  void e1_loadRecipe_populatesTitle() {
    vm.loadRecipe("recipe-1");
    assertThat(vm.getTitle()).isEqualTo("Pancakes");
  }

  @Test
  void e1_loadRecipe_populatesIngredients() {
    vm.loadRecipe("recipe-1");
    assertThat(vm.getIngredientNames()).containsExactly("Flour", "Eggs");
  }

  @Test
  void e1_loadRecipe_setsRecipeId() {
    vm.loadRecipe("recipe-1");
    assertThat(vm.getRecipeId()).isEqualTo("recipe-1");
  }

  // ── E2: toggleEditMode enables/disables editing ──────────────────────────

  @Test
  void e2_toggleEditMode_entersEditMode() {
    vm.loadRecipe("recipe-1");
    assertThat(vm.isEditing()).isFalse();
    vm.toggleEditMode();
    assertThat(vm.isEditing()).isTrue();
  }

  @Test
  void e2_toggleEditMode_exitsEditMode() {
    vm.loadRecipe("recipe-1");
    vm.toggleEditMode(); // enter
    vm.toggleEditMode(); // exit
    assertThat(vm.isEditing()).isFalse();
  }

  // ── E3: changing title or ingredients sets isDirty ───────────────────────

  @Test
  void e3_changingTitle_setsDirty() {
    vm.loadRecipe("recipe-1");
    assertThat(vm.isDirty()).isFalse();
    vm.titleProperty().set("New Title");
    assertThat(vm.isDirty()).isTrue();
  }

  @Test
  void e3_addingIngredient_setsDirty() {
    vm.loadRecipe("recipe-1");
    assertThat(vm.isDirty()).isFalse();
    vm.addIngredient("Butter");
    assertThat(vm.isDirty()).isTrue();
  }

  @Test
  void e3_removingIngredient_setsDirty() {
    vm.loadRecipe("recipe-1");
    assertThat(vm.isDirty()).isFalse();
    vm.removeIngredient(0);
    assertThat(vm.isDirty()).isTrue();
  }

  // ── E4: discardChanges reverts state and clears dirty ───────────────────

  @Test
  void e4_discardChanges_revertsTitle() {
    vm.loadRecipe("recipe-1");
    vm.titleProperty().set("Changed Title");
    vm.discardChanges();
    assertThat(vm.getTitle()).isEqualTo("Pancakes");
  }

  @Test
  void e4_discardChanges_revertsIngredients() {
    vm.loadRecipe("recipe-1");
    vm.addIngredient("Butter");
    vm.discardChanges();
    assertThat(vm.getIngredientNames()).containsExactly("Flour", "Eggs");
  }

  @Test
  void e4_discardChanges_clearsDirty() {
    vm.loadRecipe("recipe-1");
    vm.titleProperty().set("Changed");
    assertThat(vm.isDirty()).isTrue();
    vm.discardChanges();
    assertThat(vm.isDirty()).isFalse();
  }

  @Test
  void e4_discardChanges_exitsEditMode() {
    vm.loadRecipe("recipe-1");
    vm.toggleEditMode();
    vm.discardChanges();
    assertThat(vm.isEditing()).isFalse();
  }

  // ── E5: isValid is false when title is blank ─────────────────────────────

  @Test
  void e5_isValid_falseWhenTitleBlank() {
    vm.loadRecipe("recipe-1");
    vm.titleProperty().set("");
    assertThat(vm.isValid()).isFalse();
  }

  @Test
  void e5_isValid_falseWhenTitleWhitespace() {
    vm.loadRecipe("recipe-1");
    vm.titleProperty().set("   ");
    assertThat(vm.isValid()).isFalse();
  }

  @Test
  void e5_isValid_trueWhenTitleNotBlank() {
    vm.loadRecipe("recipe-1");
    assertThat(vm.isValid()).isTrue();
  }

  // ── E6: addIngredient / removeIngredient modify the list ─────────────────

  @Test
  void e6_addIngredient_appendsToList() {
    vm.loadRecipe("recipe-1");
    vm.addIngredient("Butter");
    assertThat(vm.getIngredientNames()).contains("Butter");
    assertThat(vm.getIngredientCount()).isEqualTo(3);
  }

  @Test
  void e6_removeIngredient_removesAtIndex() {
    vm.loadRecipe("recipe-1");
    vm.removeIngredient(0); // remove "Flour"
    assertThat(vm.getIngredientNames()).containsExactly("Eggs");
  }

  @Test
  void e6_addIngredient_blankNameIsNoOp() {
    vm.loadRecipe("recipe-1");
    vm.addIngredient("  ");
    assertThat(vm.getIngredientCount()).isEqualTo(2);
  }

  @Test
  void e6_removeIngredient_outOfBoundsIsNoOp() {
    vm.loadRecipe("recipe-1");
    vm.removeIngredient(99);
    assertThat(vm.getIngredientCount()).isEqualTo(2);
  }

  // ── E7: save persists the recipe to the repository ───────────────────────

  @Test
  void e7_save_persistsRecipeToRepository() throws InterruptedException {
    vm.loadRecipe("recipe-1");
    vm.titleProperty().set("Updated Pancakes");

    vm.save();
    waitForStatus("Saved successfully.");

    verify(mockRepo).save(any(Recipe.class));
  }

  @Test
  void e7_save_exitsEditModeOnSuccess() throws InterruptedException {
    vm.loadRecipe("recipe-1");
    vm.toggleEditMode();
    vm.titleProperty().set("Updated Pancakes");

    vm.save();
    waitForStatus("Saved successfully.");

    assertThat(vm.isEditing()).isFalse();
  }

  @Test
  void e7_save_showsSuccessMessage() throws InterruptedException {
    vm.loadRecipe("recipe-1");
    vm.titleProperty().set("Updated Pancakes");

    vm.save();
    waitForStatus("Saved successfully.");

    assertThat(vm.getStatusMessage()).isEqualTo("Saved successfully.");
  }

  // ── E8: save runs on background thread; isSaving is true while running ───

  @Test
  void e8_save_isSavingTrueWhileRunning() throws InterruptedException {
    // Use a latch to catch isSaving=true before the background thread finishes
    CountDownLatch savingStarted = new CountDownLatch(1);
    CountDownLatch savingDone = new CountDownLatch(1);

    // Make the repo block until we're ready to check isSaving
    doAnswer(
            inv -> {
              savingStarted.countDown(); // signal: save has started
              savingDone.await(); // block until test says go
              return null;
            })
        .when(mockRepo)
        .save(any());

    vm.loadRecipe("recipe-1");
    vm.titleProperty().set("Updated");
    vm.save();

    savingStarted.await(2, TimeUnit.SECONDS); // wait for background thread to start
    assertThat(vm.isSaving()).isTrue();

    savingDone.countDown(); // unblock the save
    waitForFxEvents(); // wait for FX callbacks to finish
    assertThat(vm.isSaving()).isFalse();
  }

  // ── E9: save failure preserves dirty state and shows error ───────────────

  @Test
  void e9_saveFailure_preservesDirtyState() throws InterruptedException {
    doThrow(new RuntimeException("disk full")).when(mockRepo).save(any());

    vm.loadRecipe("recipe-1");
    vm.titleProperty().set("Updated");
    vm.save();
    waitForStatus("Save failed");

    assertThat(vm.isDirty()).isTrue();
  }

  @Test
  void e9_saveFailure_staysInEditMode() throws InterruptedException {
    doThrow(new RuntimeException("disk full")).when(mockRepo).save(any());

    vm.loadRecipe("recipe-1");
    vm.toggleEditMode();
    vm.titleProperty().set("Updated");
    vm.save();
    waitForStatus("Save failed");

    assertThat(vm.isEditing()).isTrue();
  }

  @Test
  void e9_saveFailure_showsErrorMessage() throws InterruptedException {
    doThrow(new RuntimeException("disk full")).when(mockRepo).save(any());

    vm.loadRecipe("recipe-1");
    vm.titleProperty().set("Updated");
    vm.save();
    waitForStatus("Save failed");

    assertThat(vm.getStatusMessage()).contains("Save failed");
  }

  // Helper to wait for status
  private void waitForStatus(String expected) throws InterruptedException {
    for (int i = 0; i < 20; i++) {
      waitForFxEvents();
      if (vm.getStatusMessage().contains(expected)) return;
      Thread.sleep(50);
    }
  }

  // ── E10: save while not dirty or not valid is a no-op ────────────────────

  @Test
  void e10_save_notDirty_isNoOp() throws InterruptedException {
    vm.loadRecipe("recipe-1");
    // don't change anything — isDirty stays false
    vm.save();
    waitForFxEvents();

    verify(mockRepo, never()).save(any());
  }

  @Test
  void e10_save_notValid_isNoOp() throws InterruptedException {
    vm.loadRecipe("recipe-1");
    vm.titleProperty().set(""); // makes isValid false
    vm.save();
    waitForFxEvents();

    verify(mockRepo, never()).save(any());
  }
}
