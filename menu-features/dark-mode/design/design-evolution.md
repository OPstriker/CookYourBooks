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

## Version 2: Planned Improvements

*(In progress — screenshot to be added)*

Based on the problems found in Version 1, these are the changes we plan to make:

- Add a tooltip to the toggle button so users know what it does before clicking
- Increase the contrast on the bottom action buttons so they look clickable
- Save the theme preference to a settings file so it persists between launches
- Add a subtle card background to list items so they are easier to tell apart
