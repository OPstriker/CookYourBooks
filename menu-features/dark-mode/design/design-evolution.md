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

1. **The toggle button is confusing.** The sun icon alone does not tell the user what it does. Someone using the app for the first time would not know if clicking it turns dark mode on or off.

2. **The bottom buttons look disabled.** The Delete, Open Recipe, and Export PDF buttons are so faint that they look like they cannot be clicked. This would confuse a user like Michael who needs to act quickly in the kitchen.

3. **The theme resets every time you reopen the app.** Version 1 switches the theme while the app is open, but does not save the preference. Every time you relaunch, it goes back to light mode.

4. **List items are hard to tell apart.** The unselected items in the collections list blend into the background. There is barely any visual separation between items and the panel behind them.

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

Problem 1 from V1 feedback: *"The sun icon alone does not tell the user what it does."* A user like Mary — who opens the app in a bright kitchen and wants to quickly reduce screen glare — should be able to see the button and immediately understand what it will do. Switching the icon makes the button self-explanatory without needing any extra label or tooltip.

### Problems Still Remaining After Version 2

1. **No tooltip.** Hovering over the button shows nothing. A first-time user might still not know the button is there. This is fixed in V3.
2. **The theme resets on relaunch.** The preference is still not saved between sessions.
3. **Bottom buttons still look faint.** The contrast issue from V1 is not yet addressed.
