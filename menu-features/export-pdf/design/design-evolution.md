# Export to PDF — Design Evolution

## Version 1: Plain Text Layout

**Screenshot:** `v1-wireframe.png`

![V1 Export PDF](v1-wireframe.png)

Version 1 was the first working implementation of the PDF export. The goal was to get the recipe data onto a page in a readable order. The layout was a simple top-to-bottom single column:

- Recipe title displayed as large text at the top left
- Serving count directly below the title
- "Ingredients" section heading followed by a dashed list of ingredients
- "Instructions" section heading followed by numbered steps
- No visual decoration, dividers, or page structure

The PDF was functional — all recipe data appeared correctly, word-wrapping and automatic pagination worked, and Unicode characters were sanitized to prevent PDFBox encoding errors.

### Problems We Noticed in Version 1

After generating several real recipes with V1, we identified four issues:

1. **No visual hierarchy.** The title, section headings, and body text all looked similar. A user scanning the page could not quickly locate the ingredients or steps.

2. **Wasted horizontal space.** Ingredients lists are usually short lines. Printing them in a full-width single column left the right half of the page completely empty, which looked unfinished.

3. **No page identity.** There was no indication of which app produced the PDF, and no page number on multi-page recipes. A printed page with no label could be confused with any other document.

4. **Plain appearance does not match "nicely formatted."** The assignment requirement specifically asks for a nicely formatted PDF. V1 looked like raw terminal output, not a document a food critic like Jimmy would want to hand to a colleague.

---

## Version 2: Formatted Layout with Two-Column Body

**Screenshot:** `v2-wireframe.png`

![V2 Export PDF](v2-wireframe.png)

Based on the problems found in V1, Version 2 introduced a structured visual layout:

- **Grey header background block** spanning the full page width, containing the recipe title and serving count centered horizontally — makes the title immediately visible and gives the PDF a branded, document-like feel
- **Horizontal divider lines** separating the header from the body, and the body from the footer — creates clear visual zones so the reader's eye knows where to look
- **Two-column body layout** — INGREDIENTS on the left, INSTRUCTIONS on the right, separated by a thin vertical rule — uses the horizontal space efficiently and lets the reader see ingredients and steps side by side
- **Footer on every page** — "CookYourBooks" on the left and "Page N of M" on the right — gives the document identity and helps with multi-page recipes

### Why These Changes

The two-column layout directly addresses the wasted space problem from V1. Ingredients are typically short lines (e.g. "- 200g pasta") while instructions are longer, so splitting them into parallel columns makes both sections easier to scan without the page feeling empty.

The header and footer changes address the identity and hierarchy problems. A user printing the recipe now gets a document that looks intentional, not like a debug dump.

The divider lines were a small addition but had a large visual impact — they give the reader clear landmarks without adding any text content.
