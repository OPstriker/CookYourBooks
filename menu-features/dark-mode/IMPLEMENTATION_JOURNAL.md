# Dark Mode — Implementation Journal

## Git History (Version 1)

These are the commits made for Version 1, in the order they were added.

| Commit | What it added |
|---|---|
| `0519a09` | Added `cookyourbooks-dark.css` with dark colors for all views |
| `b247516` | Added `ThemeManager.java` to handle adding and removing the dark CSS |
| `3391dda` | Wired the toggle button in `MainViewController` to call `ThemeManager.toggle()` |
| `462a01b` | Created `ThemeManager` in `CookYourBooksGuiApp` and called `setScene()` after the Scene was built |
| `3a89487` | Committed `ThemeManager.java` after it was lost to the linter |
| `254f53f` | Fixed a comment that the formatter moved to the wrong field |

---

## PR History

### Version 1 PR
**Link:** https://github.com/neu-cs3100/sp26-hw-cyb12-group-4633/pull/2

**Review comment to expect:** The reviewer asked why we used `.root`-prefixed selectors in the dark CSS instead of just removing the light stylesheet and adding the dark one. The answer is in the Decision Log below.

---

## Decision Log

### Decision: Use `.root`-prefixed selectors in the dark CSS

When we first added the dark CSS to the Scene's stylesheet list, the dark colors did not show up. The light colors from the FXML were still winning.

This happened because each FXML file loads the light CSS directly onto its root node. In JavaFX, node-level stylesheets have higher priority than Scene-level stylesheets when the specificity is the same. So adding the dark CSS to the Scene was not enough and the light CSS on the node
kept overriding it.

We fixed this by adding `.root` in front of every selector in the dark CSS file. For example, instead of `.library-list` we wrote `.root .library-list`. This makes the dark selector more specific (two classes instead of one), so it wins over the light CSS even though the light CSS is on the node.

We also considered a different approach: remove the light CSS from FXML completely and manage all stylesheets in Java code. This would also work, but it would mean changing all six FXML files and rewriting how the app loads its CSS from the start. We decided against this because it was more work and harder to undo. The `.root` prefix approach only required changes to the dark CSS file itself.

---

## Accessibility Check (Version 1)

The dark mode toggle button is reachable by keyboard. Pressing Tab moves focus to the button in the top bar, and pressing Space activates it. This means a user can switch between light and dark mode without using a mouse.

The button does not have a tooltip or screen reader label yet. A user relying on a screen reader would hear the button text ("☀") but would not know what it does. This is fixed in Version 3, where we add a Tooltip that describes the button's action.

---

## Known Limitations (Version 1)

- The button icon stays ☀ in both light and dark mode. There is no visual feedback to tell the user which mode is currently active. This is fixed in Version 2.
- There is no tooltip on the button. Users have to click it to find out what it does. This is fixed in Version 3.
- The theme resets every time the app is restarted. The preference is not saved anywhere, so users have to toggle dark mode again on every launch. Persistence is not yet implemented.


---

## Git History (Version 2)

These are the commits made for Version 2, in the order they were added.

| Commit | What it added |
|---|---|
| `8848c40` | Documented V2 design in `design-evolution.md` — icon switches between 🌙 and ☀ |
| `f8f0afe` | Updated Design Evolution and Design Rationale for V2 |
| `e1fe4ae` | Added V2 screenshot to the design folder |
| `6b28698` | Added `darkModeButton.setText(...)` in `MainViewController` so the icon updates on every toggle |
| `faef4bf` | Started V2 Implementation Journal |

---

## PR History

### Version 2 PR
**Link:** *(to be added after the PR is merged)*

---

## Decision Log (Version 2)

### Decision: Update the button text in Java code, not in FXML

We considered two ways to make the icon switch on click. The first option was to set the initial icon in `MainView.fxml` and then update it in Java every time the button is clicked. The second option was to set the initial icon and all updates entirely in Java code.

We chose to handle everything in Java. The button starts as 🌙 by default (light mode is the default state), and after every toggle we immediately call `darkModeButton.setText(...)` based on the current `isDarkMode()` value. We did not put the initial icon in FXML because it would mean the icon is defined in two places — once in the FXML and once in the Java code — which makes it easy for them to get out of sync if the default mode ever changes.

---

## Accessibility Check (Version 2)

The icon change does not break keyboard accessibility. The button is still reachable by Tab and activated by Space. The icon itself (🌙 or ☀) is a Unicode character, so a screen reader will read it out loud. However, reading "sun" or "crescent moon" by itself still does not clearly explain what the button does. This is addressed in Version 3 by adding a Tooltip with a plain-English description.

---

## Known Limitations (Version 2)

- There is no tooltip on the button. The switching icon helps, but a first-time user hovering over the button still gets no explanation. This is fixed in Version 3.
- The theme still resets every time the app is restarted. The preference is not saved between launches. Persistence is not yet implemented.
- The bottom action buttons (Delete, Open Recipe, Export PDF) still look faint in dark mode. The contrast issue from V1 has not been addressed.






