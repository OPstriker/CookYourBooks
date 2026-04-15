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

## Version 2 — Git History

V2 focused on usability improvements found after using V1: adding a clear button,
fixing sidebar visibility, and simplifying the button layout through iteration.

| Commit | Message | What it does |
|--------|---------|--------------|
| `5dca4bb` | fixed dark mode icon and shopping cart to appear only on library view and not in the home page | Hides cart and dark mode buttons when not on Library view |
| `2fad0f6` | fixed homepage | Restores home page layout broken by sidebar visibility changes |
| `3086ed2` | displaying message to clear shopping list or go back but with no actual implementation | UI scaffold for clear/back actions — placeholder only |
| `a73b86a` | clear shopping list implementation | Wires the Clear button to actually reset `sections` and return to selection mode |
| `94c9cc5` | adding cancel / back button to the shopping cart | Adds a Back button to the result screen |
| `05e3237` | made three separate buttons, one to exit, one to clear, one to go into the previous made shopping list, implementation does not work correctly | Experimental three-button layout — later simplified |
| `9e4a328` | changed functionality of buttons, seemed too complicated for users, but now clear is not working as intended | Simplifies button layout; uncovers clear regression |
| `d6883a6` | fixed functionality of the clear button | Restores correct clear behaviour after refactor |
| `5f85b29` | made a back button that served a duplicate purpose, got rid of it for simplicity for users | Removes redundant back button introduced in earlier iteration |

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

### PR #9 — [Shoppinglist v2](https://github.com/neu-cs3100/sp26-hw-cyb12-group-4633/pull/9)
**Branch:** `shoppinglist-v2`

Usability improvements based on V1 feedback: added a Clear button to reset the shopping
list from the result screen, hid the cart and dark mode icons on the Home page, and
simplified the button layout after iterating through several approaches. The final layout
keeps only Back and Clear — the redundant back button introduced mid-iteration was removed.

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

### Decision 3: One shopping list at a time (v1)

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

---

### Decision 4: Clear button vs. no clear action (v2)

**Context:** V1 had no way to clear the shopping list from the result screen — the user
had to navigate back to Library and re-confirm a new selection to start over. After
using V1, this felt like a missing escape hatch.

**Decision:** Add a **Clear** button directly on the result screen that resets the
sections and returns the user to selection mode.

**Alternatives considered:**
- *No clear action (v1 behaviour):* Simple but forces a multi-step workaround to start
  over — navigate back, re-enter selection mode, pick new recipes, confirm again.
- *Clear on Back button press (implicit):* Could clear the list whenever Back is pressed,
  but this would be destructive and unexpected — users pressing Back to briefly check
  something in the Library would lose their list.
- *Dedicated Clear button (chosen):* Explicit and reversible in intent. The user must
  consciously press Clear, which prevents accidental data loss while still giving them
  a fast path to start over.

---

## Version 3 — Git History

V3 added two usability improvements found after using V2: showing which collection each
recipe belongs to, and allowing the user to cross off an entire recipe section with one
click on the header.

| Commit | Message | What it does |
|--------|---------|--------------|
| (pending) | feat(v3): add collectionName and dismissedProperty to RecipeSection | Adds `String collectionName` field, `BooleanProperty dismissed`, and matching getters to `RecipeSection`; updates constructor to accept collection name as second parameter |
| (pending) | feat(v3): resolve collection name per recipe in ShoppingListResultViewModelImpl | Builds a `recipeId → collectionTitle` lookup map in `buildSections()` by iterating `listCollections()` once; passes resolved name (or "Unknown Collection" fallback) into each `RecipeSection` |
| (pending) | feat(v3): add collection subtitle and click-to-dismiss section header in ShoppingListResultViewController | Adds subtitle label below header, click handler to toggle `dismissedProperty()`, and dismissed listener that grays out and strikes through header, subtitle, and all checkboxes |
| (pending) | test(v3): stub listCollections in setUp, add SL8 and SL9 for collection name resolution | Stubs `listCollections()` as empty list by default; adds SL8 (collection name resolved correctly) and SL9 (unknown collection fallback) |

---

### Decision 5: How many buttons on the result screen (v2)

**Context:** V2 started with three buttons (Exit, Clear, View Existing List) but this
was quickly found to be too complicated and overlapping in responsibility.

**Decision:** Settle on **two buttons — Back and Clear** — after iterating through
the three-button layout and a transitional two-button layout that still had a redundant
back action.

**Alternatives considered:**
- *Three buttons (Exit / Clear / View Existing):* Too many choices for a simple screen.
  "Exit" and "View Existing" were effectively the same action.
- *Two buttons with redundant back (interim):* Reduced confusion but still had a
  duplicate path for returning to the Library.
- *Back + Clear (chosen):* Back handles navigation; Clear handles list management.
  Each button has exactly one responsibility with no overlap.

---

### Decision 6: Collection lookup strategy — one pass vs. per-recipe call (v3)

**Context:** `LibrarianService` has no `findCollectionByRecipeId()` method. To show
a collection name under each recipe header, the collection must be resolved manually.

**Decision:** Iterate `listCollections()` **once** in the background thread and build a
`Map<String, String>` keyed by recipe ID before building any sections.

**Alternatives considered:**
- *Call `listCollections()` per recipe:* Simple to read but calls the service N times
  for N recipes — unnecessary repeated work and harder to reason about in tests.
- *Add a new service method:* Would mean modifying `LibrarianService` and its
  implementation for a pure display concern — violates the principle that the UI
  layer should adapt to the service layer, not the other way around.
- *One-pass map (chosen):* Single service call, O(1) lookup per recipe, and the
  lookup map is fully local to `buildSections()` — no state leaks into the ViewModel.

---

### Decision 7: Section-level dismiss via property vs. removing items (v3)

**Context:** Clicking a recipe header should mark the whole section as "done." The
question was how to represent and apply that state.

**Decision:** Add a `BooleanProperty dismissed` to `RecipeSection` and apply inline
styles via a listener — mirroring the existing `IngredientItem.checkedProperty()` pattern.

**Alternatives considered:**
- *Remove the section from the list on click:* Irreversible — user cannot undo without
  clearing and rebuilding the whole list.
- *Iterate and check every ingredient checkbox:* Modifies individual item state, making
  it ambiguous whether an ingredient was checked individually or as part of a dismiss.
  Also harder to undo cleanly.
- *`dismissedProperty()` + style listener (chosen):* Reversible, keeps individual
  checkbox state untouched (items are disabled but not checked), and follows the same
  observable-property pattern already established by `IngredientItem`. The controller
  applies the visual change; the ViewModel data class holds only the boolean — clean
  separation.
