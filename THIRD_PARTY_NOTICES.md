# Third-party notices

Metro bundles the following third-party material.

## Map data — OpenStreetMap
Station names, positions and line order in `app/src/main/assets/network.json` (and
`tools/raw/osm.json`) are derived from OpenStreetMap.
© OpenStreetMap contributors. Available under the Open Database License (ODbL) 1.0:
https://www.openstreetmap.org/copyright. The derived network data is offered under the same
licence.

## Platform numbers — Delhi Metro Rail Corporation
Boarding platform numbers (the `platforms` table in `network.json`, from
`tools/raw/dmrc_platforms.json`) were read from the journey planner on
https://www.delhimetrorail.com using `tools/fetch_platforms.py`. They are facts published by
DMRC, not covered by the ODbL above, and may change when DMRC reassigns platforms. Where the
planner gives no platform, or its number disagrees with other sources, the platform was filled in
by hand from delhimetrorail.info, yometro.com and Wikipedia; see `MANUAL_PLATFORMS` in
`tools/build_network.py`.

## Icons — Lucide
Line icons in `app/src/main/res/drawable/ic_*.xml` (except `ic_launcher_fg.xml`) are converted
from Lucide (https://lucide.dev). ISC License, see `licenses/Lucide-ISC.txt`.

## Fonts — IBM Plex Sans and IBM Plex Mono
Subsets of IBM Plex in `app/src/main/res/font` (full fonts in `tools/raw/fonts`).
© IBM Corp. SIL Open Font License 1.1, see `licenses/IBM-Plex-OFL.txt`.

## Design direction — unslop
The interface follows the unslop design method by Damir Sokolovsky (@heydamir),
https://github.com/heydamir/unslop. CC BY 4.0: https://creativecommons.org/licenses/by/4.0/

## Libraries
AndroidX, Jetpack Compose and kotlinx.serialization (Apache License 2.0), resolved by Gradle
at build time and not stored in this repository.
