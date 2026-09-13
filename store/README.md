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

`screenshots/raw/` holds the untouched device captures. The store versions in `screenshots/phone`,
`tablet7` and `tablet10` add a caption band (Helvetica Bold on #0E0E10) and rounded corners, and are
flattened to 24-bit PNG. The recipe is a small ImageMagick script: canvas of the target size, caption
annotated in the top band, screenshot resized and composited below with a rounded-corner mask,
`-alpha off`. Phone 1080×2400: band 400, shot 860 wide, 66 pt. 7-inch 1812×2176: band 360, shot 1460,
84 pt. 10-inch landscape 2560×1600: band 260, shot 2000, 80 pt. 10-inch portrait 1600×2560: band 400,
shot 1300, 84 pt. Keep caption lines under about 27 characters on phone.
