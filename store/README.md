# Store material

Play Store submission material. `listing/console-forms.md` holds pre-written answers for the
Play Console questionnaires and the step-by-step upload checklist, `policy/privacy-policy.md` the
privacy policy source, `graphics/` and `screenshots/` the assets.

The privacy policy is published from `docs/privacy-policy.html` via GitHub Pages at
https://ahmedthegeek.github.io/QuickLaunch/privacy-policy.html. When editing the policy, update
both the Markdown source here and the HTML in `docs/`, and bump the date in both.

The same listing text and graphics also live under `app/src/main/play/` in the layout the
Gradle Play Publisher plugin expects, so `./gradlew publishListing publishImages` can push them.
