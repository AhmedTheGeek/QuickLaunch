# Play review video: accessibility prominent disclosure

The Play Console asks for a video because the app declares an AccessibilityService with
`isAccessibilityTool="false"`. Google's guidance says the video must show: the app opening, the
path to the disclosure, the whole disclosure text, the consent flow including the permission grant,
the decline flow including reaching the disclosure again, and a core feature that uses the service.

Files here (`*.mp4` are gitignored):

- `record-disclosure.sh [--reset] [package]`: drives the emulator over adb and screen-records
  part 1 (everything except the key press). `--reset` clears the app's data and disables the
  service so the setup row shows again. Writes `part1.mp4` and `part1.marks` (tap instants).
- `part2.mp4`: the core feature. Record it by hand on the emulator or a phone with a keyboard:
  open another app, press Ctrl+Space, Quick Launch appears, press it again, it closes.
  On a Mac host with the emulator, two settings must change first or the key never reaches Android:
  untick *System Settings > Keyboard > Keyboard Shortcuts > Input Sources > Select the previous
  input source* (Ctrl+Space), and in the emulator set *Extended controls > Settings > General > Send
  keyboard shortcuts to: Virtual device* (otherwise the emulator window eats every Control combo).
  Keys injected with `adb shell input` bypass the accessibility key filter, so they cannot be used.
  Start with `adb shell screenrecord /sdcard/p2.mp4`, press the keys, stop with Ctrl+C, `adb pull`.
- `assemble.py`: trims the dead time between taps in part 1, appends part 2 when present, burns
  in numbered captions, and writes `accessibility-disclosure.mp4`.

Upload the result unlisted to YouTube or to Google Drive with link sharing, and paste the link into
the Accessibility declaration in Play Console (App content > Accessibility API usage).
