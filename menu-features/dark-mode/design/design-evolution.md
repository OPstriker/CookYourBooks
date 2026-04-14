# Dark Mode — Design Evolution

## Version 1: First Dark Theme

**Screenshot:** `v1-dark-mode.png`

![V1 Dark Mode](v1-dark-mode.png)

Version 1 was our first attempt at applying a dark theme across the whole app. We replaced the white background with a dark navy color and updated the text to be light so it would still be readable. Here is what the first version looked like:

- The background across all panels is a dark navy/charcoal color
- The selected collection (Collection 2) is highlighted with a blue bar
- The top bar has a sun icon button to toggle between light and dark mode
- The filter field and both panels use the same dark background color
- The bottom buttons (New Collection, Delete, Open Recipe, Export PDF) are visible but look faint

### Problems We Noticed in Version 1

After looking at V1 and clicking through the app, we found four issues:

1. The toggle button is confusing: The sun icon alone does not tell the user what it does. Someone using the app for the first time would not know if clicking it turns dark mode on or off.

2. The bottom buttons look disabled: The Delete, Open Recipe, and Export PDF buttons are so faint that they look like they cannot be clicked. This would confuse a user like Michael who needs to act quickly in the kitchen.

3. The theme resets every time you reopen the app: Version 1 switches the theme while the app is open, but does not save the preference. Every time you relaunch, it goes back to light mode.

4. List items are hard to tell apart: The unselected items in the collections list blend into the background. There is barely any visual separation between items and the panel behind them.

---

## Version 2: Icon Switches on Toggle

**Screenshot:** `v2-dark-mode.png`

![V2 Dark Mode](v2-dark-mode.png)

Version 2 fixes the most confusing part of V1: the toggle button. In V1, the button always showed ☀ whether the app was in light mode or dark mode. There was no way to tell at a glance which mode you were in. Version 2 makes the icon switch every time you click:

- When the app is in **light mode**, the button shows **🌙** — it tells you "click here to go dark"
- When the app is in **dark mode**, the button shows **☀** — it tells you "click here to go light"

This was a one-line code change in `MainViewController.java`. After calling `themeManager.toggle()`, the button text is updated immediately:

```java
darkModeButton.setText(themeManager.isDarkMode() ? "☀" : "🌙");
```

### What Prompted This Change

**(Problem 1) The toggle button is confusing.** The sun icon alone does not tell the user what it does. Making the icon switch between 🌙 and ☀ on every click directly fixes this and the button now shows its next action before the user clicks it. A user like Mary who opens the app for the first time can see 🌙 and immediately understand that clicking it will switch to dark mode.

Problems 2, 3, and 4 (faint buttons, theme resetting, list item contrast) are documented as remaining limitations below. Each version focuses on one fix so each PR stays small and easy to review.

### Problems Still Remaining After Version 2

- **(Problem 1 — partially)** No tooltip yet. Hovering over the button still shows nothing. Fixed in V3.
- **(Problem 2)** Bottom buttons still look faint. The contrast issue has not been addressed.
- **(Problem 3)** The theme resets on relaunch. The preference is still not saved between sessions.
- **(Problem 4)** List items are still hard to tell apart. No card background has been added.

---

## Version 3: Tooltip Added to Toggle Button

**Screenshot:** `v3-dark-mode.png`

![V3 Dark Mode](v3-dark-mode.png)

Version 3 adds a Tooltip to the dark mode button. In V2, the switching icon made the button clearer, but a user hovering over it still got no explanation. Now when you hover over the button, a small label appears that tells you exactly what will happen if you click:

- Hovering in light mode shows: "Switch to dark mode"
- Hovering in dark mode shows: "Switch to light mode"

The tooltip text updates on every click so it always matches the current state. This was done in two lines in `MainViewController.java` — one to create the tooltip on startup, and one to update its text after each toggle:

```java
darkModeButton.setTooltip(new Tooltip("Switch to dark mode"));
```

```java
darkModeButton.getTooltip().setText(themeManager.isDarkMode() ? "Switch to light mode" : "Switch to dark mode");
```

### What Prompted This Change

Problem 1 from V2 feedback: *"Hovering over the button shows nothing."* The icon switch helped, but a first-time user still had no confirmation of what the button does until they clicked it. A Tooltip gives that confirmation passively and no click required. This is especially helpful for a user like Mary who is new to the app and cautious about clicking buttons she does not fully understand.

### What Changed From V2 to V3

| | V2 | V3 |
|---|---|---|
| Button icon | Switches 🌙 ↔ ☀ | Same |
| Hover tooltip | None | Shows current action |
| Screen reader support | Reads icon character | Icon + tooltip text available |

### Problems Still Remaining After Version 3

1. **The theme resets on relaunch.** The preference is still not saved between sessions. Persistence would require writing to a local settings file and reading it on startup.
2. **Bottom buttons still look faint.** The contrast on Delete, Open Recipe, and Export PDF has not been addressed.
