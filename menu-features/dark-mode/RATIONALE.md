# Dark Mode — Design Rationale

## Why We Chose This Feature
We chose Dark Mode because it addresses a real usability gap that multiple GA0 personas would immediately benefit from. CookYourBooks is used during active cooking sessions in kitchens with bright overhead lights where high-contrast white screens cause glare and late at night when users are planning meals or reviewing recipes before bed. The current all-white UI is jarring in low-light conditions and offers no accommodation for users who prefer reduced brightness for comfort or accessibility reasons.

Dark Mode also has a well-understood implementation path in JavaFX (CSS stylesheet swapping) that fit our team's remaining capacity after GA1, making it a realistic scope for the remaining sprint while still delivering genuine user value.

## User Need
Two of our GA0 personas are directly affected:

Michael: (Cook, tech comfort 2/5) uses CookYourBooks in cook mode during active meal preparation.
Kitchen environments vary bright overhead task lighting reflects off screens, making the white UI hard to read at arm's length. A dark theme reduces glare and improves legibility in high-ambient-light kitchen settings without requiring Michael to repeatedly adjust his device's brightness.

Mary: (College student, tech comfort 3/5) uses the app after coming home from class or work, often in the evening when planning meals. Late-night use on a bright white screen contributes to eye strain and disrupts sleep. A system-preference-aware dark mode means Mary's CookYourBooks experience automatically matches the rest of her device environment without requiring manual reconfiguration each time.

## Alternatives Considered

**1. High-contrast mode only**
One option we looked at was adding a high-contrast mode instead of a full dark theme. We decided not to go with this because high contrast still has a white background. It does not make the screen dimmer, so it would not help Michael with kitchen glare or Mary using the app at night.

**2. Adding dark colors directly into each FXML file**
We also thought about just changing the background colors inside each FXML file using inline styles.
The problem is that we have six FXML files, so we would end up writing the same colors over and over. If we ever wanted to change a shade, we would have to go back and update every single file.
Keeping all the colors in one stylesheet is much easier to manage.

**3. A full theming system with many color options**
We considered building something bigger and a system where users could pick from multiple themes, not just light and dark. We decided this was too much for the time we had left before April 16, and our users really only need a simple toggle. We kept this in mind as something that could be added in the future.

**What we chose:** We are building a ThemeManager class that swaps between two CSS files. One for light mode and one for dark mode when the user clicks the toggle. It also remembers the user's choice for next time and checks the system's dark mode setting on the first launch.

---

## Version-by-Version Design Decisions

### Version 1 — Core Toggle

**Goal:** Get the dark theme working at all. The minimum useful version is one where the user can click a button and the whole app switches to a dark color scheme.

**What we built:**
- `cookyourbooks-dark.css` — a single CSS file containing dark color overrides for every view (Library, Recipe Editor, Import, Search, Home). All selectors are prefixed with `.root` so they have higher specificity than the light CSS loaded by each FXML file. This means adding the dark CSS to the Scene is enough — we never need to touch the FXML files.
- `ThemeManager.java` — a dedicated class that owns the toggle logic. It holds a `BooleanProperty` for the current mode and calls `scene.getStylesheets().add()` or `.remove()` on toggle. Keeping this logic in its own class means the controller does not need to know anything about how stylesheets work.
- Toggle button wired in `MainViewController` — a single ☀ button in the top bar calls `ThemeManager.toggle()` on click.

**Key decision — `.root`-prefixed selectors:**
When we first added the dark CSS to the Scene, the dark colors did not show up. Each FXML loads the light CSS directly onto its root node, and node-level stylesheets win over Scene-level stylesheets at equal specificity. Prefixing every dark selector with `.root` raises the specificity from one class (010) to two classes (020), so the dark rules win without any FXML changes.

**What V1 left unfinished:**
- The toggle button always showed ☀ regardless of the current mode. There was no visual feedback showing which mode was active.
- No tooltip — new users had no way to know what the button did without clicking it.
- The theme reset on every relaunch. The user's preference was not saved anywhere.

---

### Version 2 — Icon Feedback

**Goal:** Fix the most confusing part of V1. The button icon should always show the user what will happen next, not just sit there as a static symbol.

**What we built:**
- After every `ThemeManager.toggle()` call, `MainViewController` immediately calls `darkModeButton.setText(themeManager.isDarkMode() ? "☀" : "🌙")`.
- In light mode the button shows 🌙 (clicking will go dark). In dark mode the button shows ☀ (clicking will go back to light). The icon communicates the next action, not the current state.

**Key decision — update the icon in Java, not in FXML:**
We could have set the initial icon in `MainView.fxml` and then updated it in Java on every click. We chose to manage the icon entirely in Java so the icon is never defined in two places. If the default mode ever changes, there is only one place to update. Splitting the responsibility between FXML and Java would make it easy for them to get out of sync.

**What V2 left unfinished:**
- A first-time user hovering over the button still got no explanation. The switching icon helps, but a plain-English label would be clearer.
- The theme still reset on every relaunch.

---

### Version 3 — Tooltip and Persistence

**Goal:** Address the two remaining gaps from V1 and V2 — no hover explanation, and no memory between launches.

**What we built (tooltip):**
- `darkModeButton.setTooltip(new Tooltip("Switch to dark mode"))` is called in `initialize()`.
- After every toggle, the tooltip text is also updated: `"Switch to light mode"` when dark mode is on, `"Switch to dark mode"` when light mode is on.
- A dynamic tooltip is more useful than a static one. A label that always says "Toggle dark mode" tells the user nothing about which direction the toggle will go. A label that says "Switch to light mode" tells the user exactly what will happen before they click.

**What we built (persistence):**
- `ThemeManager` now uses `java.util.prefs.Preferences` — a built-in Java API that reads and writes to the OS user preferences store. No new dependencies, no extra files.
- The `isDarkMode` property is initialized from the saved preference in the constructor, so the correct mode is known before the Scene even exists.
- `setScene()` applies the dark CSS immediately if the saved preference was dark, so the app starts in the right mode without a visible flash.
- `toggle()` calls `PREFS.putBoolean(DARK_MODE_KEY, isDarkMode.get())` on every change so the preference is always kept in sync.
- `MainViewController.initialize()` syncs the button icon and tooltip text with the saved preference on startup, so the button always matches the actual mode even before the user clicks anything.

**Color blindness accessibility fix (added alongside V3):**
Selected items in the library lists are highlighted in blue. Blue alone is not visible to users with red-green or blue-yellow color blindness. We added a white 3 px left border to every selected cell in dark mode so the selection state is communicated by shape and position, not just by color. This fix is in `cookyourbooks-dark.css` and required no changes to Java code.

**What V3 left unfinished:**
- The tooltip is not reachable by keyboard. JavaFX tooltips do not receive focus, so users who navigate entirely by keyboard cannot read the tooltip text without a mouse.
- The bottom action buttons (Delete, Open Recipe, Export PDF) have low contrast in dark mode and look faint even when active.
