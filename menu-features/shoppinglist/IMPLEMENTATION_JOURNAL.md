# Implementation Journal: Shopping List Result Screen

---

## Git History

Each commit represents one focused, independently reviewable change. The feature was
built bottom-up: data classes first, then the ViewModel interface and implementation,
then tests, then the view layer, and finally the wiring and integration.

| Commit    | Message                                                                        | What it does                                                                                                        |
|-----------|--------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------|
| `4b87369` | add SHOPPING_LIST to NavigationService.View enum                               | Adds the new navigation destination — everything else depends on this enum value existing                           |
| `d1ef2e9` | add IngredientItem data class for shopping list display                        | Plain class pairing a display string with a `BooleanProperty` for checked state                                     |
| `338c1a1` | add RecipeSection data class grouping ingredients by recipe                    | Groups a recipe title with its `ObservableList<IngredientItem>` for the controller to iterate                       |
| `6c5e8c9` | add ShoppingListResultViewModel interface                                      | Defines the observable contract the controller binds to: sections, loading state, navigation                        |
| `5fc7098` | implement ShoppingListResultViewModelImpl                                      | Background fetch via `BackgroundTaskRunner`, filters recipes by ID, builds sections on FX thread                    |
| `1bdccd9` | add unit tests for ShoppingListResultViewModelImpl (SL1-SL7)                   | 7 tests covering section population, ingredient count, checked state, loading flag, navigation, and re-entrant load |
| `3675bfa` | add ShoppingListResultView.fxml layout                                         | Scaffold: toolbar, `ScrollPane` + `VBox` for dynamic sections, Back button                                          |
| `d8ba003` | add ShoppingListResultViewController binding sections to FXML                  | Builds recipe section headers and checkbox rows programmatically; binds strikethrough on check                      |
| `27cfc60` | wire ShoppingListResultViewModel and onConfirm callback in CookYourBooksGuiApp | Injects real `onConfirm` lambda: resolves IDs → `load()` → navigate to `SHOPPING_LIST`                              |
| `10cac65` | navigate to existing shopping list if one already exists on cart button press  | Smart cart button: non-empty list → go to result screen, empty → enter selection mode                               |
| `d7e1699` | add integration tests for shopping list callback chain (IT-SL1–IT-SL3)         | End-to-end tests verifying the confirm callback, smart cart button, and selection mode entry                        |
| `5009648` | spotless to fix format issues                                                  | Auto-format pass to satisfy Google Java Format enforced by Spotless                                                 |

---

## PR History

### PR #5 — [shopping list view on library view](https://github.com/neu-cs3100/sp26-hw-cyb12-group-4633/pull/5)
**Branch:** `shoppinglist-libraryview`

Implemented the recipe selection phase: the Library view entering shopping list mode
(checkboxes on recipes), the confirmation dialog, and the `ShoppingListViewModel` /
`ShoppingListViewModelImpl` that manage selection state and fire the `onConfirm` callback.
This PR established the integration seam — the callback that the result screen wires into.

### PR #8 — [Shoppinglist feature implement shopping list (v1)](https://github.com/neu-cs3100/sp26-hw-cyb12-group-4633/pull/8)
**Branch:** `shoppinglist-feature`

Implemented the result screen: `IngredientItem`, `RecipeSection`, `ShoppingListResultViewModel`,
`ShoppingListResultViewModelImpl`, `ShoppingListResultView.fxml`,
`ShoppingListResultViewController`, and all wiring in `CookYourBooksGuiApp`. Also added
the smart cart button behaviour and the full integration test suite (IT-SL1–IT-SL3).

---

## Decision Log

### Decision 1: Per-recipe grouping vs. flat aggregated list

**Context:** The service layer already provides `PlannerService.generateShoppingList()`,
which merges all ingredients from all selected recipes into one flat `ShoppingList` object.
The simplest implementation would have been to call this method and display the result.

**Decision:** Display ingredients **grouped by recipe** instead, using the `Recipe` objects
directly rather than the aggregated `ShoppingList`.

**Alternatives considered:**
- *Flat list via `PlannerService`:* Simpler to implement but loses the context of which
  ingredient belongs to which dish. If the user wants to swap a recipe last minute, a flat
  list gives them no way to know which items to remove.
- *Per-recipe grouping (chosen):* Requires iterating `Recipe.getIngredients()` manually but
  keeps the display readable and contextually meaningful.

**Why this matters:** The ViewModel (`ShoppingListResultViewModelImpl`) builds
`List<RecipeSection>` on the background thread, not `ShoppingList` — a deliberate choice
that keeps the UI layer independent of the aggregation logic in the service layer.

---

### Decision 2: Interactive checkboxes with strikethrough vs. read-only list

**Context:** The initial V1 wireframe showed checkboxes but did not specify how checked
items would look.

**Decision:** Checked items are **grayed out with strikethrough text**, applied via an
inline style listener on `IngredientItem.checkedProperty()`.

**Alternatives considered:**
- *Remove item from list on check:* Visually clean but loses the ability to undo — the
  user can no longer tell what they have already picked up.
- *Bold unchecked / dim checked (no strikethrough):* Less immediately obvious at a glance
  in a bright store environment.
- *Strikethrough + gray (chosen):* Standard shopping list convention; universally
  understood; reversible by unchecking.

---

### Decision 3: One shopping list at a time

**Context:** Should pressing the cart button always start a fresh selection, or should it
remember the current list?

**Decision:** Pressing the cart button when a list already exists **navigates directly to
the existing list** rather than re-entering selection mode.

**Alternatives considered:**
- *Always start fresh:* Forces the user to re-select recipes every time they want to view
  their list, which is disruptive mid-shop.
- *Ask the user (dialog):* Adds a confirmation step on every cart button press — friction
  that would frustrate frequent users.
- *Single persistent list (chosen):* Matches the mental model of a physical shopping list.
  The user builds a list once and refers back to it. If they want a new list, they can
  navigate back to the Library and start a new selection from there.
