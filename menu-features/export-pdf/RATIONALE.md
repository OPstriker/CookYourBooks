# Export to PDF — Design Rationale

## Why We Chose This Feature

We chose Export to PDF because it addresses a real gap between the digital app and the physical world where cooking actually happens. Many users collect recipes digitally but still prefer printed copies in the kitchen — paper does not go to sleep, does not need unlocking, and does not get splattered with cooking oil. A PDF export also lets users share recipes with others who do not have the app installed, since PDF is a universally readable format.

The feature also had a clear and realistic implementation path using Apache PDFBox, which fit the team's remaining capacity after GA1 while still delivering meaningful user value.

## User Need

Our GA0 persona **Jimmy Chu** is directly affected:

**Jimmy** (food critic, tech comfort 2/5) has collected hundreds of recipes over 20 years — from chef interviews, magazine clippings, and post-meal notes. He uses a laptop daily but gives up on any tool that takes more than a few minutes to learn. He often needs to reference a recipe quickly before or during writing a review, and he is used to working with physical documents.

For Jimmy, being able to click one button and get a clean, printable PDF of a recipe means he can bring it to a restaurant visit, hand it to a colleague, or file it alongside his physical clippings — without needing to learn anything new. The Export PDF feature fits directly into how he already works.

## Alternatives Considered

**1. Export to plain text (.txt)**
We considered exporting recipes as plain text files. The advantage is simplicity — no third-party library needed. However, plain text has no formatting, no section headers, and no visual hierarchy, making it harder to read at a glance in a kitchen or print in a presentable way. PDF preserves layout and is universally printable.

**2. Export to Markdown (.md)**
The codebase already has a `MarkdownExporter` for shopping lists, so exporting recipes to Markdown was a natural option. Markdown is readable as plain text and renders well in tools like GitHub or Notion. However, most non-technical users (like Jimmy) do not have a Markdown viewer, and printing raw Markdown produces cluttered output. PDF was the better choice for a general audience.

**3. Print directly from the app (JavaFX PrinterJob)**
JavaFX has a built-in `PrinterJob` API that can print any node directly. We decided against this because it ties the output to the current screen layout, requires a printer to be connected at export time, and does not produce a shareable file. PDF gives the user a file they can store, email, or print later.

## What We Chose

We implemented a `PdfExporter` adapter class using Apache PDFBox 3.0.3. The exporter takes a `Recipe` domain object and writes a formatted A4 PDF with a grey header block, centered title, two-column ingredients and instructions layout, and a footer on every page. The export is triggered from the Library View by selecting a recipe and clicking "Export PDF", which opens a system file chooser so the user controls where the file is saved.
