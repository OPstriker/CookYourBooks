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



