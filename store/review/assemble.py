#!/usr/bin/env python3
"""Cut part1.mp4 (and part2.mp4 if present) into the final review video with captions.

part1.marks holds "<label> <seconds>" lines written by record-disclosure.sh at each tap. Each tap
is kept from just before it until the screen has settled (DWELL), which removes the seconds the
script spends reading the UI tree between taps. part2.mp4 (Ctrl+Space in another app) is appended
whole with its own caption. Output: accessibility-disclosure.mp4 next to this script.
"""
import os, subprocess, sys

HERE = os.path.dirname(os.path.abspath(__file__))
os.chdir(HERE)
FONT = "/System/Library/Fonts/Helvetica.ttc"

# label -> (seconds kept after the tap, caption)
STEPS = {
    "open1":   (2.5, "1. Opening Quick Launch from the home screen"),
    "row1":    (9.0, "2. Tapping 'Open with Ctrl+Space' shows the\naccessibility disclosure"),
    "decline": (2.0, "3. 'Not now' declines. Nothing is enabled\nand the app closes."),
    "open2":   (2.5, "4. Opening the app again: the setup row\nis still offered"),
    "row2":    (4.0, "5. Tapping it shows the same disclosure again"),
    "accept":  (5.0, "6. 'Continue' is the consent. It opens\nSettings > Accessibility"),
    "service": (3.5, "7. The service page repeats what the service\ncan and cannot see"),
    "toggle":  (3.0, "8. Turning the switch on shows Android's\nown confirmation"),
    "allow":   (3.5, "9. 'Allow' grants the accessibility permission"),
}
PART2_CAPTION = ("10. Core feature: in another app, pressing Ctrl+Space\n"
                 "on the physical keyboard opens Quick Launch.\n"
                 "A second Ctrl+Space closes it.\n"
                 "The service only ever sees this key combination.")
LEAD = 0.4  # seconds kept before each tap so the tap itself is visible

def caption(text):
    text = text.replace("'", "’").replace(":", "\\:")
    return (f"drawtext=fontfile={FONT}:text='{text}':fontsize=40:fontcolor=white:line_spacing=12:text_align=center:"
            "box=1:boxcolor=black@0.65:boxborderw=22:x=(w-text_w)/2:y=110")

marks = [l.split() for l in open("part1.marks") if l.strip()]
marks = [(l, float(t)) for l, t in marks]
segments = []
for i, (label, t) in enumerate(marks):
    if label not in STEPS:
        continue
    dwell, text = STEPS[label]
    start = max(0.0, t - LEAD)
    end = t + dwell
    if i + 1 < len(marks):
        end = min(end, marks[i + 1][1] - LEAD)  # never run into the next tap's lead-in
    segments.append((start, end, text))

inputs, filters, labels = ["-i", "part1.mp4"], [], []
for n, (start, end, text) in enumerate(segments):
    # screenrecord emits frames only when the screen changes; fps=30 first so trimming by time keeps still periods.
    filters.append(f"[0:v]fps=30,trim=start={start:.2f}:end={end:.2f},setpts=PTS-STARTPTS,{caption(text)}[s{n}]")
    labels.append(f"[s{n}]")
if os.path.exists("part2.mp4"):
    inputs += ["-i", "part2.mp4"]
    filters.append(f"[1:v]fps=30,scale=1080:2400,setpts=PTS-STARTPTS,{caption(PART2_CAPTION)}[p2]")
    labels.append("[p2]")
filters.append("".join(labels) + f"concat=n={len(labels)}:v=1:a=0[out]")

cmd = ["ffmpeg", "-y", "-v", "error", *inputs, "-filter_complex", ";".join(filters), "-map", "[out]",
       "-c:v", "libx264", "-preset", "medium", "-crf", "20", "-pix_fmt", "yuv420p", "-r", "30",
       "accessibility-disclosure.mp4"]
subprocess.run(cmd, check=True)
dur = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0",
                      "accessibility-disclosure.mp4"], capture_output=True, text=True).stdout.strip()
print(f"accessibility-disclosure.mp4: {len(labels)} segments, {float(dur):.0f}s")
