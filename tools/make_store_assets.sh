#!/usr/bin/env bash
# Renders the Play Store graphics from store/icon.svg and store/feature-graphic.html.
# Needs rsvg-convert, ImageMagick and Google Chrome/Chromium.
set -euo pipefail
cd "$(dirname "$0")/.."

CHROME=$(command -v google-chrome-stable || command -v chromium || command -v google-chrome)

# 512x512 app icon: full-bleed square, Play applies its own mask.
rsvg-convert -w 512 -h 512 store/icon.svg -o store/icon-512.png

# 1024x500 feature graphic: 24-bit PNG, no alpha.
tmp=$(mktemp -d)
"$CHROME" --headless=new --disable-gpu --hide-scrollbars --force-device-scale-factor=1 \
  --allow-file-access-from-files --window-size=1024,500 \
  --screenshot="$tmp/feature.png" "file://$PWD/store/feature-graphic.html" >/dev/null 2>&1
magick "$tmp/feature.png" -background '#0C0C0D' -alpha remove -alpha off -type TrueColor \
  PNG24:store/feature-graphic-1024x500.png
rm -rf "$tmp"

identify store/icon-512.png store/feature-graphic-1024x500.png
