# Play Console forms – prepared answers for Quick Launch

## App details
- App name: Quick Launch
- Default language: English (United States)
- App or game: App
- Free or paid: Free
- Category: Tools
- Contact email: ahussein@awesomemotive.com
- Privacy policy URL: (host store/policy/privacy-policy.md, e.g. GitHub Pages)

## App content declarations
- Privacy policy: URL above
- Ads: No, this app does not contain ads
- App access: All functionality is available without special access (no login)
- Content rating questionnaire (IARC): Category "Utility, Productivity, Communication, or Other".
  Answer "No" to every question (no violence, sexuality, language, controlled substances, gambling,
  user interaction, sharing location, purchases). Expected rating: Everyone / PEGI 3.
- Target audience: 18 and over (simplest; avoids the Families policy). Not designed for children.
- News app: No
- COVID-19 contact tracing / status: No
- Data safety:
  - Does your app collect or share any of the required user data types? **No.**
    (All processing is on-device and never leaves the device; Google's definition of "collect" is
    transmission off the device. Usage statistics and app list are processed locally only.)
  - Is all user data encrypted in transit? N/A (no transmission)
  - Do you provide a way to request deletion? N/A (no collection); uninstall removes all local data.
- Government apps: No
- Financial features: None
- Health: No health features
- Advertising ID: Does not use advertising ID

## Permissions that Play may ask about
- android.permission.SYSTEM_ALERT_WINDOW: core feature (instant overlay). No declaration form needed.
- android.permission.PACKAGE_USAGE_STATS: core feature ("most used apps" ordering), optional, user grants in Settings. No declaration form, but be ready to describe: "Orders the app list by how often the user uses each app; processed on device only."
- <queries> for MAIN/LAUNCHER intents: standard for launchers; QUERY_ALL_PACKAGES is NOT requested, so no declaration form.

## Store listing assets (in store/)
- Icon 512x512: store/graphics/icon_512.png
- Feature graphic 1024x500: store/graphics/feature_1024x500.png
- Phone screenshots (1080x2400): store/screenshots/phone/
- 7-inch tablet (1812x2176): store/screenshots/tablet7/
- 10-inch tablet (2560x1600, 1600x2560): store/screenshots/tablet10/
- Title, short and full description: store/listing/*.txt

## Release
- Upload: app/build/outputs/bundle/release/app-release.aab (signed with the upload key in keystore/, see keystore.properties)
- Play App Signing: accept Google-generated app signing key on first upload; the upload key in keystore/ is what future uploads must be signed with. Back up keystore/upload-keystore.jks and keystore.properties somewhere safe.
- Testing track first: Internal testing (instant), then Closed testing if the account is new (12 testers, 14 days), then Production.
