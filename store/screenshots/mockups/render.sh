#!/bin/zsh
# Renders every scene in gen.py with headless Chrome at exact Play sizes, then copies the PNGs into
# store/screenshots/ and app/src/main/play/. Needs Google Chrome, ImageMagick (magick), python3, and icons/ (fetch-icons.sh).
set -e
cd "$(dirname "$0")"
CH="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
python3 gen.py > scenes.tsv
mkdir -p out
while IFS=$'\t' read -r fn rel W H; do
  mkdir -p "out/$(dirname $rel)"
  "$CH" --headless=new --disable-gpu --hide-scrollbars --allow-file-access-from-files --force-device-scale-factor=1 \
        --window-size=$W,$H --virtual-time-budget=12000 --screenshot="out/$rel" "file://$fn" 2>/dev/null
  magick "out/$rel" -alpha off -define png:color-type=2 "../$rel"
  echo "rendered $rel"
done < scenes.tsv
P=../../../app/src/main/play/listings/en-US/graphics
rm -f $P/phone-screenshots/*.png $P/seven-inch-screenshots/*.png $P/ten-inch-screenshots/*.png
i=1; for f in ../phone/*.png; do cp "$f" $P/phone-screenshots/$i.png; i=$((i+1)); done
cp ../tablet7/01_fold.png $P/seven-inch-screenshots/1.png
cp ../tablet10/01_landscape.png $P/ten-inch-screenshots/1.png; cp ../tablet10/02_portrait.png $P/ten-inch-screenshots/2.png
