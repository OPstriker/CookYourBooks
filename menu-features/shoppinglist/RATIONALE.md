# Design Rationale: Shopping List Result Screen

## Why I Chose This Feature

We chose the Shopping List result screen because it completes the user journey that the
recipe selection flow starts. Without it, the user selects recipes and confirms but
nothing happens. The result screen turns that selection into something immediately
actionable: a checklist the user can take with them while shopping. It felt like the most
meaningful piece to implement because it is the payoff of the entire feature.

## What User Need It Addresses

This feature directly addresses **Mary's** need for a lightweight shopping list manager.
Mary is a busy professional who grew up with technology she expects apps to remove
friction, not add it. Before this feature, she would have to open each recipe
individually, mentally note the ingredients, and either write them down or hold them in
her head at the store.

The shopping list screen eliminates that entirely: she selects the recipes she plans to
cook, confirms, and gets a single organized view of everything she needs to buy, grouped
by recipe so she can cross-reference while shopping. The interactive checkboxes let her
tick items off in real time as she moves through the store, so she never loses her place.

## Alternatives Considered

### Per-recipe grouping vs. a flat aggregated list

The service layer already provides `PlannerService.generateShoppingList()`, which merges
all ingredients from all selected recipes into one flat list. We chose per-recipe grouping
instead because a flat list becomes hard to read when recipes share ingredients with
different quantities, and it removes the context of which dish each ingredient belongs to
— context Mary would need if she wants to swap a recipe out last minute.

### Interactive checkboxes vs. a read-only list

A static list would have been simpler to implement, but it would force the user to track
progress mentally. Checkboxes with strikethrough styling give immediate visual feedback
and make the screen genuinely useful at the store rather than just a reference.

### One shopping list at a time

We decided that pressing the cart button when a list already exists navigates directly to
the existing list rather than starting a new selection. Supporting multiple simultaneous
lists would increase complexity significantly and make it harder for the user to track
what they actually need. one current list is more useful than several stale ones
competing for attention.
