package app.cookyourbooks.adapters;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import app.cookyourbooks.model.Ingredient;
import app.cookyourbooks.model.Instruction;
import app.cookyourbooks.model.Recipe;

/**
 * Exports a {@link Recipe} to a nicely formatted PDF file using Apache PDFBox.
 *
 * <p>The generated PDF layout:
 *
 * <ul>
 *   <li>Grey header background block with centered recipe title and serving count
 *   <li>Horizontal divider line separating header from body
 *   <li>Two-column body: Ingredients (left) and Instructions (right)
 *   <li>Footer on every page: "CookYourBooks · Page N"
 * </ul>
 *
 * <p>Long content that overflows the two-column layout falls back to single-column. New pages are
 * added automatically when content exceeds the bottom margin.
 */
public final class PdfExporter {

  // ── Page geometry ──────────────────────────────────────────────────────────

  private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
  private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();
  private static final float MARGIN = 55f;
  private static final float CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN;

  // ── Header ─────────────────────────────────────────────────────────────────

  private static final float HEADER_HEIGHT = 80f;
  // Light grey background for the header block (RGB 0–1 scale)
  private static final float HEADER_GREY = 0.92f;

  // ── Two-column layout ──────────────────────────────────────────────────────

  private static final float COL_GAP = 24f;
  private static final float COL_WIDTH = (CONTENT_WIDTH - COL_GAP) / 2f;

  // ── Footer ─────────────────────────────────────────────────────────────────

  private static final float FOOTER_HEIGHT = 28f;

  // ── Font sizes ─────────────────────────────────────────────────────────────

  private static final float TITLE_SIZE = 20f;
  private static final float SUBTITLE_SIZE = 10f;
  private static final float SECTION_HEADING_SIZE = 10f;
  private static final float BODY_SIZE = 10f;
  private static final float FOOTER_SIZE = 8f;

  // ── Usable body area (between header+divider and footer) ───────────────────

  // Y coordinate where the body content starts (just below header + divider + spacing)
  private static final float BODY_TOP = PAGE_HEIGHT - HEADER_HEIGHT - 18f;
  // Y coordinate where body content must stop (above footer)
  private static final float BODY_BOTTOM = MARGIN + FOOTER_HEIGHT + 8f;

  // ── Left / right column X origins ─────────────────────────────────────────

  private static final float LEFT_COL_X = MARGIN;
  private static final float RIGHT_COL_X = MARGIN + COL_WIDTH + COL_GAP;

  /**
   * Exports the given recipe to a PDF file at {@code outputPath}.
   *
   * <p>Overwrites the file if it already exists.
   *
   * @param recipe the recipe to export (must not be null)
   * @param outputPath the destination file path (must not be null)
   * @throws IOException if the PDF cannot be written
   */
  public void export(Recipe recipe, Path outputPath) throws IOException {
    try (PDDocument doc = new PDDocument()) {

      // Pre-compute wrapped lines for both columns so we know how many pages are needed
      PDType1Font bodyFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
      PDType1Font boldFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

      List<String> ingredientLines = buildIngredientLines(recipe, bodyFont);
      List<String> instructionLines = buildInstructionLines(recipe, bodyFont);

      // Render pages
      renderPages(doc, recipe, ingredientLines, instructionLines, bodyFont, boldFont);

      doc.save(outputPath.toFile());
    }
  }

  // ── Rendering ──────────────────────────────────────────────────────────────

  private void renderPages(
      PDDocument doc,
      Recipe recipe,
      List<String> ingredientLines,
      List<String> instructionLines,
      PDType1Font bodyFont,
      PDType1Font boldFont)
      throws IOException {

    float lineH = BODY_SIZE * 1.45f; // line height for body text
    float sectionHeadH = SECTION_HEADING_SIZE * 1.6f; // line height for section headings

    // How many body lines fit per page in one column
    float bodyAreaHeight = BODY_TOP - BODY_BOTTOM;
    // Reserve first two lines per column for the section heading + small gap
    float reservedForHeading = sectionHeadH + 4f;
    int linesPerPage = (int) ((bodyAreaHeight - reservedForHeading) / lineH);

    int leftTotal = ingredientLines.size();
    int rightTotal = instructionLines.size();
    int totalPages =
        Math.max(1, (int) Math.ceil(Math.max(leftTotal, rightTotal) / (double) linesPerPage));

    for (int pageIdx = 0; pageIdx < totalPages; pageIdx++) {
      PDPage page = new PDPage(PDRectangle.A4);
      doc.addPage(page);

      try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {

        // ── Header background ──────────────────────────────────────────────
        cs.setNonStrokingColor(HEADER_GREY, HEADER_GREY, HEADER_GREY);
        cs.addRect(0, PAGE_HEIGHT - HEADER_HEIGHT, PAGE_WIDTH, HEADER_HEIGHT);
        cs.fill();
        cs.setNonStrokingColor(0f, 0f, 0f); // reset to black

        // ── Centered title ─────────────────────────────────────────────────
        String title = sanitize(recipe.getTitle());
        float titleW = boldFont.getStringWidth(title) / 1000f * TITLE_SIZE;
        float titleX = (PAGE_WIDTH - titleW) / 2f;
        float titleY = PAGE_HEIGHT - HEADER_HEIGHT + 46f;
        drawText(cs, boldFont, TITLE_SIZE, titleX, titleY, title);

        // ── Centered subtitle (servings) ───────────────────────────────────
        if (recipe.getServings() != null) {
          String sub = sanitize("Serves: " + recipe.getServings());
          float subW = bodyFont.getStringWidth(sub) / 1000f * SUBTITLE_SIZE;
          float subX = (PAGE_WIDTH - subW) / 2f;
          float subY = titleY - TITLE_SIZE * 1.4f;
          // Draw in dark grey
          cs.setNonStrokingColor(0.4f, 0.4f, 0.4f);
          drawText(cs, bodyFont, SUBTITLE_SIZE, subX, subY, sub);
          cs.setNonStrokingColor(0f, 0f, 0f);
        }

        // ── Divider line below header ──────────────────────────────────────
        float dividerY = PAGE_HEIGHT - HEADER_HEIGHT - 6f;
        cs.setStrokingColor(0.7f, 0.7f, 0.7f);
        cs.setLineWidth(0.5f);
        cs.moveTo(MARGIN, dividerY);
        cs.lineTo(PAGE_WIDTH - MARGIN, dividerY);
        cs.stroke();
        cs.setStrokingColor(0f, 0f, 0f);

        // ── Section headings ───────────────────────────────────────────────
        float headingY = BODY_TOP - 2f;
        drawText(cs, boldFont, SECTION_HEADING_SIZE, LEFT_COL_X, headingY, "INGREDIENTS");
        drawText(cs, boldFont, SECTION_HEADING_SIZE, RIGHT_COL_X, headingY, "INSTRUCTIONS");

        // Thin vertical separator between columns
        float sepX = MARGIN + COL_WIDTH + COL_GAP / 2f;
        cs.setStrokingColor(0.82f, 0.82f, 0.82f);
        cs.setLineWidth(0.4f);
        cs.moveTo(sepX, headingY);
        cs.lineTo(sepX, BODY_BOTTOM);
        cs.stroke();
        cs.setStrokingColor(0f, 0f, 0f);

        // ── Body content (two columns, paginated) ──────────────────────────
        int start = pageIdx * linesPerPage;
        float bodyY = headingY - sectionHeadH;

        // Left column — ingredients
        int leftEnd = Math.min(start + linesPerPage, leftTotal);
        float y = bodyY;
        for (int i = start; i < leftEnd; i++) {
          drawText(cs, bodyFont, BODY_SIZE, LEFT_COL_X, y, ingredientLines.get(i));
          y -= lineH;
        }

        // Right column — instructions
        int rightEnd = Math.min(start + linesPerPage, rightTotal);
        y = bodyY;
        for (int i = start; i < rightEnd; i++) {
          drawText(cs, bodyFont, BODY_SIZE, RIGHT_COL_X, y, instructionLines.get(i));
          y -= lineH;
        }

        // ── Footer divider ─────────────────────────────────────────────────
        float footerDivY = MARGIN + FOOTER_HEIGHT;
        cs.setStrokingColor(0.7f, 0.7f, 0.7f);
        cs.setLineWidth(0.5f);
        cs.moveTo(MARGIN, footerDivY);
        cs.lineTo(PAGE_WIDTH - MARGIN, footerDivY);
        cs.stroke();
        cs.setStrokingColor(0f, 0f, 0f);

        // ── Footer text ────────────────────────────────────────────────────
        String footerLeft = "CookYourBooks";
        String footerRight = "Page " + (pageIdx + 1) + " of " + totalPages;
        float footerY = MARGIN + FOOTER_HEIGHT - 14f;

        cs.setNonStrokingColor(0.45f, 0.45f, 0.45f);
        drawText(cs, bodyFont, FOOTER_SIZE, MARGIN, footerY, footerLeft);

        float footerRightW = bodyFont.getStringWidth(footerRight) / 1000f * FOOTER_SIZE;
        drawText(
            cs, bodyFont, FOOTER_SIZE, PAGE_WIDTH - MARGIN - footerRightW, footerY, footerRight);
        cs.setNonStrokingColor(0f, 0f, 0f);
      }
    }
  }

  // ── Line builders ──────────────────────────────────────────────────────────

  /**
   * Converts the recipe's ingredient list into display strings, word-wrapped to fit {@link
   * #COL_WIDTH}.
   */
  private List<String> buildIngredientLines(Recipe recipe, PDType1Font font) throws IOException {
    List<String> lines = new ArrayList<>();
    for (Ingredient ing : recipe.getIngredients()) {
      for (String line : wrap("- " + sanitize(ing.toString()), font, BODY_SIZE, COL_WIDTH)) {
        lines.add(line);
      }
    }
    return lines;
  }

  /**
   * Converts the recipe's instruction list into display strings, word-wrapped to fit {@link
   * #COL_WIDTH}.
   */
  private List<String> buildInstructionLines(Recipe recipe, PDType1Font font) throws IOException {
    List<String> lines = new ArrayList<>();
    for (Instruction step : recipe.getInstructions()) {
      String prefix = step.getStepNumber() + ". ";
      String text = sanitize(step.getText());
      List<String> wrapped = wrap(prefix + text, font, BODY_SIZE, COL_WIDTH);
      // Indent continuation lines to align with the text after "N. "
      String indent = " ".repeat(prefix.length());
      for (int i = 0; i < wrapped.size(); i++) {
        lines.add(i == 0 ? wrapped.get(i) : indent + wrapped.get(i).stripLeading());
      }
    }
    return lines;
  }

  // ── Shared utilities ───────────────────────────────────────────────────────

  /**
   * Writes a single line of text at the given coordinates using the specified font and size.
   * Assumes the caller has already set the non-stroking color if needed.
   */
  private static void drawText(
      PDPageContentStream cs, PDType1Font font, float size, float x, float y, String text)
      throws IOException {
    cs.beginText();
    cs.setFont(font, size);
    cs.newLineAtOffset(x, y);
    cs.showText(text);
    cs.endText();
  }

  /**
   * Word-wraps {@code text} so each line fits within {@code maxWidth} points at the given font and
   * size. Words that individually exceed the column width are placed on their own line.
   */
  private static List<String> wrap(String text, PDType1Font font, float size, float maxWidth)
      throws IOException {
    List<String> lines = new ArrayList<>();
    String[] words = text.split(" ", -1);
    StringBuilder current = new StringBuilder();

    for (String word : words) {
      String candidate = current.isEmpty() ? word : current + " " + word;
      float width = font.getStringWidth(candidate) / 1000f * size;
      if (width > maxWidth && !current.isEmpty()) {
        lines.add(current.toString());
        current = new StringBuilder(word);
      } else {
        current = new StringBuilder(candidate);
      }
    }
    if (!current.isEmpty()) {
      lines.add(current.toString());
    }
    return lines;
  }

  /**
   * Replaces Unicode characters unsupported by PDFBox's built-in Latin-1 fonts with safe ASCII
   * equivalents, preventing {@link IllegalArgumentException} at render time.
   */
  private static String sanitize(String text) {
    return text.replace('\u2019', '\'') // right single quotation mark
        .replace('\u2018', '\'') // left single quotation mark
        .replace('\u201C', '"') // left double quotation mark
        .replace('\u201D', '"') // right double quotation mark
        .replace('\u2013', '-') // en dash
        .replace('\u2014', '-') // em dash
        .replace('\u2022', '-') // bullet point
        .replace('\u00e9', 'e') // e with accent
        .replace('\u00e8', 'e') // e with grave
        .replace('\u00ea', 'e') // e with circumflex
        .replace('\u00e0', 'a') // a with grave
        .replace('\u00ee', 'i') // i with circumflex
        .replace('\u00fc', 'u') // u with umlaut
        .replace('\u00f6', 'o'); // o with umlaut
  }
}
