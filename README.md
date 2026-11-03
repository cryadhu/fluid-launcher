# cry: glass launcher (v1.1.0)

The current release replaces the previous interface with a warm frosted-glass Home inspired by the latest reference, with Fluid OS navigation and controls. MainActivity now uses FluidModel / FluidLauncher; the old For you, Today, Tools, league, news, home grid and customization screens are not part of this interface. Their stored data is left untouched. Earlier release notes below describe the retired interface.

- New Home, Space, Notifications and Controls pages, with spring page motion, restrained parallax and a compact floating page indicator.
- Shared glass material: a procedural lavender wallpaper, a cached three-pass blurred wallpaper sample computed off the UI thread, translucent veils, refractive offset, fine highlights and soft shadows. Overlay backgrounds also blur the underlying launcher content on supported Android versions. Foreground text is never blurred.
- A floating searchable installed-app drawer with All / Work / Personal / Tools filters, press springs, a drag-controlled dismissal and a native-app launch bloom. Hold an app for space assignment, dock changes, app info or uninstall. Work / Personal are launcher collections, not Android managed profiles.
- Nine built-in live widgets: clock, notifications, agenda, music, weather, tasks, activity, storage and date. Cards expand from their actual touch bounds into an inset-aware scrollable detail surface, then return to their original bounds. Add, remove and reorder Home widgets in Customize. The music disc uses a reduced-motion-safe infinite transition.
- Live calendar events require calendar permission. Notifications and media controls require notification-listener access; notification content stays in memory and is never persisted. Weather uses Open-Meteo after choosing a city. Tasks, spaces, dock and appearance persist in new fluid_launcher_v1 preferences.
- Activity uses the phone step counter with activity permission and counts from the first active launcher session that day. No fabricated activity or weather values. The brightness slider changes this launcher's window brightness; Android owns system-wide brightness and restricted radio switches.
- New glass settings and widget gallery with Light / Dark / Auto appearance. Android HOME registration and bootstrap-icon hiding remain functional.

Validation: 57 unit tests pass, including new config round trips, corrupt-state recovery and blur-edge checks. Release lint passes. Four emulator UI tests pass, covering all four pages, drawer filtering/search, long-press menus, keyboard dismissal, widget hero expansion/return and gallery scrolling. Signature and ZIP alignment verification pass; a signed upgrade from the backed-up previous emulator installation succeeds and the new HOME activity launches without a runtime crash. The emulator data was restored before the signed upgrade. Final APK: output/cry-1.1.0-release.apk. Screenshots: output/cry-1.1.0-home.png, output/cry-1.0.0-drawer.png, output/cry-1.0.0-space.png, output/cry-1.0.0-controls.png and output/cry-1.0.0-dark-settings.png. Performance and platform-controlled app/radio animations still need testing on the OPPO Find X9 Ultra / ColorOS 16.

---

## v1.1.0 reference Home

Home now uses warm ivory and gray glass, an analog clock and animated live weather, a stacked notification widget and a compact dock. Swipe up opens the drawer even when the gesture starts over a widget. Long-press a Home widget for Compact / Standard / Large sizes or Remove; sizes and removals survive restart. Older Fluid layouts migrate to the new clock, weather and notification header once, retaining other chosen widgets.

Notifications come from the Android notification listener. Swipe an individual card left or right to dismiss it, use Clear all for dismissible notifications, or See all to open Today with every notification. Android ongoing notifications are retained. Failed dismissals return the card to the stack; content remains in memory only. Long-pressing removes the widget itself through its options sheet.

Clock hands animate with the phone time. Weather includes drifting clouds, a sun and rain animation for actual rainy conditions, animated temperature changes and city setup through the expanded weather card. No sample temperatures, events or notifications are inserted in production.

Validation: 57 unit tests and four emulator interaction tests pass. Coverage includes resize persistence/migration, Home swipe-up, resize/remove, both notification swipe directions, Clear all, widget hero transitions and gallery scrolling. Final hardware smoothness on ColorOS remains a device test.

## v1.0.1 corrections

- The music orb leaves gaps in its circular track for the status label and play control, with a larger accessible play target.
- Play resumes an available media session. Otherwise it opens the default music player, falling back to installed YouTube Music and then its website when no local player is available. It no longer opens notification-access settings merely to start music.
- The edit pencil uses a corrected closed outline and aligned cap.
- Release signing keys and passwords remain local in the ignored signing directory. Build artifacts, emulator backups, logs and temporary files are also excluded from Git.

## Earlier implementation and release history

# cry Android launcher

A native Android Home launcher built with Kotlin and Jetpack Compose. The visual specification is in `android-launcher-design-spec.md`.

## Build and release

Requirements: JDK 17, Android SDK platform 35, Android build tools, and Gradle 8.9. Set `JAVA_HOME` and `ANDROID_HOME`, then run:

```powershell
.\gradlew.bat :app:assembleRelease :app:testDebugUnitTest :app:lintRelease
```

This workspace also contains `.gradle-dist/gradle-8.9/bin/gradle.bat` for builds without downloading another Gradle distribution. Android instrumentation tests are in `app/src/androidTest`.

The latest optimized, non-debuggable APK is `output/cry-0.14.0-release.apk`. R8 minification and resource shrinking are enabled. Its signature and ZIP alignment were verified.

The dedicated release key and passwords are local ignored files in `signing/`. Back up that entire folder securely; future release updates must use the same key. Never commit or share its private contents. Release builds are signed differently from the early debug APKs, so they cannot update those debug installations in place. Uninstalling an app removes its local data.

Release certificate SHA-256: `df1d2a1b6b525a7c605a955ba75977987638fe22f8389a43ea81dde54beb8194`.

## Launcher setup

The package is `com.example.launcher`, with an exported MAIN/HOME/DEFAULT activity. A separate MAIN/LAUNCHER/DEFAULT setup alias lets installers and app markets open the initial setup. Only that alias is disabled, with DONT_KILL_APP, after the app becomes the default Home app. The Home activity remains enabled. The launcher excludes itself from its own installed-app list.

The setup prompt uses Android's Home role and falls back to Home/default-app/general settings. If the prompt is cancelled, Home offers a separate Open Home app settings action. On ColorOS the usual path is Settings > Apps > Default apps > Home app > cry; labels vary by firmware. No firmware-specific bypass is used. This is not a privileged system application.

## Pages and interaction

- Feed: daily exchange rates and category-filtered public news, with offline cache.
- Home: device clock/date, installed app shortcuts, draggable app drawer, shared search field and editable four-slot dock.
- Today: device agenda, connectivity/sound controls, Quick Actions and media-session controls.
- Tools: persisted tasks, notes, contact favourites and voice dictation.

Home has no app-name heading. Settings opens Android settings directly. Long-press and drag Home/dock icons to rearrange them; drag drawer apps onto Home or the dock. Drop previews animate and orders persist. Search is one shared editable field that moves with the drawer. Drag the handle/search upwards to open and downwards to close. Page indicators are above the system navigation area, with content space reserved.

Third-party and branded apps use their own installed activity icons across Home, drawer, dock, folders and drag previews. System utilities retain the bundled 24x24 line glyphs and amber/mint/coral/sky accents. If installed artwork cannot be loaded, the package/label/category design glyph provides a fallback. No external icon pack is required.

Tasks, notes, pins and favourites use Room. The database is shared for the application's lifetime to avoid closing it while cancelled observers are still finishing. Preferences use DataStore.

## News preferences (v0.6.0)

Top stories has a settings button with persistent selections for World, Politics, Sports, Movies, Music, Technology, Business, Science and Lifestyle. All topics are selected on first use. Clearing every topic keeps news empty. Only selected categories are displayed, including when cached stories are used.

Category-specific public Guardian RSS feeds are fetched with bounded concurrency and interleaved. Technology retains a Hacker News fallback. Daily exchange rates come from Frankfurter. Refresh attempts are limited to a 15-minute interval; failed requests retain available cached results. No API key is bundled.

## Today controls and Quick Actions (v0.7.0)

Today now has Wi-Fi, Bluetooth, Mobile data and a sound tile that cycles Normal -> Silent -> Vibrate -> Normal. Flashlight moves into the reference-style 2x4 Quick Actions card: Scan, Translate, Calculator, Timer, QR code, Voice memo, Screenshot and Flashlight. The card follows the supplied line-icon style, spacing and accent order.

Wi-Fi lists real nearby networks after precise-location permission and device location are enabled. Refresh is subject to Android scan throttling. Selecting a WPA2/WPA3 personal network asks for its password, then opens Android's saved-network confirmation on API 30+. Enterprise/WEP networks and older Android versions use system Wi-Fi settings. Passwords are not persisted by the launcher. The top-right power button reflects actual state and opens Android's compact Wi-Fi controls because current Android versions prohibit ordinary apps from switching Wi-Fi directly.

Bluetooth shows paired devices and can discover nearby devices with Nearby devices permission. Selecting an unpaired device starts Android pairing. Discovery stops when dismissing the sheet or leaving Today. Paired profile connections and switching Bluetooth off use system Bluetooth settings; enabling uses Android's consent request. Both sheets retain More settings.

Mobile data does not open a launcher popup. Direct switching is permitted only with carrier/system privileges. An ordinary installation opens mobile-network settings instead. Becoming the default launcher does not confer these privileges. The sound cycle requests Notification policy access when Android requires it.

Scan uses Google's document scanner with PDF viewing and a Camera fallback; QR scanning uses Google's code scanner and displays a copyable result without automatically following links. Both scanner modules need Google Play services. Translate opens Google Translate. Calculator, Timer and Voice memo use the corresponding phone apps; missing handlers are reported.

Screenshot uses explicit Android screen-capture consent and a short-lived media-projection foreground service. It saves one PNG to Pictures/Screenshots on API 29+ and immediately releases capture and its notification. On API 26-28 it saves to the app's external Pictures folder. Screen capture times out if no frame is available. Flashlight requests Camera permission when needed. Calendar and media access are optional.

## Validation and device limits

Release builds, 13 unit tests, 15 instrumentation tests and lint pass. The signed v0.6.0 to v0.7.0 emulator upgrade succeeds with fixture data restored and no startup crash. Instrumentation validation covers the reference card, all three sound modes, Wi-Fi password submission, Bluetooth pairing callbacks, Android panel/settings handoffs, news preferences and existing launcher drag/drawer/dock/persistence behavior. Screenshot consent, file creation and projection shutdown were checked separately.

Testing uses an Android 14 emulator. Physical OPPO Find X9 Ultra / ColorOS 16 behavior has not been verified. OEM system panels and permission screens can differ. Generated emulator fixtures are backed up in `tmp/` before replacing test installations. Those fixtures and signing material are not application assets.

## v0.7.1 compact transient page indicator

The indicator now uses 4dp dots with 4dp gaps and a 12dp active marker: 36dp total width in a 16dp bottom strip, replacing the 192dp row and 56dp reserved space. It appears during horizontal page navigation or vertical page scrolling and fades away one second after scrolling ends, including flings. Hiding it does not shift content. Dots are a position display; page swipes and the pager's four accessibility navigation actions remain available. A controlled-clock UI test checks compact bounds, idle hiding, reappearance during vertical scrolling, and hiding again after release.

## v0.8.0 notification controls, default dialer and floating indicator

Airplane mode replaces Screenshot in Quick Actions and opens the Android airplane-mode screen. The screenshot projection service and its foreground-service permissions are removed. Mobile data now opens Android's compact Internet controls when carrier/system privileges are absent, without the repeated warning toast. Android still restricts direct radio switching by ordinary launchers.

POST_NOTIFICATIONS is now declared and a Launcher notifications channel is created. Today has an explicit Allow notifications button; it requests Android 13+ permission on demand and opens app-notification settings when permission was permanently denied or is already granted. The release permission prompt was exercised on Android 14: Allow succeeded, POST_NOTIFICATIONS became granted, and Today updated to Notifications allowed.

The separate Enable media access button explains notification-listener access and links to both its detailed Android settings page and this app's App info. Sideloaded APKs may require App info > three-dot menu > Allow restricted settings, then enabling notification access manually. The launcher cannot grant this sensitive access or remove OEM restrictions itself. The help remains available when returning from settings.

Favourite contact shortcuts build a tel: ACTION_DIAL intent explicitly targeting TelecomManager.defaultDialerPackage, preserving the selected number. They fall back to the implicit dial intent only if the default package is unavailable. Truecaller must be selected as Android's default Phone app to be used as the system default dialer; caller-ID/spam-screening defaults are separate.

The transient indicator now overlays page content and reserves no extra bottom strip. Only the system navigation inset remains in page layout. Its compact dots, idle timeout and accessibility navigation actions are preserved.

Validated: 20 instrumentation tests pass, including dialer package/number routing, notification declaration/channel/help, airplane settings handoff, and content bounds behind the indicator. Release lint, signature and ZIP alignment pass; an in-place signed v0.7.1 to v0.8.0 upgrade succeeds with restored emulator fixture data. Tests used Android 14, not a physical OPPO ColorOS 16 device.

## v0.8.1 full-height app grid and inset-safe connectivity sheets

All Apps no longer contains the bottom close-handle row. The grid uses the reclaimed 48dp area, retaining system navigation/keyboard insets. Pull down from the header or from the top of the app grid, or press Back, to close the drawer. Shared search and dragging apps onto Home/the dock are preserved.

Wi-Fi and Bluetooth sheets cap their drawable content using the host window height minus the union of the status-bar and display-cutout top insets. The cap includes the 21dp drag handle and existing navigation padding, so expanded long lists stop at the status-bar boundary and scroll within the sheet. The outer modal container remains full-height, avoiding the incorrect top anchoring caused by constraining that container. No duplicate content insets are applied.

New UI checks measure the app-grid bottom and expanded Wi-Fi/Bluetooth sheets with long test lists, and verify More settings remains reachable by scrolling. Screenshots of the long-list test fixtures are saved only in emulator test files. Release lint, signing and ZIP alignment pass. Physical OPPO ColorOS 16 has not been tested.

## v0.9.0 Home swipe and clock header

Home no longer shows a search field or reserves its former 52dp spacer. Search is rendered only inside All Apps and travels with the drawer surface. An upward swipe across Home opens the drawer through a nested scroll gesture connection; horizontal page swipes and long-press app/dock dragging remain available. The existing arrow also opens the drawer, and pulling down closes it. The drawer transition uses the viewport height rather than the removed Home search anchor.

Clock and Settings now occupy the same top-aligned header row. Tapping the clock opens the device clock application with Android's ACTION_SHOW_ALARMS intent. Search filtering remains available once All Apps is open.

Direct Wi-Fi power changes, Bluetooth disabling and mobile-data changes remain restricted by Android for ordinary installed apps. Default-Home status does not confer system or carrier privileges. Wi-Fi/mobile-data controls therefore use Android panels, and Bluetooth enabling uses the public system consent request; no unsupported bypass or visual-only fake toggle was added.

Validated: all 23 instrumentation tests pass, including a swipe from Home content, absent Home search, drawer search filtering, clock/header alignment and clock launch intent, along with icon/dock dragging and previous features. Release lint, signing and alignment pass. The signed v0.8.1 to v0.9.0 emulator upgrade succeeds and launches Home without a runtime crash. The release Home screenshot is in tmp/home-0.9.png. Physical ColorOS 16 remains untested.

## v0.9.1 focused Home, news and Truecaller shortcuts

Top Stories displays at most the first 20 stories after applying selected-topic filtering. The cached feed is preserved and ordering is unchanged. A unit test checks the limit, selection order and empty selection.

The Home up-arrow control and its 48dp row are removed. Swipe up across Home to open All Apps; search remains inside the drawer. The Notifications posting-permission card is removed from Today. Notification permission/channel support and the separate music-controls access help remain available through Android settings and the music card.

Phone intents, including the default dock Phone slot and favourite-contact dial links, now explicitly target com.truecaller rather than the system default dialer. The Phone dock slot resolves and labels Truecaller. No system-dialer fallback is used; if Truecaller cannot handle the intent or is absent/disabled, the launcher asks the user to install or enable it. Existing user-selected non-Phone dock apps remain unchanged. Truecaller was not installed on the emulator: intent routing was validated with an instrumentation activity monitor.

Validated: 14 unit tests and all 23 UI tests pass, along with release lint, signing and alignment. The signed v0.9.0 to v0.9.1 emulator upgrade succeeds with restored fixture data and no Home startup crash. Physical OPPO ColorOS 16 remains untested.

## v0.10.0 persistent Home folders

Drop an app onto the center of another Home app to create a folder. Dropping onto an existing folder adds that app without duplicating its member. Drop near the edge of a cell to rearrange instead. Folder tiles show miniature design icons and a name, animate on arrival, and can be dragged as a single Home item. Folders use the existing eight-slot Home grid and free a slot when two Home apps merge. Folder nesting and docking folders are not supported.

Tap a folder for the reference-style three-column panel, folder title/edit action, app count and Add apps tile over a blurred/dimmed Home screen. Blur uses Compose's Android 12+ rendering support; older devices retain the dimmed overlay. Tap a member to launch it, rename the folder through the pencil/title, or use Add apps to select membership. Long-press a member to remove its shortcut from the folder. Back, tapping outside or Close folder returns Home. Removing the last member removes the folder tile. The accessibility Ungroup action refuses when Home lacks room rather than dropping unrelated shortcuts.

Folder IDs, names, member order and Home item order are persisted together as home_layout_v1 JSON in the existing DataStore. Existing installations continue to use their Room pin order until the first layout edit. Tasks, notes, favourites and dock preferences are preserved; no Room schema migration is required. After a Home layout is saved, it is authoritative, with first-level app pins mirrored for compatibility. Unavailable apps retain their saved component references until a membership edit.

Validated: 19 unit tests pass, including folder creation/merge/deduplication, reorder, serialization and self/nested-drop rejection. All 26 UI tests pass, including real center-drop creation, rename, disk persistence, fresh ViewModel reload, adding/removing members, folder movement, edge-drop reordering and prior launcher behavior. The open-folder rendering was visually checked against the reference; tmp/folder-open.png contains the emulator test screenshot. Release lint, APK signing and ZIP alignment pass. The signed v0.9.1 to v0.10.0 emulator upgrade succeeds with restored fixture data and no Home startup crash. Physical ColorOS 16 remains untested.

## v0.10.1 animated Home removal bin

Dragging a Home app shortcut or folder reveals a compact bottom bin with a slide/fade entrance. Hover highlights and scales the bin, gives a haptic cue, and replaces the drag hint with Release to remove from Home. Dropping into it animates the ghost shrinking away and persists removal of that Home entry. Leaving the bin before release cancels removal. The bin takes precedence over folder/dock drop previews while hovered and disables its click action during dismissal. The page indicator hides during the drag.

Removal changes the saved Home layout only. Applications remain installed and listed in All Apps; removing a folder removes its Home tile while leaving its member apps installed. Existing dock and drawer drag behavior remains available; the removal target is shown for Home-origin drags, with no permanently reserved layout space.

New UI tests cover shortcut removal/recreation/availability in All Apps, cancelling removal after hovering, and removing a folder while checking that its member packages remain installed. tmp/home-bin-hover.png is an emulator test screenshot. Release lint, APK signing and ZIP alignment pass. Physical ColorOS 16 remains untested.
Full v0.10.1 validation: all 29 instrumentation tests pass, and the signed v0.10.0 to v0.10.1 emulator upgrade succeeds with restored fixture data and no Home startup crash.

## v0.11.0 correct installed app artwork

Third-party apps now use their own installed launcher activity icons instead of inferred generic wallet, food or shopping symbols. This covers PhonePe, Zomato, Blinkit, Axis Bank and other Indian apps automatically, including apps not known when this APK was built. Preinstalled brand apps also use their own artwork; only built-in system utilities retain the design line glyphs. The existing tile shapes, layout and gesture targets are retained. No downloaded logo catalogue, package allowlist, extra permissions or network requests are required.

ResolveInfo.loadIcon supplies the actual activity/alias artwork. Legacy, adaptive and layered drawables are rendered to 96px bitmaps on the repository IO dispatcher. A failed icon load falls back to the existing themed glyph without hiding the app. The same artwork is used in Home, All Apps, dock app cells, folder previews, open folders, the folder picker and drag ghosts. Existing package callbacks and resume refresh reload artwork after installation or app updates.

Validated: 21 unit tests, the existing 29 UI checks and two new artwork tests pass. The new checks cover branded-app icon policy, retaining legacy/adaptive artwork and drawable bounds, and bitmap rendering in app cells and folder previews. Release lint, APK signing and ZIP alignment pass. The signed v0.10.1 to v0.11.0 emulator upgrade preserves the restored fixture data and launches Home without a runtime crash. Indian apps were not installed on the emulator; their physical-device artwork has not been visually verified. Icons are obtained directly from each installed package rather than downloaded approximations.

## v0.11.1 Truecaller calling entry point

The default Phone dock shortcut now requests ACTION_DIAL without an empty tel: URI, adds CATEGORY_DEFAULT and CLEAR_TOP, and stays scoped to Truecaller. Saved Truecaller app shortcuts also request this calling entry point instead of opening the generic MAIN launcher activity, which may restore Messaging. Favourite contacts preserve their selected number in a tel: URI and use the same flags. Dial intents clear any stale explicit component before being scoped to Truecaller. No private Truecaller activity name or undocumented deep link is used.

Validated: 21 unit tests, five targeted platform UI tests, release lint, signing and ZIP alignment pass. The new routing test covers the default Phone shortcut and a saved messaging-alias component; the existing favourite test verifies the phone number, package and CLEAR_TOP flag. Truecaller is not installed on the emulator: interception tests verify the outgoing intents, not Truecaller's internal tab handling. The resulting screen needs confirmation on the user's OPPO with its installed Truecaller version.

## v0.12.0 All Apps long-press menu

Holding a drawer icon displays an anchored, animated in-window menu with Add to Home, App info and Uninstall. Already-pinned first-level Home apps show a disabled Already on Home action. System apps show a disabled Uninstall action. App info opens Android's package details screen; Uninstall requests Android's package deletion confirmation and never deletes immediately. The manifest declares REQUEST_DELETE_PACKAGES for this user-initiated action.

A drawer long-press stores a pending app without changing Home or closing the drawer. Movement must exceed Compose's touch-slop threshold before the menu closes and the existing drag-to-Home/dock flow begins. Releasing without moving keeps the menu open. Back and outside taps dismiss it while keeping All Apps open. The menu stays in the same window so the original held gesture can continue into a drag. Existing Home/dock long-press drag behavior is retained. Accessible App options actions open the same menu.

The root long-press recognizer consumes release during PointerEventPass.Initial, before child click handlers. This prevents a completely motionless hold from launching the underlying app on finger-up. The stationary-hold regression test sends no movement event at all.

Final validation: 21 unit tests and 16 targeted UI tests pass after the release-consumption fix. The targeted suite covers all three menu tests, six Home/drawer/dock gesture tests, three folder tests, three removal-bin tests and the page indicator. The earlier full suite also exercised the remaining controls. Release lint, APK signing and ZIP alignment pass. A signed v0.11.1 to v0.12.0 upgrade succeeds with the restored emulator fixture and no startup crash. A separate stationary press-and-release in the signed release keeps App info and Uninstall visible; tmp/cry-fixed-menu.png is the verified screenshot. Physical OPPO ColorOS 16 remains untested.

## v0.12.1 4×4 Home grid

Home now has four columns and four rows, with 16 shared shortcut/folder slots above the separate four-app dock. Row spacing is reduced and the compact clock treatment is used below 760dp of available height so all four rows fit the emulator portrait viewport. The original eight default shortcuts are retained, leaving the added rows available for user choices. Explicit legacy pin lists can populate all 16 positions.

A shared HOME_CAPACITY constant updates persistence decoding, saved Home layouts, reorder/drop helpers, Add to Home, folder ungrouping and accessibility move limits. Existing saved orders and folders retain their data without a storage migration. The first pin edit of a legacy/default Home now saves the actual visible layout, so adding a ninth shortcut does not silently keep the old eight-item display. Full Home reports its 16-item capacity.

Validated: all 22 unit tests and all 37 instrumentation UI tests pass. New tests check all 16 cells and app labels are visible above the dock, adding shortcuts 9–16, rejecting a seventeenth item, disk persistence and a fresh ViewModel reload, reordering/persistence of all sixteen entries, and dragging a drawer app into the fourth row. The four-row rendering was visually inspected in tmp/home-4x4.png. Release lint, APK signing and ZIP alignment pass. A signed v0.12.0 to v0.12.1 emulator upgrade preserves the restored fixture and launches Home without a runtime crash. Physical OPPO ColorOS 16 remains untested.

## v0.12.2 persistent individual Home slots

Each Home entry now stores a slot number (0–15). Apps and folders can occupy any cell in the 4×4 grid without filling earlier rows. Moving to an empty cell leaves the source empty; an edge-drop onto an occupied cell swaps the two existing Home entries. A new drawer app dropped on an occupied cell moves that occupant into the first available empty slot, preserving unrelated icons. A completely full grid refuses an extra non-folder item rather than silently dropping a shortcut. Center-drops still create or add to folders.

Old JSON layouts without slot numbers migrate in their existing packed order. Slot numbers are persisted in the same home_layout_v1 JSON and mirrored into Room pin positions. Normalization validates slot ranges and resolves duplicate cells while preserving legitimate gaps. Bin removal, folder merges and member edits preserve other positions; ungrouping uses the folder's original cell and available empty cells. Add to Home fills the first free cell. The renderer and folder/drag hit testing use actual slot numbers instead of list indices.

Validated: 24 unit tests and all 38 distinct UI cases pass (37 in the full run, followed by all six gesture tests after correcting the old full-grid insertion fixture to leave a free slot). New unit checks cover migration, sparse round trips, occupied-cell swaps, safe full-grid rejection and preservation of displaced apps. The new UI regression moves one of only two apps into slot 15, checks the other stays in slot 1, and confirms the sparse positions with a fresh ViewModel reload. The bottom-row rendering was visually inspected in tmp/home-sparse.png. Release lint, APK signing and ZIP alignment pass. A signed v0.12.1 to v0.12.2 emulator upgrade preserves the restored fixture and launches without a runtime crash. Physical OPPO ColorOS 16 remains untested.

## v0.12.3 centered expanded folders

The expanded folder title, app count, app panel and Close folder action are centered together vertically and horizontally inside the system-bar-safe viewport, with symmetric 24dp padding. The former fixed 52dp top offset is removed. Content taller than the viewport still scrolls from its natural top and the blurred/dimmed Home background remains in place.

Validated: all three folder UI tests pass, including explicit horizontal/vertical center bounds, rename and fresh-load persistence, app membership edits and folder dragging. The centered expanded rendering was visually inspected in tmp/folder-centered.png. Release lint, signing and ZIP alignment pass. The signed v0.12.2 to v0.12.3 emulator upgrade restores the original fixture and starts Home without a runtime crash. Physical OPPO ColorOS 16 remains untested.

## v0.13.0 stationary Home returns

Explicit HOME/setup intents use a dedicated returnHome state update rather than the animated page-navigation path. A return epoch recreates the pager at Home immediately and initializes the drawer transition closed, so returning from Feed/Tools does not animate horizontally through launcher pages. Normal user swipes retain their page animations. Launching an installed app prepares this clean Home state underneath it; app Back can reveal the same Home activity with no drawer slide-out.

The Home window uses stationary alpha-only open/close and task-transition resources, and launched apps receive a short centered fade/scale option with a stationary launcher underneath. This applies to installed-app launches and external intent launches. The Home activity is excluded from normal app Recents. The launcher remains a HOME-role activity and does not attempt to parent another app's task, intercept its Back navigation or use privileged/hidden animation APIs.

Android's public Activity documentation states that application overrides cannot customize cross-task transitions on modern Android. Consequently ColorOS may still choose its own app-close, predictive Back, Home-gesture or Recents animation. This update removes the launcher's own horizontal page return and supplies stationary Home/default launch resources; it cannot promise the stock OPPO launcher's privileged icon-morph animation. Reference: https://developer.android.com/reference/android/app/Activity#overrideActivityTransition(int,%20int,%20int)

Validated: 24 unit tests and all 41 distinct UI cases pass (39 in the full run, plus all three return tests after correcting their fixture to use the real HOME task and a fresh Settings root task). New checks verify Home appears in the first frame after explicit HOME intents from Feed and Tools, the Home window animation has no translation/scale, the activity is excluded from ordinary Recents, and Back from a real Settings app returns to Home with the drawer closed. Release lint, APK signing and ZIP alignment pass. The signed release installs and launches with the restored emulator fixture; a separate release smoke test opens Android Settings and returns to the Home clock with Back. Older APKs were not present in output for a previous-version upgrade check. Physical OPPO ColorOS 16 animation behavior remains untested.

## v0.14.0 Home customization, pages and widgets

Home's fixed Settings button is removed. Long-press an empty grid cell or background to open the reference-style bottom sheet: Widgets, Wallpaper, Android Settings, Add page, widget shortcuts, Edit pages and Lock home layout. Android Settings still opens directly from the Settings action. Four wallpaper colour presets update the launcher background. The first upgrade to this customization system also unpins a first-level Android Settings shortcut without moving remaining slots; Settings remains in All Apps and can be deliberately pinned again.

Add page inserts a new independent 4×4 Home page immediately to the right of primary Home. It supports the drawer, drag placement into sparse cells, folders and the shared dock. App launch prepares the originating Home page underneath it; explicit HOME intents still return to primary Home. Page IDs remain stable even when a page is removed, so Today/Tools actions are not reassigned by a changed pager index. The small page indicator updates its count.

Edit pages shows previews based on the actual page contents. Today, Tools and any added page can be removed; For you and Home are protected in both UI and model. Reset asks for confirmation, restores the original four pages, initial shortcuts/dock, wallpaper, topics and widget selections, and unlocks the layout. Added page layouts and widgets are cleared. Room notes, tasks and favourites are retained. Lock blocks shortcut, folder, dock and widget layout edits while still allowing page management and unlocking through the sheet.

The picker contains all ten reference widgets in three groups:
- Time and weather: Clock (4×2), Agenda (4×2), Weather (4×2).
- Money and media: Markets (4×2), Music player (4×1), Quick settings (4×1).
- Tools and focus: Tasks (2×2), Focus timer (2×2), Quick note (4×1), Favourites (4×1).

Widgets reserve their entire rectangular grid footprint. App dragging/pinning respects those cells. Add chooses a free contiguous area and creates a new page when the current page has insufficient space, preserving existing icons. Hold a widget for Remove widget or Move to page; the latter also offers a new page. All page layouts, widget placements, lock state, colour choice and weather city are stored in home_customization_v1 JSON in DataStore alongside the existing primary-Home slot layout. No Room schema migration is required.

Clock opens the device clock. Agenda reads permitted device-calendar events and links to Calendar. Weather lets the user choose a city and uses Open-Meteo geocoding/current/hourly forecasts, with attribution and refresh. Markets reads NIFTY 50 and S&P 500 from Yahoo Finance's public chart endpoint, showing one-month percentage change, quote time, delayed-data labelling, real sparklines and attribution. The public endpoint can throttle or change; empty/error states offer Retry rather than fabricated prices. Weather and Markets refresh periodically while the launcher process is alive; only those placed widgets, or a visible catalogue preview, request network data. Provider checks confirmed valid public forecast and index responses during development.

Music controls the actual active media session after media-access permission. Quick settings opens the existing local Wi-Fi/Bluetooth sheets, toggles the flashlight with camera permission and cycles sound mode; Android radio restrictions are unchanged. Tasks toggle saved items and add real Room tasks. Quick note saves real Room notes or uses speech input. Favourites pick contacts and route calls through Truecaller. Focus provides a shared 25-minute countdown with pause/reset, a persisted deadline and completion notification when permitted. It does not request exact-alarm or foreground-service privileges: if Android kills the launcher process, completion is reconciled when it restarts rather than guaranteeing an alarm at that moment.

Data sources: https://open-meteo.com/ ; https://finance.yahoo.com/ . These are built-in Home widgets, not an external Android AppWidget-host catalogue.

Validated: all 30 unit tests pass. All 49 distinct UI cases pass: the full 48-case suite plus the added custom-page drawer-drag case; subsequent focused runs passed customization, folder, gesture and Home-return checks (20 cases), followed by the final customization/return subset (11 cases). Coverage includes all ten widget types, footprint collision prevention, persistent configuration and sparse app slots, independent added-page layouts, page previews/protected removal/reset, widget moving/removal, layout locking, real task/note/focus actions and the existing launcher controls. Live Open-Meteo geocoding/forecast and Yahoo NIFTY/S&P endpoints returned valid responses in host smoke checks. Release lint, signing and ZIP alignment pass. The signing certificate matches the previous installed release, and a signed v0.13.0 to v0.14.0 emulator upgrade restores the original fixture, removes the Home Settings shortcut and starts without runtime crashes. Final palette-only adjustments were rebuilt with unit/lint checks and installed in place. Reference-style release screens were visually inspected in tmp/cry-customize-release-sheet.png and tmp/cry-widget-release-picker.png. Physical OPPO ColorOS 16 remains untested.

### v0.14.1
- Widget catalog and page editor use an explicitly bounded dialog viewport so their weighted scroll areas cannot expand beyond the screen on OEM firmware. The customization sheet also scrolls on short displays.
- Phone shortcuts and saved Truecaller entries now send ACTION_DIAL with a tel: scheme even when opening an empty dialpad; scheme-less DIAL was reaching the messaging composer on the reported phone. Favourite numbers retain their tel:number URI. No call is placed automatically.
- Added finger-swipe regression coverage for the catalog and page grid, and updated phone-intent assertions. Instrumentation APK compiles; runtime UI checks could not run on this host because WHPX initialization failed (80070005). Physical ColorOS/Truecaller validation is still required.

### v0.15.0 — Premier League
For you starts with a top-six Premier League table: position, club name and badge, goals against (GA), goals for (GF), goal difference (GD), and points. Tap the card to open all 20 clubs in a scrollable table with Refresh, season, update time and a source link. Home closes the expanded table.

Standings and club logos come from ESPN's public, unauthenticated JSON/CDN endpoints. No API key is required; these endpoints are not a documented stability guarantee. The app validates a complete 20-club table before replacing the disk cache, preserves saved standings on errors, and shows an offline/error state. Refresh on entering For you is limited to once per 15 minutes after success (one minute retry delay on failure); the full table has a manual Refresh button. Club badges are cached locally, with a fallback glyph if downloading fails. No standings are hardcoded.

Validation: live endpoint returned 20 ranked clubs and a working PNG badge; parser tests cover ordering, stat mapping (including negative GD), season, and rejection of incomplete tables. Runtime phone UI verification is pending because this host's Android emulator cannot initialize WHPX.

### v0.15.1
Removed the GA/GF/GD explanatory legend from the full Premier League table. Replaced configuration-height customization dialogs with a shared native MATCH_PARENT window using decorFitsSystemWindows=true and safeDrawingPadding. Widgets, Edit pages, Wallpaper and the expanded league table now use the actual window's safe bounds rather than an estimated display height; this addresses content extending behind three-button navigation. Existing swipe regression instrumentation compiles; runtime device checks remain pending due to the host WHPX failure. Unit tests and release lint pass.

### v0.15.2
Added an explicit bottom gutter to the shared full-screen panel used by Widgets, Edit pages and the Premier League table. It reserves the navigation-bar inset even when transient system bars are hidden, with a minimum 24dp space when OEM dialog inset reporting returns zero. Top and side safe areas remain inset-aware.

### v0.15.3
Widgets, Edit pages and Wallpaper now render as full-screen Activity overlays instead of Dialog windows, using the Activity safeDrawing insets. This avoids the separate dialog inset behavior still reported on the phone. Editors include an additional 24dp bottom gutter and widget/page scroll content includes 80dp of trailing space, letting the final controls scroll above navigation. Back returns to customization. Release, instrumentation compilation, unit tests and lint pass; no Android device is connected for runtime visual validation.

### v0.15.4
Added Pld (games played) before GA in both Premier League tables, using ESPN's gamesPlayed stat. Column widths adjusted to keep all five numeric columns visible. Parser regression includes the played value.

### v0.16.0 — Arsenal fixtures
Added Arsenal's next four upcoming games immediately below the Premier League table on For you. Each fixture shows home and away badges/names, date, kickoff in Asia/Kolkata (IST), and competition. Includes all competitions through ESPN's public Arsenal schedule endpoint. Provider data is cached to disk, refreshed independently of standings/news, with Refresh and offline/error states. Past, completed, postponed and cancelled matches are excluded; unconfirmed times show Time TBC. Logos reuse the standings badge cache. Tests cover provider timestamps without seconds, home/away badges, status filtering, future chronological top-four selection, and Indian date rollover.

### v0.16.1
Compacted the four Arsenal fixtures into two-line rows: inline 22dp club badges and single-line names, with date and IST time together below. Reduced vertical padding and removed the separate competition line to reduce feed scrolling. Fixture selection and kickoff data are unchanged.

### v0.16.2
Centered the Add widgets shortcut cards with 28dp icons and an 8dp icon/label gap. Removed Required labels and the required-page helper text from Edit pages; protected pages still have no delete button. Edit pages now draws its scrolling grid behind navigation and reserves only navigation-bar height plus 16dp in the grid's trailing content padding, removing the previous fixed 80dp tail and external bottom gutter.

### v0.17.0
Widgets now scrolls behind the navigation bar with navigation inset plus 16dp of trailing content, matching Edit pages. Reset moved into the Edit pages toolbar at the top right. Removed visual page titles from editor previews and placed delete icons on removable previews; For you and Home have no delete control.

Long-press a removable preview and drag onto another removable preview to reorder on release. A floating preview follows the pointer, the drop target highlights, and holding near the grid's top/bottom auto-scrolls. Cancelling preserves the original order. For you and Home cannot be dragged or targeted; saved ordering persists and keeps all page content under stable IDs. Reset restores original ordering. Unit coverage checks fixed-page guards and ordering persistence; a new drag UI regression compiles. 36 unit tests and release lint passed. Runtime gesture verification is pending because the host emulator cannot initialize WHPX and no phone is connected.

### v0.17.1
Weather catalog previews now have a 200dp minimum and wrap their content vertically, preserving forecast and bottom source/refresh text instead of clipping a fixed 164dp box. Other previews keep their previous sizes.

Wallpaper selections apply the matching solid-color preset to Android home and lock screen with WallpaperManager FLAG_SYSTEM | FLAG_LOCK on an IO dispatcher. Declares normal SET_WALLPAPER permission, serializes rapid selections, checks platform support/policy, and saves the launcher selection only after a successful API result. Failure is surfaced to the user. Preset colors are shared between launcher rendering and the generated Android wallpaper bitmap. No phone is attached for runtime ColorOS lock-screen verification.

### v0.18.0 — Themes
The top Widgets action in Customize home is now Themes; the Add widgets shortcut strip and See all widget catalog remain. The Themes screen has Tonal, Solar, Ember and Sand preview cards, selected indicators, a custom background/accent hex-color editor, and a follow-system light/dark switch (Tonal at night, Sand in light mode). Theme preferences and opaque custom colors persist alongside customization settings. Theme selection switches the launcher to its theme background; explicit wallpaper choices still use their own solid color and existing Android wallpaper update path. Theme changes themselves do not replace the phone wallpaper.

Shared color tokens and text styles now update across launcher screens, utility icons, cards, dock, dialogs and editors, with light/dark Material schemes and system-bar icon contrast. Weather and Markets catalog previews wrap their actual content without a minimum height, eliminating the weather blank tail and Markets clipping. Existing Home widget grid footprints are unchanged.

Validation: 39 unit tests passed, including preference round trips, palette resolution, custom color validation and system-mode choice. Theme-picker UI regression compiles; release lint, signing and APK alignment checks pass. Runtime phone visual checks remain pending (no connected phone; host emulator WHPX failure).

### v0.18.1
Open-folder apps now support long-press then drag onto the Home grid or dock. The folder panel/dimming/blur clears during the gesture while its input window stays alive; coordinates are converted from the dialog to the launcher. Drops use the existing animated placement. Folder membership is removed only on a valid committed placement, dock drop or bin removal; cancelled/full/blocked placement preserves the saved folder. Stationary long-hold still opens Remove from folder. Layout locking is respected.

Phone intents now query installed exported Truecaller telephone handlers, reject messaging/SMS/MMS/conversation/compose/shortcut activities (including alias targets), prefer a dialer, and bind the resolved component explicitly. Empty phone launches request a fresh Truecaller task so an existing SMS composer is not resumed. Favourite-number telephone URIs remain intact. If only messaging handlers exist, the app reports that the calling screen is unavailable rather than opening them. Truecaller's current device-specific exported activities still require real-phone verification.

Validation: 42 unit tests passed; release lint and UI instrumentation compilation pass. New tests cover transactional extraction, last-member/full/blocked cases, and calling-handler filtering. A folder-to-Home gesture regression compiles. Runtime phone checks remain pending because no phone is connected and the host emulator cannot initialize WHPX.

### v0.19.0 — Adaptive dock
Dock Phone uses the same model.launch(app) path as the working Home app shortcut. The built-in Messages entry uses a distinct messages glyph and label, even when Truecaller is the SMS handler; previously both entries could display Truecaller's identical artwork, making the SMS launcher look like Phone. Explicit app entries also use the Home launch path.

Dock persistence supports zero through five entries. The centered dock width follows its count, with an empty add target when cleared. Dragging an external app reveals an insertion slot until the five-icon limit. Insertion preserves existing entries; a full dock rejects a sixth app instead of replacing one. Existing icons can reorder, drag to the removal bin, move to Home, or use Remove from dock accessibility actions. Successful Home-to-dock moves remove the Home shortcut; folder membership is removed only after a valid dock drop. Unavailable built-in entries can also be dragged to the bin but cannot create invalid Home entries. The bin sits above the dock so it does not obscure drop positions.

Validation: 44 unit tests and release lint passed; updated instrumentation compiles for fifth-icon insertion, count-based resizing, removal with Home unchanged, and persistence. Signed APK and alignment verified. Phone launch and gestures still need device verification; no phone is attached and host WHPX is unavailable.

### v0.20.0 — Functional Home settings
Customize home → Settings opens an in-launcher Home settings screen with Appearance and Layout and behavior tabs styled after the reference. Android settings remains available as a link at the bottom.

Appearance: Tonal/Slate/Paper wallpaper presets, user-selected photo wallpaper, Light/Dark/Auto theme mode, accent swatches, Squircle/Circle/Square app-icon tiles, icon sizing (44–68dp), and app-label visibility. Photo selection reads only the selected URI, samples large images, stores a private JPEG and applies it to both Android wallpaper destinations; launcher rendering uses the same photo with a contrast overlay. Theme preset/custom selection clears a prior accent override so its chosen colors apply correctly.

Layout: 4×2/4×3/4×4 grids, dock limit 0–5, optional Top/Bottom/Off Home search bar, Large/Small/Hidden Home clock, and Feed/Today/Tools visibility. Defaults preserve the existing 4×4 grid, five-icon limit and no Home search bar. Smaller grids/limits are rejected with visible feedback if they would hide existing icons/widgets; no shortcuts are discarded. Hidden page data is retained and toggles can restore deleted base Today/Tools pages. Home stays visible; the page editor still protects Home and For you against deletion/reordering.

Gestures: optional swipe-up drawer opening; drawer closing stays enabled. Double-tap lock listens only on empty Home space and calls Android's GLOBAL_ACTION_LOCK_SCREEN (Android 9+) through a dedicated user-enabled Accessibility service. That service has no window-content retrieval capability and ignores received events. Notification dots use the existing user-enabled notification-listener service, exposing only package names with clearable, non-summary notifications to the UI. Dots update on posts/removals and disappear on access disconnect; icons/folder previews respect the global switch. Settings contains Android access setup buttons. No access is enabled automatically.

Validation: 49 unit tests passed, including all setting round trips/legacy defaults, enum/bounds sanitization, safe grid resizing, hidden-page preservation and accent contrast. Instrumentation compiles for Home settings routing and label/grid/search/page/swipe controls. Release lint, signing and alignment checks passed. No phone was attached; runtime photo/lock/notification and gesture verification remains pending (host emulator WHPX failure).

### v0.20.1 — Drawer and notification refinements
Opening any drawer app menu clears the search field focus and focusSearch request, then hides the IME immediately and again after menu composition. Search text remains intact. Recent-row and grid icons use separate hit keys, preserving menu anchors and hold-and-drag behavior when the same app appears in both places.

Notification indicators are a fixed blue across themes and appear on Home/drawer/dock app icons, collapsed folders (including members beyond the first four previews), and each app inside an open folder. The badge is outside the icon clip so Circle mode cannot crop it. With notification access, any active notification for a package, including ongoing or summary notifications, supplies the dot; removing the last notification clears it. The existing global Notification dots switch is respected.

The drawer has a New apps row directly below search, followed by a divider. Uses PackageInfo.firstInstallTime rather than update time, excludes unknown/future/expired timestamps, deduplicates launcher aliases by package, and starts with newest installs. Only apps installed within the previous five minutes qualify; expiry is checked every second while the drawer is composed. Up to four are shown; if more qualify, the row advances through circular groups of four every five seconds. Rotation pauses for menus/drags. Row/divider disappear together when no app qualifies, and are hidden during a text search. Recent icons support launching, menus and dragging to Home.

Validation: 52 unit tests passed, including exact five-minute expiry, future/unknown/update-like old timestamps, alias deduplication and round-robin coverage. Instrumentation compiles for keyboard focus/IME dismissal, recent row/divider visibility, and folder notification indicators. Release lint, signing and APK alignment passed. Runtime phone verification remains pending (no connected phone; host emulator WHPX unavailable).

### v0.20.2 — Premier League hero transition
The For you league card now morphs into the full table in the same Activity window instead of opening a separate Dialog. The visible surface animates its actual card position/size, corner radius, padding/safe insets and heading size. Back/Refresh/source controls fade in while the table viewport grows to reveal the remaining clubs. Opening uses a 450ms eased transition; collapse uses 380ms. If the table is scrolled, it smoothly returns to its first rows before the reverse morph. HOME/page changes dismiss the overlay immediately to preserve launcher behavior.

Source bounds are frozen for opening and tracked for closing, so data refreshes that change card height still return to the current card. Source layout remains in place but hidden during the morph; first-six row presentation is shared between compact and expanded renderers. An in-memory club-badge cache avoids reloading visible logos during the transition. Detail scrolling is enabled once expansion completes. Native Compose motion duration scaling is retained. Full-screen content uses the Activity's safe insets, with no system dialog/window animation layered over the hero.

Validation: 52 unit tests passed; release lint, APK signing/alignment and instrumentation compilation passed. Added UI regression coverage for intermediate expansion geometry, all-20 scrolling, reverse closure and restoration of card bounds. Runtime smoothness on the phone remains unverified because no phone is connected and the host emulator cannot initialize WHPX.

### v0.20.3
- Newly installed apps remain eligible for 20 minutes, retaining the four-app round-robin display.
- Drawer long-press menu adds Add to favorite / Remove from favorites. App favorites are persisted separately from contact favorites.
- Favorites appear below new apps in a horizontally scrollable row with launch, options, and drag-to-Home support. Empty favorites have no row or divider. Search continues to show matching apps only.
- Updated install-window boundary tests; added favorites row instrumentation coverage (compiled only; device runtime verification pending).

### Markets and news

The former Today page is now Markets & news. Indian equities (Reliance, TCS and HDFC Bank on NSE) are selected by default. Tap a quote or the settings icon to select a market preset, enter up to eight Yahoo Finance symbols from any supported exchange, and choose news topics including sports, music and movies. Preferences persist across restarts. Stock charts offer 1D, 1M, 3M and 1Y ranges; quotes display their provider timestamp and may be delayed. Public Yahoo Finance data can be unavailable or rate limited; the page shows a retry message instead of sample prices. Refresh reloads quotes and news. Guardian RSS stories display publisher images when supplied, with a glass placeholder when an image is unavailable, and open the original article on tap. Notification details remain available from the home notification stack. Shared glass surfaces and translucent control tints apply throughout the active Fluid launcher.

Release signing in this checkout has been recreated with a new local key at `signing/release.p12` and credentials at `signing/release.properties`. Back up the ignored `signing/` directory securely and reuse it for future releases. This signing identity cannot update installations signed with a previous key. Neither signing material nor generated APKs belong in Git.

### Dock and notification interactions (1.1.1)

Swipe-up no longer opens All apps; use the dock’s All apps button. Hold and drag a dock app onto another slot to reorder it, or onto the animated Remove bin to remove its shortcut. Removing an icon never uninstalls the app. Settings → Dock, directly below Appearance, controls one to six app icons, icon size, app names, and selected apps. The All apps button is separate from the app count. Reducing the visible count preserves hidden saved shortcuts.

Every notification in the home stack is composed with its actual content, so dragging the top card reveals the next notification immediately. Swipe left or right to dismiss clearable notifications; ongoing notifications return to their position. See all opens a scrollable list. Tapping a notification sends the originating app’s content PendingIntent with the Android 14+ sender launch opt-in, preserving the app-provided destination. Notifications without an action or with expired actions display a message. Android’s sender opt-in is documented at https://developer.android.com/guide/components/activities/background-starts.

The targeted Fluid interaction suite covers disabled swipe-up, drag-to-bin removal without uninstalling, dock reordering and settings, original notification action delivery, fully composed notification cards and swipes in both directions. Android test dependencies use Espresso 3.7.0 and matching AndroidX Test libraries for Android 16 input support. The fixture grants the emulator Home role before activity launch to keep setup prompts out of interaction tests.

Validation for 1.1.1: all 60 unit tests and all eight targeted Fluid interaction tests pass on the Android 16 emulator. Physical device and OEM notification destinations have not been visually verified.

### Inline notification deck (1.1.2)

Home notifications use a compact rounded glass stack with the app name and headline on the left and the app icon on the right. The count appears only in the section title, `Notification(x)`. See all unfolds the stack in place on Home and becomes Collapse. The expanded deck shows at most four full cards; remaining notifications stay rendered behind the fourth. Scroll vertically within the deck to bring later notifications forward while earlier cards cycle behind the stack; scroll back to revisit them. Horizontal swipes still dismiss clearable notifications and tapping a card still sends its original app action. The deck uses a fixed four-card viewport so large notification queues do not push the rest of Home indefinitely.

Validation for 1.1.2: 61 unit tests and nine Fluid emulator interaction tests pass. The compact, expanded and one-step-scrolled notification layouts were visually inspected using captured test screenshots. Release lint, APK signing and ZIP alignment pass.

### Notification list refinement (1.1.3)

The collapsed notification stack has no card or icon shadows and renders at most three preview cards. See all expands every notification inline into Home’s existing scroll container; vertical gestures on the notifications scroll the entire Home content, including its header and widgets, while the dock stays fixed. Collapse restores the three-card preview. Notification app icons are now 32 dp. Both collapsed and expanded cards show local timestamps as `Jan, 03 2022 10:00AM` (English month abbreviation, two-digit day, four-digit year and 12-hour time). This replaces the four-card rotating viewport from 1.1.2.

Validation for 1.1.3: 61 unit tests and nine targeted Android 16 emulator interaction tests pass. The notification test checks the three-card collapsed limit, all seven expanded fixtures, 32 dp icons, timestamp text, full Home scrolling and reaching the following widget. Collapsed, expanded and scrolled layouts were visually inspected. Release lint, APK signing and ZIP alignment pass.


## v1.1.4 automatic weather and widget-size labels

Weather defaults to automatic approximate foreground location, with a one-shot location lookup and an Open-Meteo coordinate forecast. The launcher requests location permission once after default-Home setup. The expanded weather card offers Use current location and a manual city fallback. Active-launcher refreshes are limited to fifteen-minute intervals; coordinates are not persisted or tracked in the background.

Weather animations use the returned WMO code, is_day flag and wind_speed_10m in km/h. Sunny/partly cloudy, overcast, rainy, snowy, foggy, stormy, moon/stars and wind visuals follow the result; wind is shown from 20 km/h. Night is based on the forecast location, independent of the launcher theme. Source fields: https://open-meteo.com/en/docs .

Widget-size options keep labels centered and on one line. Narrow layouts and larger font scales use full-width stacked choices. Validation: 63 unit tests pass, release lint/build pass, and an Android UI test verifies single-line labels at 180% font scale in a 240dp-wide panel. The debug-only Compose test host is excluded from release builds.

Release signing for v1.1.4 uses the existing local key in signing/cry-release.p12, as requested. Devices with APKs signed by the previous certificate require a reinstall; subsequent builds must reuse this existing key. Signing files remain excluded from Git.

### v1.1.5 — Page order and Your Space

Markets & News is now the first page, Home is second, followed by Your Space and Controls. Startup, the Home button, Back from a page, and app launch returns select Home.

Your Space has Work, Personal, and Tools tabs showing the apps assigned to each section. Assign apps through Organize spaces or the drawer long-press menu. Space widgets are saved independently of Home: long-press to resize or remove, and use Add widgets to bring them back. The calendar is no longer included by default in Your Space.

Validation: 64 unit tests, release lint, signed release build, and an Android UI test covering Home return, all three mapped app tabs, and independent Space widget removal passed.

### v1.1.6 — Fluid Launcher identity and persistent feeds

The app is now named Fluid Launcher with application ID `com.cry.fluid` and the supplied adaptive icon, including its monochrome layer. This package installs separately from `com.example.launcher`; choose Fluid Launcher as the default Home app and grant its permissions again. Existing old-package settings are not transferred automatically.

Home-button returns spring smoothly to Home, whose page marker is now a small house. Markets & News saves responses in app storage across restarts and checks the 10-minute refresh window while the page is active. Stock symbols/ranges and individual news sources have separate cache entries; unavailable sources keep their last successful data and wait 10 minutes before retrying. Changing to an uncached selection loads it immediately.

Validation: 66 unit tests, Android navigation UI test, release lint, signed APK verification, and installation/HOME launch under the new package ID passed. The existing release key was reused.

### v1.1.7 — Refresh, charts, settings and wallpapers

Markets & News now has an icon-only refresh control that updates both stocks and headlines immediately, bypassing their normal 10-minute cache window. Stock charts smoothly morph between 1D, 1M, 3M and 1Y data. Theme choices are centered, and Android Back follows the same panel history as the toolbar Back button.

Customize now includes Wallpaper. Home screen and lock screen have separate selections, each offering Original and the new Lavender design recreated from the supplied reference without its status icons. Selecting a Home wallpaper updates both the launcher background (including glass sampling) and Android's system wallpaper; selecting a lock wallpaper updates Android's lock wallpaper independently. Selection is saved after successful application, and the operation survives Android's wallpaper-driven activity recreation.

Wallpaper application uses Android's [WallpaperManager](https://developer.android.com/reference/android/app/WallpaperManager) system and lock flags. Each image is rendered at device dimensions from the same artwork used in the launcher previews.

Validation: 68 unit tests, two Android UI tests (including real system/lock wallpaper application and panel Back navigation), release lint, and signed release build passed. The local JBR 17 G1 runtime crashed during optimization; the successful build used Serial GC without changing app release settings.

### v1.1.8 — Control widgets and animated notification stacks

Removed the Controls page; the page order is Markets, Home, Your Space. Add Brightness, Media volume, Wi-Fi, Bluetooth, Mobile data, Sound, Flashlight and Airplane mode from either widget gallery. Radio widgets form compact three-column rows on Home and Your Space and support long-press resizing/removal. Brightness can change Android's system level after allowing Modify system settings; volume controls the music stream. Sound cycles normal/silent/vibrate with Android's required access; Wi-Fi, Bluetooth, mobile data and airplane mode open their supported Android control panels.

The refresh glyph now uses a clean pair of circular arrows. Stock range changes keep the existing chart and card layout, with a small loader in each quote header rather than an extra loading text row. Notification expansion and collapse keep the same cards mounted while animating their position, width, height and stack spacing.

Validation: 69 unit tests, release lint, signed release build and two Android UI tests passed, including control widget removal and intermediate card positions during notification expansion/collapse.

### v1.1.10 — Local acrylic material preview

Shared surfaces now use a Windows Start menu-inspired acrylic finish: stronger cached backdrop blur, a neutral translucent light/dark tint, stable fine grain, subtle hairline borders and restrained neutral shadows. The treatment follows the blur/tint/noise approach described in [Microsoft's Acrylic material guidance](https://learn.microsoft.com/en-us/windows/apps/design/style/acrylic). It avoids the directional gloss and raised bevels of the rejected preview. Foreground icons and text remain sharp.

Keep this styling preview local for review; no push is authorized for this update.

Validation: 69 unit tests, release lint, signed APK verification and emulator visual checks in Light and Dark passed. No push was performed.

### v1.1.11 — Dawn wallpaper, cloud artwork and notification shadows

The supplied Dawn image is the default Home background. Both wallpaper pickers now offer Dawn, Original and Lavender in that order. Rendering crops out the navigation pill embedded in the supplied image. The selected wallpaper also feeds the acrylic backdrop sampling and Android wallpaper application.

The automatic dock defaults to four app icons. Older automatic three-icon defaults migrate once, while new explicit choices and custom dock contents persist. Weather clouds use a shaded, multi-lobed silhouette with a rounded base and enough horizontal room for their drift animation. Notification shadows are enabled in the stacked and expanded views, with the clipping wrappers removed and room below the stack for the shadow.

Validation: 70 unit tests, release lint, signed release build and three Android UI tests passed, including Dawn system wallpaper application and notification expand/collapse. Local emulator visual checks cover the new default background, four-app dock and notification shadow. This update remains local; no push is authorized.

### v1.1.12 — Independent wallpapers, custom photos and device theme

Wallpaper artwork and colors stay identical when changing theme. Dark mode changes foreground surfaces, with contrast adapted to the unmodified wallpaper. Status/navigation icon appearance follows the wallpaper brightness. Auto is the initial theme and follows Android's current device theme; existing installations migrate once to Auto, and subsequent explicit choices persist.

Home and lock-screen wallpaper sections each offer a custom photo picker. The chosen image is decoded with bounded dimensions, copied into private app storage, applied to the corresponding Android wallpaper target, and retained across restarts without needing ongoing access to the original URI. Built-in wallpaper thumbnails remain independent of a selected custom photo.

Selecting Light or Dark requests the system night mode through UiModeManager and verifies the result. If the device blocks the request, the app opens Display settings for the user to change it. Auto follows the device without changing its schedule. Android's [UiModeManagerService](https://android.googlesource.com/platform/frameworks/base/+/android16-release/services/core/java/com/android/server/UiModeManagerService.java) can restrict system theme changes on devices with night mode locked.

Validation: 71 unit tests, two Android tests (custom import, pixel-identical wallpaper rendering across themes, and wallpaper application), release lint and signed APK verification passed. Direct system-theme changes were blocked on the Android emulator; the app's Display settings fallback was exercised. Auto was restored and the device returned to its original light mode.

### v1.1.13 — Theme behavior restored

Theme selection again changes the complete launcher palette, including the wallpaper treatment: Dark mode applies the intended dark veil to Dawn, Original, Lavender and custom-photo wallpapers. Custom wallpaper upload and the separate Home/lock selections remain available.

Light, Dark and Auto now only control Fluid Launcher's own appearance. They no longer request a system-wide Android theme change or send the user to Android Display settings.

### v1.1.14 — Responsive home widgets

The analogue clock now initializes its seconds hand at the current time, then moves continuously through the nearest next second instead of sweeping a full dial after returning Home. The weather cloud has a shorter silhouette. Dark Dawn and Lavender wallpapers use a lighter dark veil so their artwork remains visible.

Today at Work includes an inline task field and add button in the widget itself. The notifications widget also reconciles with Android's active notification list every four seconds while visible, covering devices that omit a notification-removal callback.

### v1.1.15 — Music orbs and inline tasks

The widget gallery now includes two music-orb choices: Music has a glass backing, while Music (open) leaves the orb unframed. Both have an animated circular album-art illustration, open progress ring, and play/pause control. The artwork rotates and makes a subtle circular orbit while music plays.

Today at Work no longer opens its detail screen on a normal tap, keeping the inline task field and add button available directly in the widget.

### v1.1.16 — Pastel Glass wallpapers and blur

Wallpaper choices now begin with the supplied **Pastel Glass** image, followed by **Default** (the former Original artwork), Dawn and Lavender. Existing Original selections migrate to Default. Custom photo is the fifth, same-size tile in each picker rather than a separate full-width button.

Home and lock-screen wallpaper sections each have their own Blur wallpaper switch. It updates the launcher background for Home and applies a downsampled, soft blurred version to the matching Android system or lock-screen wallpaper.

### v1.1.17 — Plan page

Plan is a fourth page after Your Space. It brings together the day’s calendar, inline tasks, Quick Actions, and Favorites. Calendar widgets now launch the native calendar on tap rather than expanding. They load up to 30 events and show the first four by default, with See all / Collapse for longer schedules. Favorites uses the phone’s starred contacts after Contacts permission is granted.

### v1.1.18 — Refined app drawer

The app drawer now uses a compact search and filter header, outlined All/Work/Play/Tools tabs, a glass Suggested row of recent apps, and a clearer All apps grid. Existing Personal assignments remain available through the Play tab.

### v1.1.19 — Drawer favorites

The drawer’s former Suggested row is now Favorites. Long-press any app and select Add to favorites; the selection persists and appears at the top of All. The long-press action menu is now a full-screen glass page rather than a centered sheet.

### v1.1.20 — Full-height app drawer

The app drawer itself no longer sits in a rounded bottom sheet. It is a full-height wallpaper overlay with a compact search header and small pill-shaped All, Work, Play and Tools tabs matching the supplied reference.

### v1.1.21 — Page, accent and widget controls

Layout now includes Pages: add, move, and remove pages after the fixed Markets & News and Home pages. Appearance includes a global accent-color picker used by launcher buttons, tabs, and progress highlights. Long-press widgets to resize, move them up or down, or remove them; Plan’s widgets use the same controls and can now be removed.

### v1.1.26 — In-page arrange and removal

Arrange now happens on the page itself instead of opening a separate screen. Every widget card wobbles in place, a lifted card follows the finger, and displaced cards settle into their new slots with a quick spring rather than snapping. Cards are inset inside the scroll viewport so their glass shadows are no longer cropped. Removing a widget drops it into a red “Drag here to remove” bar that slides up from the bottom of the page, with a destination highlight while a card hovers over it. Layout reordering rewrites only the visible widget slots, so the fixed clock/weather row and the tiny control tiles keep their stored positions.
