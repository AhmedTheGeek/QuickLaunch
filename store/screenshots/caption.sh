#!/bin/zsh
# caption.sh <in.png> <out.png> <W> <H> <band> <shot_w> <fontsize> <radius> <caption text with \n>
in=$1; out=$2; W=$3; H=$4; band=$5; sw=$6; fs=$7; rad=$8; text=$9
BG='#0E0E10'; FG='#F2F2F7'
# Rounded-corner screenshot
magick "$in" -resize "${sw}x" \( +clone -alpha extract -draw "fill black polygon 0,0 0,$rad $rad,0 fill white circle $rad,$rad $rad,0" \( +clone -flip \) -compose Multiply -composite \( +clone -flop \) -compose Multiply -composite \) -alpha off -compose CopyOpacity -composite /tmp/ql_shot.png
# Canvas + caption + shot; drop alpha for Play
magick -size ${W}x${H} xc:"$BG" \
  -font Helvetica-Bold -pointsize $fs -fill "$FG" -interline-spacing -8 -gravity North -annotate +0+$((band/2 - fs)) "$text" \
  /tmp/ql_shot.png -gravity North -geometry +0+$band -composite \
  -alpha off -define png:color-type=2 "$out"
