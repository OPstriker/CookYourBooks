# Export to PDF — Feature Summary

## Screenshots

### V1 — Plain Text Layout
![V1 PDF Output](design/v1-wireframe.png)

Version 1 output: single-column plain text, no visual hierarchy, no page number.

### V2 — Structured Single-Column Layout (Wireframe)
![V2 Wireframe](design/v2-wireframe.svg)

Version 2 wireframe: bold title, uppercase section headings, horizontal dividers between sections, and a simple right-aligned page number footer. Still single-column — no branded header block yet.

### V3 — Final: Branded Two-Column Layout
![V3 PDF Output](design/v3-wireframe.png)

Version 3 (final) output: grey header block with centered title and serving count, two-column body (INGREDIENTS left, INSTRUCTIONS right), vertical rule between columns, and branded footer "CookYourBooks · Page N of M".

### File Chooser Dialog
Clicking "Export PDF" opens the operating system's native save dialog. The filename is pre-filled with the recipe title (e.g. `Garlic Butter Pasta.pdf`). The user chooses where to save the file and confirms.

## Integration Notes

The Export PDF feature is wired into the existing Library View MVVM stack and does not require any new views or navigation.

- **PdfExporter** (`src/main/java/app/cookyourbooks/adapters/PdfExporter.java`) is the adapter responsible for rendering a `Recipe` domain object into a formatted A4 PDF. It has no dependency on JavaFX and can be used independently of the UI.
- **LibraryViewModel** (`src/main/java/app/cookyourbooks/gui/viewmodel/LibraryViewModel.java`) exposes `exportRecipe(String recipeId, Path outputPath)`. The implementation in `LibraryViewModelImpl` looks up the recipe via `LibrarianService.listAllRecipes()` and delegates to `PdfExporter` on a background thread using `BackgroundTaskRunner`.
- **LibraryViewController** (`src/main/java/app/cookyourbooks/gui/view/LibraryViewController.java`) handles the button click. It opens a `FileChooser` on the FX thread, collects the save path from the user, then calls `vm.exportRecipe()`. A confirmation `Alert` appears after the export is triggered.
- **LibraryView.fxml** (`src/main/resources/fxml/LibraryView.fxml`) already contained the `exportButton` node from GA1. Only the button text was updated from `"Export"` to `"Export PDF"`.

## Status

| Item | Status |
|------|--------|
| Export PDF button in Library View | Complete |
| FileChooser for save path selection | Complete |
| V1 plain text PDF layout | Complete |
| V2 structured single-column layout (wireframe) | Complete |
| V3 formatted layout (header, two-column, footer) | Complete |
| Export runs on background thread (UI stays responsive) | Complete |
| Unicode character sanitization | Complete |
| Automatic word-wrap for long instruction text | Complete |
| Automatic pagination for long recipes | Complete |
| Unit tests (PdfExporterTest.java) | Complete — 9 tests passing |
| Keyboard accessible (Tab + Enter) | Complete |
| Font supports Latin-1 only (no Unicode fonts embedded) | Known Limitation |
| Recipe images not included in PDF | Known Limitation |
| Page size fixed to A4 | Known Limitation |
