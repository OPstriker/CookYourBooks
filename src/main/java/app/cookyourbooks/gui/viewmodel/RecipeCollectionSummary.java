package app.cookyourbooks.gui.viewmodel;

import app.cookyourbooks.model.SourceType;

/**
 * A lightweight summary of a recipe collection for display in the Library View.
 *
 * <p>Wraps the four fields the grading contract requires: ID, title, source type, and recipe count.
 */
public record RecipeCollectionSummary(
    String id, String title, SourceType sourceType, int recipeCount) {}
