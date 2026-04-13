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


## Git History (Version 2)

These are the commits made for Version 2.

| Commit | What it added |
|---|---|
| `8848c40` | Documented V2 design in `design-evolution.md` — icon switches between 🌙 and ☀ |
| *(pending)* | Added `darkModeButton.setText(...)` line in `MainViewController` so the icon updates on toggle |


### Version 2 PR
**Link:** *(to be added after the PR is merged)*






