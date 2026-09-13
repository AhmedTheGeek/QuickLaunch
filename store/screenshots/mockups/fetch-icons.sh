#!/bin/zsh
# Downloads the app icons the mockups show, from each app's Google Play listing (og:image), into ./icons.
# Icons are third-party artwork and are not committed; run this before render.sh.
cd "$(dirname "$0")" && mkdir -p icons && cd icons
for pkg in $(grep -o '"[a-zA-Z0-9_.]*\.[a-zA-Z0-9_.]*"' ../gen.py | tr -d '"' | grep -E '^(com|org)\.' | sort -u); do
  [ -f "$pkg.png" ] && continue
  url=$(curl -sL -A "Mozilla/5.0" "https://play.google.com/store/apps/details?id=$pkg&hl=en" | grep -o '<meta property="og:image" content="[^"]*"' | head -1 | sed 's/.*content="//;s/"$//')
  if [ -n "$url" ]; then curl -sL -o "$pkg.png" "${url%%=*}=w512" && echo "ok  $pkg"; else echo "MISSING  $pkg"; fi
done
