# Quick Launch — technical notes

How it works, why it is built the way it is, and how to build it. For the short version see the
[README](../README.md).

## What it does

- Searches installed apps (personal and work profile) as you type. Ranking: exact > prefix >
  word prefix > initials (`mbs` → Meta Business Suite, `yt` → YouTube) > substring > fuzzy,
  with a boost for apps you launch often (7-day half-life).
- **Most used apps before you type.** With *Usage access* granted (tap the ★ row once, or
  Settings → Apps → Special access → Usage access), the empty list is ordered by device-wide usage
  over the last 14 days, blended with your Quick Launch history. Your own launches win after a few
  uses; without the permission the list falls back to Quick Launch history, then alphabetical.
- **Clipboard link.** If the clipboard holds a URL when the card opens, an "Open link" row sits
  above the app results while the query is empty; Enter opens it with `ACTION_VIEW`. The row
  carries the icon of the app that will handle the link (resolved off-main via the `<queries>`
  entry for BROWSABLE http/https), or a link glyph when no default handler is set. Android 10+
  releases the clipboard only to the focused window, so the read happens on window focus, and it is
  done at most once per clip (keyed by the clip timestamp) to keep the Android 12+ "pasted from your
  clipboard" toast to one per copy. Sensitive clips and clips the system has classified as URL-free
  are skipped from the description alone, without reading the content.
- **Calculator.** Input with at least one operation (`3x3`, `2^10`, `15% * 80`, `sqrt(2)`, `2pi`)
  shows its result as the first row; Enter copies it and closes. `Calculator` is a small recursive
  descent parser on the raw input (the ranker only sees normalized text, which drops symbols). A
  character-class check rejects most app queries before parsing, and a bare number is never a result.
- **Unit conversion.** `<amount> <unit> in|to|as <unit>`: length, mass, volume, area, speed, time,
  data, temperature, energy, pressure, angle. The amount can be any calculator expression. Fixed
  factor tables in `UnitConverter`; no currencies, since those need live rates.
- Enter launches the top or arrow-selected result. Up/Down (also Tab, Ctrl+N/P, Ctrl+J/K) move
  the selection. Ctrl+1..9 launch that row directly. Esc, Back, tapping outside, Home or Recents close it.
- **Pins.** The highlighted row carries a 48dp pin button at its trailing edge (outline when the
  app can be pinned, filled when it is pinned); Ctrl+D toggles the same row. On touch, type until
  the app is the top row, then tap the pin. Pinned apps form their own "Pinned" section at the top
  of the empty-query list, in the order they were pinned, with a hairline before the suggestions;
  the section exists only while something is pinned. While typing there is one ranked list and a
  pin is only a within-tier nudge (`Ranker.PIN_BOOST`), smaller than a heavily used app's frecency
  boost, so match quality still decides. The order is a list of entry keys in `pins.bin`;
  `PinStore.attach` writes each entry's position onto `AppEntry.pinOrder` after every load or
  revalidation so ranking reads a field, never a map. The button is laid out on every row and only
  its alpha and clickability change, so rows never relayout and taps on idle rows launch as usual.
  The header and hairline are plain children of the results view, re-inserted at a new child index
  only when the pinned boundary moves. The footer (physical keyboards only) shows Enter, Ctrl+D and
  Esc keycaps; the Ctrl+D hint hides itself when the row would not fit, using the widths of the laid
  out hints rather than re-measuring them.
- **Long-press a result** for a small menu with *App info* (the system screen, via
  `LauncherApps.startAppDetailsActivity`) and *Add to Home screen*. The menu is drawn inside the
  panel's own window (`RowMenu`): a PopupWindow would take window focus, and the overlay reads focus
  loss as "dismiss". A transparent catcher under the menu makes the first tap anywhere else close only
  the menu. Add to Home screen pins a shortcut published by Quick Launch whose intent is the app's own
  launcher activity (`HomeShortcuts`); only the default home app may pin another app's shortcuts. The
  icon is passed as the full 108dp adaptive layer so the home app masks it like the real one. Personal
  profile only, since the pinned shortcut runs in our user.
- **Long-press a result and drag it** to open it in split screen next to the app you came from.
  The long press opens the menu at once and keeps the row armed; move past the touch slop while still
  holding and the menu closes and the row starts a system drag instead (`ResultsView`), the way a home
  screen icon works. This uses the same system drag protocol a launcher uses, so Android shows
  its own drop zones. Personal profile apps only, Android 12+, and it needs instant mode: in the
  fallback activity the system pairs the drop with our own window, so the app simply opens full screen.
- Translucent floating card that follows the system light/dark setting, with blur behind (Android 12+). Full width on phones; on tablets, foldables
  and landscape it is a centered 560dp palette.
- No settings, no network, no analytics.

## Two ways it can appear

| Mode | How | Appearance |
|---|---|---|
| **Instant** (recommended) | Grant **Display over other apps**. Tap the hint at the bottom of the card, or Settings → Apps → Quick Launch → Display over other apps. | Drawn as a system overlay window: shows on the next frame, no animation at all. |
| Fallback | Nothing to grant. | Android forces a ~330 ms fade on activities that open in their own task, and apps cannot override it. Everything else is identical. |

Instant mode also falls back automatically over apps that hide non-system overlays (Settings,
permission dialogs, some secure screens): if the overlay is not focused within ~0.9 s of drawing,
it steps aside and the activity host opens instead.

## Ctrl+Space from any app (physical keyboards)

Enable **Quick Launch keyboard shortcut** under Settings → Accessibility → Installed apps. The card
offers a ⌨ row that first shows a disclosure of what the service does and does not see (Play's
prominent-disclosure rule for the AccessibilityService API); *Continue* takes you to Settings.
Ctrl+Space then opens Quick Launch from anywhere, and a second Ctrl+Space closes it.

**If the switch is greyed out with "Restricted setting":** Android 13+ blocks accessibility services
for apps installed outside an app store until you allow it once. Open Settings → Apps → Quick Launch →
⋮ (top right) → *Allow restricted settings*, then go back to Accessibility and enable it.

Two more things to know:

- Android normally uses Ctrl+Space to switch keyboard language. While the shortcut is on, Quick Launch
  takes it instead.
- This works through an accessibility service because that is the only hook that sees a key before the
  system does. The service filters exactly one key combination, subscribes to no accessibility events,
  and cannot read screen content (`canRetrieveWindowContent="false"`). Because it never starts an
  activity, video apps do not drop into picture-in-picture when you open Quick Launch this way.

## Triggering it on Samsung

- **Good Lock → One Hand Operation+**: pick a gesture → *Open app* → Quick Launch.
- **Side key**: Settings → Advanced features → Side button → Double press → Open app → Quick Launch.
- **Android 16 / One UI 8 keyboards**: Settings → General management → Physical keyboard →
  Keyboard shortcuts → assign Meta + a key to Quick Launch.
- Add Quick Launch to *Battery → Never sleeping apps* so One UI keeps the process warm.

## Performance (release build, API 36 emulator, instant mode)

| Metric | Result |
|---|---|
| Trigger → overlay on screen, process warm | 60–90 ms |
| Trigger → overlay on screen, process cold (AOT compiled) | 120–190 ms |
| Our own code in `Application.onCreate` | < 1 ms |
| Keystroke → list updated | < 1 ms, no frames over 16 ms |
| Enter → target activity started | ~9 ms |
| Keyboard after the card (Gboard, emulator) | 200–300 ms, does not block the card |

Why the keyboard is requested *after* the first frame: on Android 15+ an IME requested at window
creation joins the pending window transition, and WindowManager then holds the overlay until the
keyboard has drawn. Requesting it one frame later cut trigger-to-visible from ~300 ms to ~70 ms.

A baseline profile is bundled and installed on first run; Android compiles it during the next idle
background dexopt (usually overnight while charging). To get the fast cold start immediately:

```
adb shell cmd package compile -m speed-profile -f com.ahmedgeek.quicklaunch
```

## Build

```
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleRelease :app:testDebugUnitTest
adb install -r app/build/outputs/apk/release/app-release.apk
```

Release builds are signed with the key in `keystore.properties` when present, otherwise debug-signed
so the project builds on any machine. minSdk 26, targetSdk 35, Kotlin, Android Views only (no Compose, AppCompat,
Material or RecyclerView: fewer classes to load on a cold start).

## Layout

```
QuickLaunchApp        Application: singletons, cache warm-up, LauncherApps callback
EntryActivity         launcher entry, never draws: shows the overlay or the fallback activity
LaunchActivity        fallback host for the panel (activity window)
overlay/              OverlayController (TYPE_APPLICATION_OVERLAY window), OverlayRootView
ui/LauncherPanel      the search UI shared by both hosts: input, ranking, keys, launch
ui/ResultsView        8 pre-inflated rows shared by suggestions and apps, no adapter, no animations
ui/IconLoader         icons rasterized off-main, memory + disk cache
index/                AppIndex (enumerate, revalidate, snapshot), IndexStore (binary cache)
search/               TextNormalizer, Ranker (tiered scorer), FrecencyStore, PinStore
clipboard/            LinkDetector (pure URL check), ClipboardLinkSource (focus-gated read, per-clip cache)
launch/AppLauncher    LauncherApps.startMainActivity, handles work profiles
suggest/              Suggestion rows above the results; SuggestionSource per feature (Calculator, UnitConverter)
```

## Known limits

- Opening Quick Launch over a video app that auto-enters picture-in-picture (YouTube) sends that
  app into PiP. Any activity launched into a new task pauses the foreground app with "user leaving"
  semantics, which is the PiP trigger, and only the caller could suppress it. Deliberately left as
  is; the fix would be an assistant-role or Quick Settings tile trigger, which never start an activity.

- Background-activity-launch from an overlay relies on the "visible overlay window" exemption.
  Android logs it as allowed on API 36; if a future release removes it, the fallback activity
  mode still works.
- Gboard rebuilds its keyboard view on every show (150–500 ms on the emulator). Samsung Keyboard
  behaves differently; measure on device.

## License

GPL-3.0. See [LICENSE](../LICENSE).
