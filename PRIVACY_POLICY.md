# Privacy Policy for Fluid Launcher

**Effective date: November 6, 2026**

This policy describes how Fluid Launcher (Android application ID `com.cry.fluid`) handles information. Fluid Launcher is maintained by **cryadhu**, the maintainer of the [Fluid Launcher GitHub repository](https://github.com/cryadhu/fluid-launcher).

## Overview

Fluid Launcher is an Android home-screen launcher with customizable pages, app collections and widgets. It does not require an account or provide a developer-operated cloud synchronization service. Launcher settings and personal widget data are stored on your device. Some optional features request information from external services, as explained below.

Fluid Launcher does not sell your personal information. The app does not integrate advertising, developer-operated usage analytics or crash-reporting services. This does not prevent Android, Google Play services or external providers from processing information under their own policies.

## Information used on your device

Depending on the features you use and the permissions you grant, Fluid Launcher accesses:

- **Installed apps:** App names, package identifiers and icons to display and search your apps, launch them, organize collections and manage favorites and dock shortcuts. The launcher does not upload your installed-app list to its developer.
- **Calendar:** Event details to display your agenda. Calendar information is not uploaded by the launcher.
- **Contacts:** Contact names and phone numbers, including starred contacts, to display favorite contacts and open your chosen calling application. Contact information is not uploaded to the developer; initiating a call passes the selected number to the receiving calling app.
- **Notifications and media:** With notification access enabled, notification titles, text, originating apps and timestamps are used for the notification widget and notification dots. Media-session information, including title, artist, album artwork and playback state, supports music widgets and controls. Notification content is held in memory rather than written to the launcher's persistent storage and is not uploaded by the launcher.
- **Activity and device status:** Step-counter readings support activity widgets. Step baselines and stand-hour summaries are stored locally. Battery level, charging status, storage information, connectivity and sound settings support device widgets and controls.
- **User-entered information:** Tasks, notes where available, alarms, labels, repeat days, timers, stopwatch state, world-clock cities, water counts, focus sessions and appearance/layout preferences are stored locally. Importing a system alarm reads only the next alarm time Android makes available, not the other app's complete alarm list.
- **Selected images:** A photo you choose for wallpaper is read and copied into private app storage for display and wallpaper application. The launcher does not upload your wallpaper photos.

## Location and weather

Automatic weather uses a foreground location lookup after you grant location permission. The launcher sends the resulting latitude and longitude to **Open-Meteo** to retrieve the forecast. Accuracy depends on Android's permission settings and location provider; precise coordinates may be available if you grant precise location. Android's geocoding service may also receive coordinates to determine a city name.

If you enter a city instead, that city name is sent to Open-Meteo's geocoding service, and the resulting coordinates are used for the forecast. You can use manual city weather instead of granting location access.

The launcher does not save a location-coordinate history or register background location tracking. Location permission may also be needed by Android for nearby Wi-Fi discovery where that feature is available.

## Internet connections and third parties

When relevant features are used, the app can contact:

- **Open-Meteo:** City searches and weather forecasts.
- **Yahoo Finance:** Requested stock symbols, chart ranges and market information.
- **The Guardian and Hacker News/Algolia:** Selected news-topic feeds and stories.
- **Frankfurter:** Currency-rate information.
- **ESPN:** Sports information and associated images where those features are available.
- **Image hosts:** Images supplied by news or sports providers.

These providers receive normal network-request information, including your IP address, the requested resource and an app-identifying user-agent. Requests can reveal the city/coordinates, stock symbols or content topics needed for the feature. Providers may keep connection logs according to their own policies; Fluid Launcher does not control their retention practices. Public content and images may be cached on your device.

Opening an article, translation service, browser, calling app or other external app transfers control to that service or app, whose privacy policy applies. Voice input or voice recording, where available, is delegated to your device's speech-recognition or recording application; the selected provider may process audio. Fluid Launcher does not run its own audio-upload service.

The app includes Google Play services document/QR scanning components. Where scanning is available, Google Play services provides the scanning interface and may download required components. Scanned results are used locally or passed to a viewer when you choose to open them. The launcher does not upload scanned documents or codes to its developer. Google Play services' own data handling is governed by Google's policies.

## Permissions and special access

Optional permissions enable features such as calendar and favorite-contact widgets, activity tracking, location-based weather, flashlight/camera actions, notifications and nearby-device controls. Android manages these permissions, and you can deny or revoke them in system settings. A feature may be unavailable without its required permission.

Notification access enables notification display, dismissal and media controls. Exact-alarm and full-screen-alarm access allow alarms and timers to ring on time and appear on the lock screen. Wallpaper and system-settings access support wallpaper and brightness controls. App removal actions use Android's uninstall confirmation rather than silently removing applications.

## Storage, retention and deletion

Settings, tasks, saved contacts where applicable, clock data, widget records, wallpaper copies and content caches remain in the app's local storage until removed, replaced or cleared. Disabling a permission stops the corresponding permitted access but does not necessarily delete previously saved local items.

You can remove supported items within the app. To delete all launcher-managed local data, use **Android Settings → Apps → Fluid Launcher → Storage → Clear storage**, or uninstall the app. Android automatic app backup is disabled in the app configuration. Device manufacturers may separately control device-to-device transfer behavior.

Clearing the app does not delete original contacts/calendar events, an alarm in another Clock app, wallpaper already applied to Android, exported files or data held by external providers. Manage those items in the responsible app or service. There is no Fluid Launcher account or developer-hosted user database to delete.

## Security

App-private storage and Android's permission protections help restrict access to local data. The launcher's network requests use HTTPS, and cleartext network traffic is disabled in its configuration. No storage or transmission method can be guaranteed completely secure.

## Children

Fluid Launcher is not specifically directed at children. It has no developer-operated account registration, advertising or personal-data submission service intended for children. External services and device applications have their own age requirements and privacy policies.

## Changes to this policy

Updates will be published on this page with a revised effective date. Review this policy when updating the app or enabling a new feature.

## Contact

For privacy questions, contact the maintainer through the [Fluid Launcher GitHub issue tracker](https://github.com/cryadhu/fluid-launcher/issues). GitHub issues are public: do not include passwords, contact lists, location details or other sensitive personal information. GitHub's own privacy policy applies when using its services.
