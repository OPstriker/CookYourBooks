# Design Evolution: Shopping List Result Screen

---

## Version 1 — Initial Wireframe

**Artifact:** ![v1-wireframe.png](v1-wireframe.png)

The V1 wireframe was sketched by hand and shows the full user flow across four screens:

1. **Home page** — the starting point; the user clicks the shopping cart icon in the sidebar
2. **Shopping List Add Menu** — the Library view enters selection mode, showing checkboxes
   next to each recipe so the user can pick what they want to cook
3. **Confirm Screen** — a dialog asks the user to confirm their selection before proceeding
4. **Shopping List** — the result screen listing all ingredients grouped by recipe, with
   checkboxes the user can tick off while shopping

A key design decision captured in the wireframe is the note:
> *"If a Shopping List exists, will redirect here automatically"*

This meant that pressing the cart button a second time should skip the selection flow
entirely and take the user straight back to their existing list — avoiding the frustration
of having to re-select recipes just to view what they already built.

### What the V1 wireframe established

- The shopping list is **grouped by recipe** (one bold header per recipe, ingredients listed below)
- Ingredients are **checkboxes**, not a plain read-only list
- The flow is linear: Home → Add Menu → Confirm → Shopping List
- The cart button is **context-aware**: new list → selection mode, existing list → skip straight to result

### What was left undefined in V1

- How checked items would be visually distinguished (strikethrough? color change? both?)
- Whether there would be a "clear list" or "start over" option
- The exact styling of the ingredient rows (bordered box vs. plain list)
- How the Back button would behave (return to Library vs. previous screen)
