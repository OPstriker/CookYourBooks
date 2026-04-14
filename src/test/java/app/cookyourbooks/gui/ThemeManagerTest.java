package app.cookyourbooks.gui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

/** Unit tests for {@link ThemeManager}. */
class ThemeManagerTest {

  @Test
  void isDarkMode_startsFalse() {
    // A brand-new ThemeManager should always start in light mode.
    ThemeManager themeManager = new ThemeManager();

    assertThat(themeManager.isDarkMode()).isFalse();
  }

  @Test
  void toggle_enablesDarkMode() {
    // After one toggle, dark mode should be on.
    ThemeManager themeManager = new ThemeManager();

    themeManager.toggle();

    assertThat(themeManager.isDarkMode()).isTrue();
  }

  @Test
  void toggle_twice_returnsToLightMode() {
    // Toggling twice should bring us back to light mode.
    ThemeManager themeManager = new ThemeManager();

    themeManager.toggle();
    themeManager.toggle();

    assertThat(themeManager.isDarkMode()).isFalse();
  }

  @Test
  void toggle_withNoScene_doesNotThrow() {
    // toggle() before setScene() is called must not throw a NullPointerException.
    // ThemeManager guards against this with: if (scene == null) return;
    ThemeManager themeManager = new ThemeManager();

    assertThatCode(themeManager::toggle).doesNotThrowAnyException();
  }
}
