# Quick Launch

Keyboard-first app launcher overlay for Android. Summon it, type a few letters, press Enter.
Not a home screen replacement.

```
mes█
  Messenger      ↵
  Messages
  Meta Business Suite
```

## Install

Download the latest APK from the [Releases](https://github.com/AhmedTheGeek/QuickLaunch/releases) page
and open it on your phone (allow "Install unknown apps" for your browser or file manager when asked).
Then open Quick Launch once and tap the ⚡ row to grant *Display over other apps* for instant mode.

## What it does

- Searches installed apps (personal and work profile) as you type. Ranking: exact > prefix >
  word prefix > initials (`mbs` → Meta Business Suite, `yt` → YouTube) > substring > fuzzy,
  with a boost for apps you launch often (7-day half-life).
- **Most used apps before you type.** With *Usage access* granted (tap the ★ row once, or
  Settings → Apps → Special access → Usage access), the empty list is ordered by device-wide usage
  over the last 14 days, blended with your Quick Launch history. Your own launches win after a few
  uses; without the permission the list falls back to Quick Launch history, then alphabetical.
- Enter launches the top or arrow-selected result. Up/Down (also Tab, Ctrl+N/P, Ctrl+J/K) move
  the selection. Esc, Back, tapping outside, Home or Recents close it.
- **Long-press a result and drag it** to open it in split screen next to the app you came from.
  This uses the same system drag protocol a launcher uses, so Android shows its own drop zones.
  Personal profile apps only, Android 12+, and it needs instant mode: in the fallback activity the
  system pairs the drop with our own window, so the app simply opens full screen.
- Dark translucent card with blur behind (Android 12+). Full width on phones; on tablets, foldables
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

Enable **Quick Launch keyboard shortcut** under Settings → Accessibility → Installed apps (the card
offers a ⌨ row that takes you there). Ctrl+Space then opens Quick Launch from anywhere, and a second
Ctrl+Space closes it.

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
ui/ResultsView        8 pre-inflated rows, no adapter, no animations
ui/IconLoader         icons rasterized off-main, memory + disk cache
index/                AppIndex (enumerate, revalidate, snapshot), IndexStore (binary cache)
search/               TextNormalizer, Ranker (tiered scorer), FrecencyStore
launch/AppLauncher    LauncherApps.startMainActivity, handles work profiles
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

GPL-3.0. See [LICENSE](LICENSE).
