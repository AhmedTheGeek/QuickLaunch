#!/usr/bin/env bash
# Records the Play "prominent disclosure" review video for the AccessibilityService declaration.
#
# Part 1 (this script, fully driven over adb): open the app from the home screen, tap the Ctrl+Space
# setup row, show the disclosure, decline ("Not now"), open the app again and reach the disclosure a
# second time, consent ("Continue"), enable the service in Settings and accept the system dialog.
# Part 2 (Ctrl+Space itself) needs a real keyboard press; see README.md next to this script.
#
# Usage: store/review/record-disclosure.sh [--reset] [package]
#   --reset   clear the app's data and disable the service first, so the setup row is shown again.
#   package   defaults to com.ahmedgeek.quicklaunch.debug (the emulator build).
# Output: store/review/part1.mp4 and part1.marks (label + second offset of each tap; assemble.py trims
# the dead time between taps and adds captions).
set -euo pipefail
cd "$(dirname "$0")"

RESET=0
[[ "${1:-}" == "--reset" ]] && { RESET=1; shift; }
PKG="${1:-com.ahmedgeek.quicklaunch.debug}"
SERVICE="$PKG/com.ahmedgeek.quicklaunch.shortcut.KeyboardShortcutService"
OUT=part1
DEVICE_FILE=/sdcard/ql-review-$OUT.mp4

dump() { adb exec-out uiautomator dump /dev/tty 2>/dev/null; }
# center <grep pattern matching one node>: prints "x y" of the node's centre.
center() {
  local node= try
  for try in 1 2 3 4 5 6; do                       # the dump fails while the screen is still animating
    node=$(dump | tr '>' '\n' | grep -m1 -- "$1") && break
    sleep 0.7
  done
  [[ -n "$node" ]] || { echo "node not found: $1" >&2; return 1; }
  echo "$node" | grep -o 'bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"' \
    | sed -E 's/bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]"/\1 \2 \3 \4/' \
    | awk '{print int(($1+$3)/2), int(($2+$4)/2)}'
}
tap() { adb shell input tap $(center "$1"); }
T0=
mark() { printf '%s %.1f\n' "$1" "$(echo "$(date +%s.%N) - $T0" | bc)" >> $OUT.marks; }

if (( RESET )); then
  adb shell am force-stop "$PKG"
  adb shell pm clear "$PKG" >/dev/null
  current=$(adb shell settings get secure enabled_accessibility_services | tr -d '\r')
  remaining=$(echo "$current" | tr ':' '\n' | grep -v "^$SERVICE$" | paste -sd: -)
  adb shell settings put secure enabled_accessibility_services "${remaining:-null}"
fi
adb shell am force-stop com.android.settings          # so Settings opens on the Accessibility list, not a remembered sub-page
adb shell input keyevent KEYCODE_HOME; sleep 1

rm -f $OUT.marks
adb shell screenrecord --bit-rate 6000000 --time-limit 170 "$DEVICE_FILE" &
REC=$!
sleep 2
T0=$(date +%s.%N)

tap 'text="Quick Launch"'; mark open1; sleep 2.5
tap "resource-id=\"$PKG:id/shortcut_hint\""; mark row1; sleep 7          # disclosure, first time: leave time to read it
tap "resource-id=\"$PKG:id/disclosure_decline\""; mark decline; sleep 2     # "Not now": back to the home screen
tap 'text="Quick Launch"'; mark open2; sleep 2.5                           # the row is still offered
tap "resource-id=\"$PKG:id/shortcut_hint\""; mark row2; sleep 4          # disclosure again
tap "resource-id=\"$PKG:id/disclosure_accept\""; mark accept; sleep 3      # Settings > Accessibility
if ! dump | grep -q 'text="Use Quick Launch keyboard shortcut"'; then
  tap 'text="Quick Launch keyboard shortcut"'; mark service; sleep 3   # service page with the system description
fi
tap 'class="android.widget.Switch"'; mark toggle; sleep 3                   # system confirmation dialog
tap 'text="Allow"'; mark allow; sleep 3.5                                  # switch is on
mark end

adb shell pkill -INT screenrecord || true
wait $REC || true
sleep 1
adb pull "$DEVICE_FILE" $OUT.mp4 >/dev/null
adb shell rm "$DEVICE_FILE"
echo "recorded $OUT.mp4"; cat $OUT.marks
