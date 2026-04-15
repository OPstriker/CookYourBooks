# Export to PDF — Implementation Journal

## Git History

All implementation work is on the `exportPDF`, `exportPDF-v2`, and `exportPDF-v3` branches. Commits are ordered from earliest to latest:

| Hash | Message | What changed |
|------|---------|--------------|
| `d87e85f` | feat: add Apache PDFBox 3.0.3 dependency | `libs.versions.toml`, `build.gradle` |
| `d69a749` | feat: implement PdfExporter adapter for recipe-to-PDF conversion | `PdfExporter.java` (new) |
| `4886d7e` | feat: add exportRecipe to LibraryViewModel and implement in LibraryViewModelImpl | `LibraryViewModel.java`, `LibraryViewModelImpl.java` |
| `37f12f8` | feat: wire Export PDF button in LibraryViewController and update FXML | `LibraryViewController.java`, `LibraryView.fxml` |
| `dfb7335` | test: add unit tests for PdfExporter | `PdfExporterTest.java` (new) |
| `c35ab8e` | feat: redesign PdfExporter V2 with formatted layout | `PdfExporter.java` (rewritten) |
| `0af6994` | add v1 and v2 wireframes to design folder | `design/v1-wireframe.png`, `design/v2-wireframe.png` (new) |
| `987cbca` | add RATIONALE.md | `RATIONALE.md` (new) |
| `e752547` | add design-evolution.md | `design/design-evolution.md` (new) |
| `586bc22` | add IMPLEMENTATION_JOURNAL.md | `IMPLEMENTATION_JOURNAL.md` (new) |
| `1948fd8` | add FEATURE_SUMMARY.md | `FEATURE_SUMMARY.md` (new) |
| `78327d9` | update newest Journal and contain all the commit history | `IMPLEMENTATION_JOURNAL.md` (updated) |
| `81c9a96` | refactor: update FEATURE_SUMMARY and IMPLEMENTATION_JOURNAL; rename design-evolution to design-artifacts; redesign V2 wireframe | `FEATURE_SUMMARY.md`, `IMPLEMENTATION_JOURNAL.md`, `design/design-artifacts.md` (new), `design/v2-wireframe.png` (updated) |
| `43082cc` | add v3 wireframe design for PDF export feature | `design/v3-wireframe.png` (new) |
| `91b589c` | docs: fix broken SVG links and update git history to include latest commits | `FEATURE_SUMMARY.md`, `IMPLEMENTATION_JOURNAL.md`, `design/design-artifacts.md` (updated) |
| `9ebbd33` | fix the name details | `IMPLEMENTATION_JOURNAL.md`, `design/design-artifacts.md` (updated) |
| `4cdf232` | feat: add accessibleText and Ctrl+Shift+E keyboard accelerator to Export PDF button | `LibraryViewController.java`, `LibraryView.fxml`, `IMPLEMENTATION_JOURNAL.md` |

The commit order reflects a deliberate inside-out approach: dependency → adapter → ViewModel → View → tests → V3 redesign → documentation. Each layer was working before the next was added.

---

## Pull Request History

| PR | Title | Status | Link |
|----|-------|--------|------|
| #4 | Add Apache PDFBox 3.0.3 dependency for PDF export | Closed (superseded by #6) | https://github.com/neu-cs3100/sp26-hw-cyb12-group-4633/pull/4 |
| #6 | ExportPDF feature Implement | Merged — 2 approvals | https://github.com/neu-cs3100/sp26-hw-cyb12-group-4633/pull/6 |
| #11 | redesign V2 wireframe | Merged — 1 approval | https://github.com/neu-cs3100/sp26-hw-cyb12-group-4633/pull/11 |
| #12 | Add V3 wireframe screenshot for final branded two-column PDF layout | Open | https://github.com/neu-cs3100/sp26-hw-cyb12-group-4633/pull/12 |

---

## Technical Decisions

### Decision 1: Apache PDFBox over iText/OpenPDF

**Options considered:**
- **Apache PDFBox 3.0.3** — Apache 2.0 license, pure Java, straightforward API for text layout
- **iText 7 Community** — more powerful layout engine but AGPL licensed, which is more restrictive
- **OpenPDF** — iText fork under LGPL, also viable but less actively maintained

**Decision:** PDFBox. The Apache 2.0 license is compatible with the rest of the project's dependencies. For our use case (title, ingredient list, numbered instructions), PDFBox's low-level API is sufficient and easier to reason about than iText's higher-level document model.

---

### Decision 2: PdfExporter as a stateless adapter with a private PageWriter inner class

**Problem:** PDF rendering requires mutable state — the current page's content stream and the current Y coordinate. If this state lived on the `PdfExporter` instance, two concurrent export calls would corrupt each other.

**Decision:** Keep `PdfExporter` stateless. All mutable state is scoped inside a private `PageWriter` inner class that is created fresh for each `export()` call. This makes `PdfExporter` safe to reuse across calls and easier to test, since each export starts from a clean state.

---

### Decision 3: BackgroundTaskRunner for the export call in LibraryViewModelImpl

**Problem:** Writing a PDF file is an IO operation that can take tens to hundreds of milliseconds. JavaFX requires all UI updates to happen on the JavaFX Application Thread (FX thread). If the export ran on the FX thread, the entire UI would freeze until the file was written.

**Decision:** Use `BackgroundTaskRunner.run()` to execute `PdfExporter.export()` on a worker thread. The success and error callbacks are automatically dispatched back to the FX thread by `BackgroundTaskRunner`, keeping the UI responsive during export.

---

### Decision 4: FileChooser stays in the Controller, not the ViewModel

**Problem:** Showing a `FileChooser` dialog requires access to the JavaFX `Window` object, which is a UI concept. ViewModels in MVVM should not know anything about the UI.

**Decision:** The `FileChooser` is opened in `LibraryViewController.onExportRecipe()`, which is already on the FX thread (called from a button click). The controller collects the file path and passes it to `vm.exportRecipe(recipeId, path)`. The ViewModel only sees a `Path` — it has no knowledge of how that path was obtained.

---

### Decision 5: Three-stage layout evolution (V1 → V2 → V3)

**V1** exposed four problems: no visual hierarchy, wasted horizontal space, no page identity, and plain appearance inconsistent with "nicely formatted."

**V2** addressed the hierarchy and identity problems incrementally — bold title, bold uppercase section headings, horizontal divider lines between sections, and a simple `Page N of M` footer. This was a deliberate intermediate step: fixing the most visible readability issues before committing to a more complex two-column layout that would require significant refactoring of the rendering logic.

**V3** then addressed the remaining V2 problems — still-wasted horizontal space and no app branding — by introducing the grey header block, two-column body layout with a vertical rule, and a branded footer (`CookYourBooks · Page N of M`). See `design/design-artifacts.md` for the full breakdown of each version's problems and changes.

---

## Accessibility Check

The "Export PDF" button in `LibraryView.fxml` is a standard JavaFX `Button` node. JavaFX buttons are keyboard-accessible by default:

- **Tab** moves focus to the button
- **Enter** or **Space** triggers the button action

The button's `disableProperty` is bound to the recipe list selection — it is only enabled when a recipe is selected, which means keyboard users navigating with Tab will naturally skip it when nothing is selected.

An `accessibleText` attribute (`"Export selected recipe as PDF file"`) is set on the button in FXML to provide a descriptive label for assistive technologies such as screen readers. The button's visible label (`"Export PDF"`) is intentionally short for UI compactness; the `accessibleText` gives the full action description to users who cannot see the button's context.

A keyboard accelerator **Ctrl+Shift+E** is registered in `LibraryViewController.setupExportShortcut()`. It listens for the scene to become available via `sceneProperty()`, then adds the key combination to `scene.getAccelerators()`. The accelerator only fires if the button is not disabled — so it is automatically inactive when no recipe is selected, consistent with the mouse behaviour.

---

## Known Limitations

- **Standard fonts only:** PDFBox's built-in Helvetica font uses Latin-1 encoding. Characters outside this range (accented letters, smart quotes, emoji) are sanitized to ASCII equivalents before rendering. A future improvement would be to embed a Unicode-capable font such as DejaVu Sans.
- **No images:** The exported PDF contains text only. Recipe photos are not included.
- **Fixed A4 size:** The page size is hardcoded to A4. Letter size (used in North America) is not currently supported.
- **No style customization:** Font size, margins, and colors are fixed constants. A future version could expose these as user preferences.
