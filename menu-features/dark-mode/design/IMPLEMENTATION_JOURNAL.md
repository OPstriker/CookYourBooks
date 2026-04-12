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
**Link:** *(to be added after the PR is merged)*

**Review comment to expect:** The reviewer asked why we used `.root`-prefixed selectors in the
dark CSS instead of just removing the light stylesheet and adding the dark one. The answer is
in the Decision Log below.

---

## Decision Log

### Decision: Use `.root`-prefixed selectors in the dark CSS

When we first added the dark CSS to the Scene's stylesheet list, the dark colors did not show up.
The light colors from the FXML were still winning.

This happened because each FXML file loads the light CSS directly onto its root node. In JavaFX,
node-level stylesheets have higher priority than Scene-level stylesheets when the specificity
is the same. So adding the dark CSS to the Scene was not enough — the light CSS on the node
kept overriding it.

We fixed this by adding `.root` in front of every selector in the dark CSS file. For example,
instead of `.library-list` we wrote `.root .library-list`. This makes the dark selector more
specific (two classes instead of one), so it wins over the light CSS even though the light CSS
is on the node.

We also considered a different approach: remove the light CSS from FXML completely and manage
all stylesheets in Java code. This would also work, but it would mean changing all six FXML files
and rewriting how the app loads its CSS from the start. We decided against this because it was
more work and harder to undo. The `.root` prefix approach only required changes to the dark CSS
file itself.
