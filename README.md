# Metro

Offline route planner for Delhi Metro, Rapid Metro Gurugram, Noida Aqua Line and Namo Bharat.
No internet permission, no accounts, no ads, no analytics. The only permission is approximate
location, requested when you tap "Nearest station".

## Build

```bash
./gradlew :app:testDebugUnitTest :app:assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

Needs JDK 17 and the Android SDK (`local.properties` points at it).

## Network data

`tools/fetch_osm.py` pulls route relations from OpenStreetMap, and `tools/build_network.py`
turns them into `app/src/main/assets/network.json` (stations, line patterns, walks,
interchange times, fare rules). Rerun both when a new section opens, then rebuild.

Hand fixes live in `build_network.py`: Rapid Metro loop direction, station renames,
inter-operator walking links and interchange times. Travel times are a distance model
calibrated against the official app; `RouterTest` pins the official results.

## Credits

Design direction: [unslop](https://github.com/heydamir/unslop) by Damir Sokolovsky, CC BY 4.0.
Icons: Lucide (ISC). Type: IBM Plex (OFL). Map data © OpenStreetMap contributors (ODbL).
Not affiliated with DMRC, NMRC, NCRTC or Rapid Metro.
