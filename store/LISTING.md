# Play Store listing: Route

Copy for the Play Console. The README's comparison with the official DMRC app (permission counts,
ads, timings) is deliberately left out: Play's metadata policy rejects comparative and
unverifiable claims, and the listing must not suggest DMRC is involved. Don't use DMRC, Delhi
Metro or Rapid Metro logos or line-map artwork in any listing graphic.

## Text

**App name** (max 30): `Route: Delhi NCR Metro`

**Short description** (max 80):
```
Offline Delhi NCR metro routes and fares. No login, no ads, no permissions.
```

**Full description** (max 4000):
```
Route plans your Delhi metro trip in seconds. Pick two stations and see which line to take, which way to board, where to change, how long it takes and what it costs.

Covers Delhi Metro, Rapid Metro Gurugram, the Noida Metro Aqua Line and Namo Bharat.

WHAT YOU GET
• Fastest or fewer-changes routes, with Airport Express included when it is quicker
• Time, fare, stations and changes at a glance, every leg in its line colour
• Which way to board ("towards Noida Electronic City") and how long each change takes
• Smart card and token fares
• Search the way people talk: "CP", "HUDA City", "Race Course", "T3"
• Bookmarks for Home, Work, College and Gym. One tap starts your trip, and you can hold the app icon for "From Home"
• A map of every line and station, offline
• Share a route as a clean picture, with the directions as text
• Need a ticket? One tap opens DMRC's official WhatsApp ticketing. Payment stays between you and DMRC

PRIVATE BY DESIGN
Route asks for no permissions and has no internet access, so it cannot send anything anywhere. No login, no ads, no analytics. Your bookmarks and recent trips stay on your phone.

GOOD TO KNOW
Station and line data come from OpenStreetMap. Travel times are estimates. Delhi Metro fares follow the published distance slabs; fares marked ≈ (Rapid Metro across phases, Noida Metro) are not confirmed, and Airport Express and Namo Bharat fares are not included. Always check the fare shown at the station.

Route is an independent app. It is not affiliated with, endorsed by or sponsored by DMRC, NMRC, NCRTC or Rapid Metro Gurugram.

Map data © OpenStreetMap contributors.
```

**What's new** (max 500): `First release on Google Play.`

## Graphics

| Slot | File | Spec |
|---|---|---|
| App icon | `store/icon-512.png` | 512×512 PNG, full-bleed |
| Feature graphic | `store/feature-graphic-1024x500.png` | 1024×500, 24-bit PNG, no alpha |
| Phone screenshots | `docs/screenshots/*.png` (8) | 1080×1920 |

Regenerate the first two with `tools/make_store_assets.sh`.

## Suggested Console answers

Confirm each against the live form; Google changes the wording.

| Section | Answer |
|---|---|
| Category | Maps & Navigation (Travel & Local also fits) |
| Free or paid | Free (this cannot be changed to paid later) |
| Privacy policy URL | `https://github.com/HilalAhmad01/Route/blob/main/PRIVACY.md` (needs `PRIVACY.md` pushed to `main`) |
| Contact email | Your choice; shown publicly on the listing |
| Ads | No, the app contains no ads |
| App access | All functionality available without login or special access |
| Data safety: collect or share data | No. Nothing collected, nothing shared |
| Data safety: data deletion | Not applicable (no accounts, nothing stored off-device) |
| Content rating (IARC) | Utility/other app. Answer No to violence, sexual content, language, drugs, gambling, user-generated content, location sharing and digital purchases |
| Target audience | 13+ or 18+. Don't tick any under-13 group; that brings in the Families policy |
| Advertising ID | No, the app doesn't use it |
| Government app | No, independent of any government body |
| Financial features | None |
| Health, news, COVID | None |

## Signing and sideloaded v1.0

Releases up to v1.1 are signed with the same key (SHA-256 `DF:1B:C6:92:…:D0:08`). At first upload
the Console offers two choices:

- **Let Google generate the app signing key (default).** Your keystore becomes the upload key,
  which Google can reset if you lose it. People who sideloaded v1.0 must uninstall before
  installing from Play.
- **Upload your existing key as the app signing key** (the "export and upload a key from a Java
  keystore" option, with Google's PEPK tool). Sideloaded v1.0/v1.1 installs then update in
  place, but the key can never be reset if lost. Back up the keystore and its passwords either way.

## Before the first upload

- [x] DMRC data licensing question (platform numbers, fare overrides, per-hop distances): cleared by Hilal, 2026-11-05
- [ ] Push `PRIVACY.md` so the policy URL resolves
- [ ] Upload `dist/Route-1.1.aab` (built from git tag `v1.1`)
- [ ] Personal accounts created after 2023-11-13: run a closed test with 12 testers for 14 days
      before applying for production access
