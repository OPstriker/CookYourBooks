# Feature Summary: Shopping List Result Screen (v1)

---

## Screenshots

### 1. Recipe Selection Mode

![Recipe Selection Mode](design/screenshots/v1/screenshot-selection-mode.png)

---

### 2. Shopping List Result Screen

![Shopping List Result Screen](design/screenshots/v1/screenshot-result-screen.png)

---

### 3. Checked Items (Strikethrough)

![Checked Items](design/screenshots/v1/screenshot-checked-items.png)

---

## Integration Notes

The Shopping List feature connects to the rest of the app at three points:

### NavigationService
`NavigationService.View.SHOPPING_LIST` was added to the navigation enum so
`MainViewController` can route to the result screen. The existing `showView()` logic
already handles the Back button visibility for any non-HOME, non-LIBRARY view.

### ShoppingListViewModel (recipe selection phase)
The selection phase (`ShoppingListViewModel` / `ShoppingListViewModelImpl`) was
implemented by a teammate in PR #5. It fires an `onConfirm` callback with the set of
selected recipe IDs. This feature wires into that callback in `CookYourBooksGuiApp`:

```java
new ShoppingListViewModelImpl(selectedIds -> {
    resultVm.load(new HashSet<>(selectedIds));
    navigationService.navigateTo(NavigationService.View.SHOPPING_LIST);
})
```

### LibrarianService
`ShoppingListResultViewModelImpl` calls `LibrarianService.listAllRecipes()` on a
background thread to resolve the selected recipe IDs into full `Recipe` objects. No new
service methods were needed the feature reuses the existing service layer.

### MainViewController (smart cart button)
The shopping cart button in `MainViewController` was updated to check whether a shopping
list already exists. If `resultVm.sectionsProperty()` is non-empty, the button navigates
directly to the result screen instead of entering selection mode. This required passing
`ShoppingListResultViewModel` as a third constructor parameter to `MainViewController`.

---

## Status

### Complete
- `IngredientItem` and `RecipeSection` data classes
- `ShoppingListResultViewModel` interface and `ShoppingListResultViewModelImpl`
- `ShoppingListResultView.fxml` layout (toolbar, scrollable sections, Back button)
- `ShoppingListResultViewController` (dynamic section building, checkbox binding, strikethrough)
- Full wiring in `CookYourBooksGuiApp` (real `onConfirm` callback, `wireShoppingListResult()`)
- Smart cart button in `MainViewController` (existing list → navigate directly, no list → select)
- 7 unit tests (SL1–SL7) for `ShoppingListResultViewModelImpl`
- 3 integration tests (IT-SL1–IT-SL3) for the full callback chain

### In Progress
- V2 design iteration (design-evolution.md — V2 section to be added)

### Known Limitations
- Checked state is **in-memory only** — if the user navigates away and returns, all
  checkboxes reset to unchecked
- There is no "clear list" or "start over" button — the only way to build a new list is
  to navigate back to the Library, which implicitly clears the existing one when a new
  selection is confirmed
- The Back button always returns to the Library view, not to whichever screen the user
  came from
