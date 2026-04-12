package app.cookyourbooks.gui;

import java.util.Objects;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Scene;

import org.jspecify.annotations.Nullable;

/**
 * Manages light/dark theme switching for the CookYourBooks application.
 *
 * <p>Swaps the dark CSS stylesheet in and out of the {@link Scene}'s stylesheet list. The light
 * theme is always present (loaded via FXML). Enabling dark mode adds {@code
 * cookyourbooks-dark.css} on top — its higher-specificity {@code .root}-prefixed selectors
 * override the light rules without any FXML changes.
 *
 * <h2>Usage</h2>
 *
 * <ol>
 *   <li>Create a {@code ThemeManager} and pass it to {@link
 *       app.cookyourbooks.gui.view.MainViewController#setThemeManager}
 *   <li>After the {@code Scene} is created, call {@link #setScene}
 *   <li>The toggle button in {@code MainViewController} calls {@link #toggle}
 * </ol>
 */
public class ThemeManager {

  private static final String DARK_CSS =
      Objects.requireNonNull(
              ThemeManager.class.getResource("/css/cookyourbooks-dark.css"),
              "cookyourbooks-dark.css not found on classpath")
          .toExternalForm();

  private final BooleanProperty isDarkMode = new SimpleBooleanProperty(false);

  @Nullable private Scene scene;

  /**
   * Provides the {@link Scene} whose stylesheet list this manager controls.
   *
   * <p>Must be called after the Scene is created in {@code CookYourBooksGuiApp.start()}.
   *
   * @param scene the application scene
   */
  public void setScene(Scene scene) {
    this.scene = scene;
  }

  /**
   * Toggles between light and dark mode.
   *
   * <p>Adds {@code cookyourbooks-dark.css} to the Scene's stylesheet list when switching to dark,
   * and removes it when switching back to light.
   */
  public void toggle() {
    isDarkMode.set(!isDarkMode.get());
    if (scene == null) {
      return;
    }
    if (isDarkMode.get()) {
      scene.getStylesheets().add(DARK_CSS);
    } else {
      scene.getStylesheets().remove(DARK_CSS);
    }
  }

  /**
   * Returns the observable dark-mode flag.
   *
   * @return the {@code isDarkMode} property
   */
  public BooleanProperty isDarkModeProperty() {
    return isDarkMode;
  }

  /**
   * Returns whether dark mode is currently active.
   *
   * @return {@code true} if dark mode is on
   */
  public boolean isDarkMode() {
    return isDarkMode.get();
  }
}
