# Android Launcher: Design and Build Spec

A single-file handoff for a coding agent (Codex or similar). Read the whole file, then build the milestones in section 12 in order. Do not skip ahead, and ask nothing that this file already answers.

Visual reference (clickable prototype and static screens): https://claude.ai/artifact/KriRS1Vkbcps9jbXNUHHse
Design frame: 390 × 844 dp portrait phone. All numbers below are dp (sizes) or sp (text).

---

## 1. Goal

Build a real Android home-screen launcher (it replaces the device's default home app). It has four horizontally swipeable pages and a full-screen app drawer:

| Index | Page | Contents |
|---|---|---|
| 0 | Feed | Markets card (3 indices) and Top stories list |
| 1 | Home (default page) | Clock and date, 4×2 app grid, search pill, dock |
| 2 | Today | Agenda card, quick-settings toggles, music card |
| 3 | Tools | Tasks, favourite contacts, quick note |

The App drawer is an overlay opened from Home.

Look and feel: calm, dark, tonal. Flat deep teal-black ground, soft tonal circles instead of a wallpaper gradient, rounded tiles, line icons in four accent colors, Manrope typeface. No emoji, no gradient washes, no left-border cards.

## 2. Tech stack (decided, do not re-litigate)

- Kotlin, Jetpack Compose with Material 3 (used as a base only; every color and shape comes from the tokens below, not from Material defaults).
- minSdk 26, targetSdk 35, single module `app`, package `com.example.launcher` (rename freely).
- Single `MainActivity` as the HOME activity, `launchMode="singleTask"`.
- State: `ViewModel` plus `StateFlow`, one `LauncherUiState`. Persistence: Room (tasks, quick notes, pinned apps) and DataStore (settings).
- Fonts: bundle Manrope (weights 300, 500, 700) in `res/font`. Do not fetch fonts at runtime.
- No network libraries until milestone 6. Feed data comes from a repository interface with a fake implementation until then.

### Manifest requirements

```xml
<activity android:name=".MainActivity" android:exported="true"
    android:launchMode="singleTask" android:stateNotNeeded="true"
    android:theme="@style/Theme.Launcher">
    <intent-filter>
        <action android:name="android.intent.action.MAIN"/>
        <category android:name="android.intent.category.HOME"/>
        <category android:name="android.intent.category.DEFAULT"/>
        <category android:name="android.intent.category.LAUNCHER"/>
    </intent-filter>
</activity>

<queries>
    <intent><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.LAUNCHER"/></intent>
    <intent><action android:name="android.intent.action.DIAL"/></intent>
    <intent><action android:name="android.intent.action.SENDTO"/><data android:scheme="smsto"/></intent>
    <intent><action android:name="android.media.action.IMAGE_CAPTURE"/></intent>
    <intent><action android:name="android.intent.action.VIEW"/><data android:scheme="https"/></intent>
</queries>
```

Use the `<queries>` block to list launchable apps. Do not request `QUERY_ALL_PACKAGES`.

## 3. Design tokens

### Colors

| Token | Hex | Use |
|---|---|---|
| `ground` | `#0E1A19` | Screen background |
| `decorA` | `#132423` | Large decorative circle, top right |
| `decorB` | `#112120` | Decorative circle, lower left (Home) |
| `surface` | `#1A2A28` | Cards, app tiles, search pill, dock |
| `surfaceRaised` | `#243836` | Dock tiles, inactive toggles, thumbnails, music art |
| `text` | `#F1F5F2` | Primary text |
| `muted` | `#B4C3BE` | Secondary text, captions, app labels |
| `dotInactive` / `handle` | `#5E7873` | Inactive page dots, drawer handle |
| `amber` | `#F2B84B` | Primary accent, section labels, active states, primary buttons |
| `mint` | `#7FD6B8` | Accent, "up" market moves |
| `coral` | `#F08A6B` | Accent, "down" market moves |
| `sky` | `#8EC5F0` | Accent |
| `onAccent` | `#0E1A19` | Text and icons on amber/mint/coral/sky fills |

Contrast rules: body text must stay at 4.5:1 or better on its surface. Never convey meaning by color alone (market moves also carry an arrow icon and a +/− sign; toggles also expose pressed state).

### Typography (Manrope)

| Style | Size / weight | Extra |
|---|---|---|
| `clock` | 104sp / 300 | line-height 1.0, letter-spacing −4sp |
| `pageTitle` | 34sp / 300 | letter-spacing −1sp |
| `subtitle` | 15sp / 500 | color `muted` |
| `homeDate` | 17sp / 500 | color `muted` |
| `body` | 15sp / 500 (700 for titles and emphasis) | |
| `sectionLabel` | 11sp / 700 | uppercase, letter-spacing 1.5sp, color `amber` |
| `caption` | 12sp / 500 | color `muted` |
| `appLabel` | 12sp / 500 | color `muted` |
| `drawerLabel` | 13sp / 700 | uppercase, letter-spacing 1.5sp, color `muted` |

### Shapes and sizes

| Element | Spec |
|---|---|
| App tile | 58 × 58, radius 20, fill `surface` (dock: `surfaceRaised`), icon 26, stroke 1.7 |
| Widget card | radius 28, padding 20, fill `surface` |
| Story card | radius 24, padding 14, thumbnail 76 × 76 radius 16 |
| Search pill | height 52, radius 26, horizontal padding 20, icon 20 |
| Dock | radius 34, padding 12, fill `surface`, four tiles spaced evenly |
| Toggle | height 64, radius 22, horizontal padding 18, icon 22, two-column grid, gap 10 |
| Music art | 56 × 56 radius 18; play button 48 circle amber; prev/next hit area 44 × 44 |
| Contact | 56 circle, initial 20sp / 700 in `onAccent` |
| Page dots | hit area 30 × 44, dot height 8, inactive width 8, active width 22 (animate width 250 ms) |
| Touch targets | Minimum 44 × 44 everywhere |

### Spacing

- Page padding: top 76, bottom 24–28, sides 20 (24 on Home and Drawer).
- Gap between cards on a page: 14. Between story cards: 10.
- App grid: 4 equal columns, row gap 28.
- Page dots sit 18 from the top, centered, above page content.

### Icons

Line icons on a 24 × 24 grid, stroke 1.7 (2 for small UI glyphs), round caps and joins, no fill. Import these as `ImageVector` using `PathParser`, or convert to vector drawables.

```
phone      M5 4h4l2 5-2.5 1.5a11 11 0 0 0 6 6L16 14l5 2v3a2 2 0 0 1-2 2A16 16 0 0 1 3 6a2 2 0 0 1 2-2z
messages   M4 5h16v11H9l-5 4z
camera     M3 8a2 2 0 0 1 2-2h2l1.5-2h7L17 6h2a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2zM12 17a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7z
browser    M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM15.5 8.5l-2 5-5 2 2-5z
photos     M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2zM3 17l5-5 4 4 3-3 6 6
music      M9 18V5l11-2v13M9 18a3 3 0 1 1-6 0 3 3 0 0 1 6 0zM20 16a3 3 0 1 1-6 0 3 3 0 0 1 6 0z
maps       M12 21s7-6 7-11a7 7 0 0 0-14 0c0 5 7 11 7 11zM12 12.5a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5z
mail       M5 5h14a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2zM3 7l9 6 9-6
calendar   M6 4h12a2 2 0 0 1 2 2v13a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2zM4 10h16M9 2v4M15 2v4
clock      M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM12 7v5l3 2
notes      M6 3h9l4 4v14H6zM15 3v4h4M9 12h7M9 16h5
settings   M4 7h9M17 7h3M4 17h3M11 17h9M15 5a2 2 0 1 0 0 4 2 2 0 0 0 0-4zM9 15a2 2 0 1 0 0 4 2 2 0 0 0 0-4z
files      M3 6a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z
wallet     M3 7a2 2 0 0 1 2-2h13v4M3 7v11a2 2 0 0 0 2 2h14V9H5a2 2 0 0 1-2-2zM16 14.5h.01
search     M11 18a7 7 0 1 0 0-14 7 7 0 0 0 0 14zM20 20l-4-4
wifi       M2 9a15 15 0 0 1 20 0M5.5 12.5a10 10 0 0 1 13 0M9 16a5 5 0 0 1 6 0M12 19.5h.01
bluetooth  M7 8l10 8-5 4V4l5 4-10 8
flashlight M8 3h8v5l-2 3v10h-4V11L8 8zM8 6h8
bell       M6 17v-6a6 6 0 0 1 12 0v6l2 2H4zM10 21h4
mic        M12 15a3 3 0 0 0 3-3V6a3 3 0 0 0-6 0v6a3 3 0 0 0 3 3zM6 11a6 6 0 0 0 12 0M12 17v4
plus       M12 5v14M5 12h14
chevronUp  M6 15l6-6 6 6
arrowUp    M12 19V5M6 11l6-6 6 6
arrowDown  M12 5v14M6 13l6 6 6-6
prev       M19 5v14L9 12zM6 5v14
next       M5 5v14l10-7zM18 5v14
play       M8 5v14l11-7z      (filled with onAccent)
pause      M8 5v14M16 5v14
```

In production, app icons come from `PackageManager` (adaptive icons). The accent-colored line icons above are the design's stand-ins and are used for the system shortcuts (Phone, Messages, Camera, Browser) only when the real icon is unavailable. Keep real icons inside the same 58 × 58 radius-20 tile.

## 4. Architecture

```
app/src/main/java/.../
  MainActivity.kt            single Activity, sets Compose content, edge-to-edge
  ui/theme/                  Color.kt, Type.kt, Shapes.kt, LauncherTheme.kt
  ui/components/             AppTile, WidgetCard, SectionLabel, PageDots, Toast, SearchPill
  ui/pages/                  FeedPage, HomePage, TodayPage, ToolsPage
  ui/drawer/                 AppDrawer
  data/apps/                 InstalledAppsRepository (PackageManager)
  data/feed/                 FeedRepository (interface), FakeFeedRepository
  data/db/                   Room: TaskEntity, NoteEntity, PinnedAppEntity
  data/system/               TorchController, MediaSessionTracker, CalendarReader
  LauncherViewModel.kt       exposes LauncherUiState, handles intents/events
```

- Use `HorizontalPager` (4 pages, `initialPage = 1`). The drawer is a full-screen overlay above the pager (`AnimatedVisibility` slide-up plus fade), not a page.
- Handle the system back gesture: back closes the drawer if open; otherwise returns to Home; on Home it does nothing (launchers never exit).
- Draw edge-to-edge. Keep content clear of the status and navigation bars using window insets (the 76 dp top padding already includes room for the page dots; add the status-bar inset if it is taller).

## 5. Screens

### 5.1 Home (page 1)

Vertical column, padding top 76 / sides 24 / bottom 24:

1. Clock block: `clock` text with the current time (24-hour, `HH:mm`, device locale rules), `homeDate` below with a 12 dp gap, format like "Friday, 23 October".
2. Flexible spacer.
3. App grid: 8 apps, 4 columns, row gap 28. Default: Photos, Music, Maps, Mail, Calendar, Clock, Notes, Settings (resolve to the user's installed equivalents; user can long-press to rearrange in a later milestone). Each cell is a column: tile (58 dp) then 8 dp gap then `appLabel`.
4. Chevron-up button, 64 × 44, centered, 8 dp above the search pill. Opens the drawer.
5. Search pill, full width, 8 dp below the chevron: search icon plus the text "Search apps, web, contacts". Tapping opens the drawer with the search field focused.
6. Dock, 16 dp below the search pill: Phone, Messages, Camera, Browser (default handlers for DIAL, SENDTO smsto, IMAGE_CAPTURE and VIEW https). Icon-only, each with a content description.

Decorative circles: 300 dp `decorA` partially off-screen top right (right −100, top −80); 200 dp `decorB` at left −70, top 400. Mark them as decorative (no semantics).

### 5.2 App drawer (overlay)

Opaque `ground` background, padding top 64 / sides 24 / bottom 8.

1. Search field: height 52, radius 26, `surface`, search icon, single-line text field with placeholder "Search apps". Focus and show the keyboard when opened from the search pill; do not auto-focus when opened from the chevron or a swipe.
2. `drawerLabel` "All apps" with 28 dp above and 20 dp below.
3. Scrollable 4-column grid of all launchable apps, alphabetical by label, same cell as Home. Grid scrolls; the rest stays fixed.
4. Empty state: centered `body` muted text "No apps match your search".
5. Bottom handle: a 120 × 5 dp pill in `handle` color inside a 160 × 44 dp button with the label "Close app drawer".

Filtering: case-insensitive substring match on the app label, updating on each keystroke. Launching an app from the drawer closes the drawer and clears the query.

Decorative circle: 320 dp `decorA` at right −110, bottom −90.

### 5.3 Feed (page 0)

Column, padding top 76 / sides 20 / bottom 28, gap 14.

1. Header: `pageTitle` "For you", `subtitle` "Markets and top stories".
2. Markets card (`WidgetCard`): `sectionLabel` "MARKETS". Three rows of height 60, default symbols NIFTY 50, SENSEX, S&P 500 (user-editable later). Each row, left to right: name (`body` 700) with `caption` "Index" under it; a 56 × 24 sparkline (stroke 2); right-aligned column (min width 88) with price (`body` 700) over a change line (12sp / 700, arrow icon 12 dp plus signed percent, e.g. "+0.00%" / "−0.00%"). Up moves use `mint` with `arrowUp`; down moves use `coral` with `arrowDown`.
3. `sectionLabel` "TOP STORIES" with 4 dp inset.
4. Story cards (three, 10 dp apart): row with a 76 dp thumbnail, then a column of category tag (11sp / 700, accent colored: TECH sky, BUSINESS mint, WORLD coral), headline (`body` 700, line-height 1.3, max 2 lines, ellipsize), and `caption` "source · time". The whole card is one button that opens the article in the browser (Custom Tabs).

The design uses clearly marked placeholders ("[PRICE]", "[SOURCE] · [TIME]", "Top story headline goes here…"). Never ship invented numbers or headlines: until milestone 6, show these placeholders (or an obvious "No data yet" state), not made-up values.

### 5.4 Today (page 2)

Column, padding top 76 / sides 20 / bottom 28, gap 14.

1. Header: `pageTitle` "Today", `subtitle` is the current date.
2. Agenda card: `sectionLabel` "UP NEXT"; up to three rows of height 52: a 4 × 38 dp rounded bar in the event's accent color, then event title (`body` 700) and `caption` time range ("10:30 – 11:00"). Empty state: "Nothing else today".
3. Quick settings: 2-column grid of four toggle buttons: Wi‑Fi, Bluetooth, Flashlight, Silent. On state: fill `amber`, text and icon `onAccent`. Off state: fill `surfaceRaised`, text and icon `text`. Each exposes a toggle/pressed accessibility state.
4. Music card: art tile (music icon, `mint`), title (`body` 700) and `caption` status line ("Now playing" or "Paused"), then Previous (44), Play/Pause (48, amber circle), Next (44).

### 5.5 Tools (page 3)

Column, padding top 76 / sides 20 / bottom 28, gap 14.

1. Header: `pageTitle` "Tools", `subtitle` "Tasks, people and quick notes".
2. Tasks card: `sectionLabel` "TASKS"; rows of height 48 with a 22 dp checkbox (accent `amber`) and label. Checked rows use `muted` text with strikethrough. Persist to Room. Whole row is the touch target.
3. Favourites card: `sectionLabel` "FAVOURITES"; up to four contacts spaced evenly: 56 dp circle with the initial on a rotating accent (mint, coral, sky, amber), name below in `caption`. Tapping starts a call (see 7).
4. Quick note card: `sectionLabel` "QUICK NOTE", multiline text field (about 64 dp tall, placeholder "Jot something down…"), then right-aligned Voice (48, `surfaceRaised`, mic icon) and Save (48, amber, plus icon) buttons. Save writes to Room and clears the field.

### 5.6 Page dots and toast

- Dots: four buttons, labels "Feed page", "Home page", "Today page", "Tools page". Active dot is a 22 × 8 amber pill; inactive 8 × 8 `dotInactive`. Tapping animates the pager to that page.
- Toast: a pill, fill `text`, text `onAccent`, 13sp / 700, 10 dp vertical and 18 dp horizontal padding, radius 20, centered, 132 dp above the bottom. Visible for 1.4 s. Announce it as a polite live region. Used for confirmations such as "Note saved" and "Flashlight on".

## 6. Interaction rules

| Gesture / action | Result |
|---|---|
| Horizontal swipe ≥ 60 dp (mostly horizontal) | Move one page, left swipe = next, right swipe = previous. Page index clamps 0–3 |
| Vertical swipe up ≥ 80 dp on Home only | Open drawer |
| Tap chevron or search pill on Home | Open drawer (search pill also focuses search) |
| Tap handle in drawer, or back | Close drawer, clear query |
| Tap app tile | Launch the app, with the default launch animation |
| Tap a toggle | Flip it (see 7), show toast |
| Tap Play/Pause | Control the active media session (see 7) |
| Tap task row | Toggle done, persist |
| Tap contact | Place call |
| Save note | Persist, clear field, toast "Note saved" |
| Page change | Animated slide, about 350 ms ease |
| Clock | Refresh on `ACTION_TIME_TICK` (register in the Activity while started), not on a polling loop |

Keep each page's state across swipes and rotation (hold it in the ViewModel, not in composable `remember` only).

## 7. Real behaviors per widget (what Android actually allows)

- **Installed apps:** `PackageManager.queryIntentActivities` for `ACTION_MAIN` + `CATEGORY_LAUNCHER`; sort by label; refresh on `ACTION_PACKAGE_ADDED/REMOVED/CHANGED` via a `LauncherApps.Callback`. Launch with `LauncherApps.startMainActivity` or `getLaunchIntentForPackage`.
- **Wi‑Fi and Bluetooth toggles:** apps cannot flip these directly on current Android. The toggle button shows the current state (read it) and tapping opens the system panel: `Settings.Panel.ACTION_INTERNET_CONNECTIVITY` for Wi‑Fi, `Settings.ACTION_BLUETOOTH_SETTINGS` for Bluetooth. The button state updates when the user returns.
- **Flashlight:** `CameraManager.setTorchMode` on the back camera that has a flash unit; register a `TorchCallback` so state stays in sync. No permission is needed.
- **Silent:** use `AudioManager.ringerMode` (vibrate/silent). Changing it into silent can require Do Not Disturb access; check `NotificationManager.isNotificationPolicyAccessGranted` and, if missing, send the user to `Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS` once.
- **Music card:** read the active session through `MediaSessionManager.getActiveSessions`, which needs a `NotificationListenerService` the user enables in settings. Show title and play state; send transport commands through `MediaController.transportControls`. If access is not granted, show a muted prompt "Enable media access" that opens the listener settings. Without any session show "Nothing playing".
- **Agenda:** `CalendarContract.Instances` for today, next three events, `READ_CALENDAR` requested at first use of the Today page with a short in-app explanation. Event bar color: cycle mint, coral, sky.
- **Favourites:** user picks contacts via the system contact picker (no blanket `READ_CONTACTS` needed to store a picked URI). Place calls with `ACTION_DIAL`; use `ACTION_CALL` only if `CALL_PHONE` is granted and the user opted in.
- **Tasks and notes:** Room tables `tasks(id, text, done, createdAt)` and `notes(id, body, createdAt)`.
- **Feed (markets and news):** define interfaces `MarketsRepository { suspend fun quotes(symbols: List<String>): List<Quote> }` and `NewsRepository { suspend fun topStories(): List<Story> }`. Ship `Fake*` implementations returning clearly labelled placeholder data. Wire a real provider in milestone 6 once the user picks one and supplies a key (store keys in `local.properties` / `BuildConfig`, never in source). Cache results, refresh at most every 15 minutes while the Feed page is visible, and show "Last updated" only when real data is shown.
- **Voice note:** launch `RecognizerIntent.ACTION_RECOGNIZE_SPEECH` and append the result to the note field.

## 8. Accessibility

- Every icon-only button has a `contentDescription`; decorative shapes and sparklines have none.
- Toggles use `Modifier.toggleable` or `Role.Switch` semantics with state description "on"/"off".
- Page dots are real buttons with page names; announce the page on change ("Today page, 3 of 4").
- All touch targets are at least 48 dp (design minimum 44; prefer 48 in code).
- Respect system font scale: text scales, and cards grow in height instead of clipping. Test at 1.3×.
- Test TalkBack traversal order on each page (header, cards top to bottom, then the dots).
- Provide a `prefers-reduced-motion` equivalent: when animations are disabled in system settings, skip the page slide and width animations.

## 9. Content rules

- No lorem ipsum and no invented figures anywhere in shipped UI. Placeholders are bracketed (`[PRICE]`) or an explicit empty state.
- The design's sample text (event titles, contact names, "Evening Focus") is for the mockup only; production reads real data or shows an empty state.
- Use the typographic apostrophe and proper en dash in times ("10:30 – 11:00").

## 10. Settings (small, optional in milestone 5)

A settings screen reached by long-pressing the Home background: default page, choose the three market symbols, choose dock apps, show/hide the Feed page. Same tokens; list rows 56 dp high on `surface` cards.

## 11. Non-goals for the first release

Widgets from other apps (AppWidgetHost), icon packs, gesture customization, notification dots, work profile support, tablets and landscape. Do not build these now.

## 12. Milestones (build in order, each must compile and run)

1. **Shell and theme.** Project, manifest HOME filter, Manrope fonts, theme tokens, pager with four empty titled pages, page dots, back handling. *Done when:* the app can be set as the default home app and the pager swipes with dots in sync.
2. **Home and drawer.** Live clock, real installed-app grid, dock, search pill, drawer with filtering and empty state, launching apps. *Done when:* every installed app launches from the drawer and search filters live.
3. **Today page.** Flashlight toggle (real), Wi‑Fi/Bluetooth/Silent toggles with the system-panel behavior, agenda from the calendar with permission flow, music card with the notification-listener flow. *Done when:* each widget shows a real state or its explicit prompt/empty state.
4. **Tools page.** Room-backed tasks and notes, favourites via contact picker, voice note. *Done when:* tasks and notes survive a process restart.
5. **Feed page and polish.** Fake repositories behind the interfaces, Markets and Top stories UI exactly as specified, toast component, long-press settings, system font-scale and TalkBack pass.
6. **Live data.** Replace the fake repositories with a real provider after the user chooses one, add caching and error states.

### Acceptance checklist

- Matches the colors, type, radii and spacing in section 3 within 2 dp.
- All touch targets at least 48 dp; TalkBack can reach and operate every control.
- Rotating the device or killing the process does not lose tasks, notes, or the selected page.
- No placeholders or invented numbers appear in a release build without being clearly labelled.
- Cold start to interactive Home in under 1 second on a mid-range device (load the app list asynchronously, show the clock and dock first).

## 13. Prompt to give the agent

> Read `android-launcher-design-spec.md` fully. Implement milestone 1, then stop and summarize what you built and how to run it. After I confirm, continue with the next milestone. Follow the tokens in section 3 exactly, use the stack in section 2, and do not add features from section 11.