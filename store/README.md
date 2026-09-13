# Store material

Play Store submission material. `listing/console-forms.md` holds pre-written answers for the
Play Console questionnaires and the step-by-step upload checklist, `policy/privacy-policy.md` the
privacy policy source, `graphics/` and `screenshots/` the assets.

The privacy policy is published from `docs/privacy-policy.html` via GitHub Pages at
https://ahmedthegeek.github.io/QuickLaunch/privacy-policy.html. When editing the policy, update
both the Markdown source here and the HTML in `docs/`, and bump the date in both.

The same listing text and graphics also live under `app/src/main/play/` in the layout the
Gradle Play Publisher plugin expects, so `./gradlew publishListing publishImages` can push them.

## Screenshots

The store screenshots are rendered mockups, not device captures: `screenshots/mockups/gen.py` rebuilds
the Quick Launch card from the real layout values (`dimens.xml`, `colors.xml`) in HTML, places it over a
Pixel-style home screen with real app icons, widgets and dock inside a device frame, adds the headline,
and `render.sh` screenshots each scene with headless Chrome at the exact Play sizes. `fetch-icons.sh`
downloads the third-party app icons from their Play listings into `mockups/icons/` (not committed).
The frosted layer behind the card is lighter than the app's real 24 dp blur so the home screen stays
legible in the store. Untouched device captures from v0.1.1 are kept in `screenshots/raw/`.

The 1024×500 feature graphic is a scene in the same generator and lands in `graphics/feature_1024x500.png`.
To change a caption, scene or the feature graphic, edit `SCENES` in `gen.py` and run `render.sh`.
