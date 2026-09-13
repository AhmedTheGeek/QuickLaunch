# Play Console – prepared answers and upload checklist for Quick Launch

Everything below the "Checklist" heading is the manual part in the Play Console. Everything the
repo can prepare is already done: signed AAB, listing text, graphics, privacy policy URL.

## Ready-made inputs

| Item | Value |
|---|---|
| App name | Quick Launch |
| Default language | English (United States) |
| App or game | App |
| Free or paid | Free (cannot be changed to paid later) |
| Category | Productivity (recommended; Tools is the fallback). No ads, no in-app purchases. |
| Contact email | me@ahmedgeek.com |
| Privacy policy URL | https://ahmedthegeek.github.io/QuickLaunch/privacy-policy.html |
| Package name | com.ahmedgeek.quicklaunch |
| Upload artifact | `release/QuickLaunch-v0.1.1.aab` (versionCode 2, versionName 0.1.1), signed with `keystore/upload-keystore.jks` |
| Upload key SHA-256 | 64:06:EA:2A:DC:0B:B3:F4:46:E4:56:24:58:A7:F1:0E:E1:03:CB:0C:38:88:34:FB:92:3B:5A:A2:EA:19:48:C6 |
| Store listing text | `store/listing/title.txt`, `short_description.txt`, `full_description.txt` (29 / 80 / 1767 chars, limits 30 / 80 / 4000) |
| Release notes | `app/src/main/play/release-notes/en-US/default.txt` |
| Icon 512×512 | `store/graphics/icon_512.png` |
| Feature graphic 1024×500 | `store/graphics/feature_1024x500.png` |
| Phone screenshots 1080×2400 | `store/screenshots/phone/` (5, rendered mockups) |
| 7-inch tablet 1812×2176 | `store/screenshots/tablet7/` (1) |
| 10-inch tablet 2560×1600 / 1600×2560 | `store/screenshots/tablet10/` (2) |

Screenshots are rendered mockups (24-bit PNG, no alpha) built by `store/screenshots/mockups/`; see
`store/README.md`. They show real third-party app icons, as launcher listings on Play commonly do.

## App content declarations (Policy > App content)

- **Privacy policy**: URL above.
- **Ads**: No, this app does not contain ads.
- **App access**: All functionality is available without special access (no login).
- **Content rating (IARC)**: Category "Utility, Productivity, Communication, or Other". Answer "No"
  to every question (no violence, sexuality, language, controlled substances, gambling, user
  interaction, location sharing, purchases). Expected rating: Everyone / PEGI 3.
- **Target audience and content**: 18 and over. Not designed for children. Avoids the Families policy.
- **News app**: No.
- **COVID-19 contact tracing / status**: No.
- **Data safety**:
  - Does your app collect or share any of the required user data types? **No.**
    Google defines "collect" as transmission off the device. The app has no INTERNET permission;
    the installed-app list, launch history and usage statistics are processed and stored locally only.
  - Encryption in transit / deletion request: not asked when nothing is collected. Uninstall removes all data.
- **Government apps**: No.
- **Financial features**: None.
- **Health**: No health features.
- **Advertising ID**: Does not use advertising ID.
- **Accessibility API usage** (the Console asks this because the app declares an AccessibilityService).
  - Is the app an accessibility tool (helps users with disabilities)? **No.** The manifest sets
    `isAccessibilityTool="false"`.
  - Purpose / core functionality (paste this):
    > Quick Launch is a keyboard-first app launcher. The optional accessibility service exists only
    > to detect the Ctrl+Space key combination on a physical keyboard so the launcher can be opened
    > from any app, the same way desktop launchers work. It requests only
    > flagRequestFilterKeyEvents, subscribes to no accessibility event types, cannot retrieve window
    > content (canRetrieveWindowContent=false), and stores nothing. The user enables it explicitly in
    > Settings; the app's own row explains what it does before sending them there, and the service
    > description shown by Android repeats it.
  - Prominent disclosure and consent: shown in-app (the "Enable Ctrl+Space shortcut" row) and in the
    system service description (`a11y_description` string). The privacy policy also covers it.
  - If reviewers push back, the fallback is to remove the service and rely on the launcher-icon
    trigger only; the feature is optional and the rest of the app does not depend on it.

## Permissions Play may ask about

- `SYSTEM_ALERT_WINDOW`: core feature (instant overlay). No declaration form.
- `PACKAGE_USAGE_STATS`: core, optional feature ("most used apps" ordering), user grants in Settings.
  No declaration form; if asked: "Orders the app list by how often the user uses each app; processed
  on device only."
- `<queries>` for MAIN/LAUNCHER intents only. `QUERY_ALL_PACKAGES` is not requested, so no
  declaration form.

## Checklist (manual, in this order)

1. **Developer account** at https://play.google.com/console. One-time $25 fee. A personal account
   must verify identity and, for new personal accounts, run a closed test with at least 12 testers
   for 14 days before production is unlocked. An organisation account skips the 12-tester rule but
   needs a D-U-N-S number.
2. **Create app**: name Quick Launch, English (US), App, Free. Accept the declarations.
3. **Set up your app** (dashboard tasks): fill the App content section using the answers above,
   then Store settings (category, contact email), then Main store listing (text and graphics from the
   table).
4. **Play App Signing**: on the first upload choose "Let Google manage and protect your app signing
   key" (Google-generated key). The key in `keystore/` then acts as the upload key; every future AAB
   must be signed with it. Back up `keystore/upload-keystore.jks` and `keystore.properties` somewhere
   outside this machine (password manager or encrypted drive). Losing them means an upload-key reset
   request to Google.
5. **Internal testing release**: Testing > Internal testing > Create release > upload
   `release/QuickLaunch-v0.1.1.aab`, paste the release notes, roll out. Add yourself as a tester and
   install from the opt-in link to confirm the Play-signed build works (overlay, usage access,
   Ctrl+Space).
6. **Closed testing** (only if the account is new and personal): promote the internal release to a
   closed track, add 12+ testers, wait 14 days, then apply for production access.
7. **Production**: promote the same release. First review usually takes 1 to 7 days.

## Optional: upload from the command line

The Gradle Play Publisher plugin is wired in `app/build.gradle.kts` and switches on when
`play/service-account.json` exists (gitignored).

1. Google Cloud Console > create a service account, create a JSON key, save it as
   `play/service-account.json`.
2. Play Console > Users and permissions > Invite new users > the service account email > grant
   "Release to testing tracks", "Manage store presence" (and "Release to production" later).
   The app must already exist in the Console (step 2 above) and have one manual upload done first,
   because the API cannot create the app or accept Play App Signing.
3. Then:
   ```
   ./gradlew publishBundle                    # upload AAB to the internal track
   ./gradlew publishListing publishImages     # push text, icon, feature graphic, screenshots
   ./gradlew promoteArtifact --from-track internal --promote-track production
   ```

## Next version

Bump `versionCode` (must increase) and `versionName` in `app/build.gradle.kts`, update the release
notes file, `./gradlew :app:bundleRelease`, upload `app/build/outputs/bundle/release/app-release.aab`.
