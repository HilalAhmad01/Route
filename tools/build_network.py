"""Turn tools/raw/osm.json into the app's bundled network file.

Output: app/src/main/assets/network.json

Model
- station: one physical station complex (interchanges merged across lines of
  the same system; stations of different systems are linked by walks).
- pattern: one direction of one line, as an ordered list of station ids with
  the distance of every hop. Loops and branches are separate patterns.
- walk: a timed link between two distinct stations (inter-system changes).

Travel times are a calibrated distance model (see TIME below) checked against
the official app; fares are computed in the app from the rules in FARES.
"""
import json
import math
import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parent.parent
RAW = ROOT / "tools" / "raw" / "osm.json"
OUT = ROOT / "app" / "src" / "main" / "assets" / "network.json"

# OSM relation id -> (line id, towards override or None). One entry per direction.
PATTERNS = {
    # Red
    447214: ("red", None), 8037718: ("red", None),
    # Yellow
    447210: ("yellow", None), 8037712: ("yellow", None),
    # Blue main + Vaishali branch (branch trains run through towards Dwarka Sector 21)
    8037669: ("blue", None), 447209: ("blue", None),
    8037670: ("blue", "Dwarka Sector 21"), 2535795: ("blue", None),
    # Green main + Kirti Nagar branch
    8037687: ("green", None), 8037666: ("green", None),
    8037675: ("green", None), 2535796: ("green", None),
    # Violet
    2535797: ("violet", None), 8037705: ("violet", None),
    # Pink (ring with a spur to Shiv Vihar)
    8242437: ("pink", None), 8241298: ("pink", None),
    # Magenta (two open sections)
    8385429: ("magenta", None), 8385430: ("magenta", None),
    20300114: ("magenta", None), 20300115: ("magenta", None),
    # Grey
    3537978: ("grey", None), 8051592: ("grey", None),
    # Airport Express
    2535798: ("airport", None), 8037715: ("airport", None),
    # Aqua (NMRC)
    9268568: ("aqua", None), 9268569: ("aqua", None),
    # Namo Bharat (RRTS) – full corridor only; depot shuttles are subsets
    17337287: ("rrts", None), 17337288: ("rrts", None),
}

LINES = [
    # id, name, short, colour, system
    ("red", "Red Line", "Red", "#E53935", "dmrc"),
    ("yellow", "Yellow Line", "Yellow", "#FFD600", "dmrc"),
    ("blue", "Blue Line", "Blue", "#2F6FE4", "dmrc"),
    ("green", "Green Line", "Green", "#2E9D4F", "dmrc"),
    ("violet", "Violet Line", "Violet", "#7E4DB8", "dmrc"),
    ("pink", "Pink Line", "Pink", "#F27BA6", "dmrc"),
    ("magenta", "Magenta Line", "Magenta", "#C2338B", "dmrc"),
    ("grey", "Grey Line", "Grey", "#9A9EA6", "dmrc"),
    ("airport", "Airport Express", "Airport", "#F28C28", "dmrc_airport"),
    ("rapid", "Rapid Metro", "Rapid", "#3E86C6", "rmgl"),
    ("aqua", "Aqua Line", "Aqua", "#21B6C4", "nmrc"),
    ("rrts", "Namo Bharat", "Namo Bharat", "#E8613A", "rrts"),
]
SYSTEM_OF_LINE = {l[0]: l[4] for l in LINES}

# Rapid Metro is hand-built: the OSM relations disagree on the loop direction.
# The official app routes Sikanderpur -> Phase 3 via Belvedere Towers, so the
# loop is one-way Phase 2 -> Belvedere -> Cyber City -> Moulsari -> Phase 3 -> Phase 2.
RAPID_PATTERNS = [
    (["Sikanderpur", "DLF Phase 1", "Sector 42-43", "Sector 53-54", "Sector 54 Chowk", "Sector 55-56"], None),
    (["Sector 55-56", "Sector 54 Chowk", "Sector 53-54", "Sector 42-43", "DLF Phase 1", "Sikanderpur"], None),
    (["Sikanderpur", "DLF Phase 2", "Belvedere Towers", "Cyber City", "Moulsari Avenue", "DLF Phase 3",
      "DLF Phase 2", "Sikanderpur"], "DLF Phase 3"),
]

# Display names, keyed by the cleaned OSM name.
RENAME = {
    "A.I.I.M.S.": "AIIMS",
    "N.H.P.C. Chowk": "NHPC Chowk",
    "R. K. Puram": "RK Puram",
    "Rainbow Metro Station": "Noida Sector 50",
    "Depot Station": "Depot",
    "Moulsari Avenue Station": "Moulsari Avenue",
    "Phase 3 Station": "DLF Phase 3",
    "Sector 55–56": "Sector 55-56",
    "Maujpur-Babarpur": "Maujpur - Babarpur",
    "Chawri Bazaar": "Chawri Bazar",
    "Duhai Depot Station": "Duhai Depot",
    "New Delhi Airport Express Terminal": "New Delhi",
}

# Walking links between stations of different systems (minutes).
WALKS = [
    ("dmrc:Noida Sector 52", "nmrc:Noida Sector 51", 10),   # foot-over bridge
    ("dmrc:Sikanderpur", "rmgl:Sikanderpur", 10),           # official app: 10 min
    ("dmrc:Sarai Kale Khan - Nizamuddin", "rrts:Sarai Kale Khan", 10),
    ("dmrc:New Ashok Nagar", "rrts:New Ashok Nagar", 8),
    ("dmrc:Anand Vihar", "rrts:Anand Vihar", 10),
    ("dmrc:Dhaula Kuan", "dmrc:Durgabai Deshmukh South Campus", 15),  # skywalk
]

# Minutes to change lines inside one DMRC station. Default 5 (official app shows
# 5 at Rajiv Chowk). Long walks are listed explicitly.
INTERCHANGE_DEFAULT = 5
INTERCHANGE = {
    "Kirti Nagar": 4, "Ashok Park Main": 2, "Yamuna Bank": 2, "Mandi House": 4,
    "Central Secretariat": 4, "Hauz Khas": 7, "Rajouri Garden": 8, "Netaji Subhash Place": 6,
    "Kashmere Gate": 6, "Inderlok": 5, "Lajpat Nagar": 6, "Kalkaji Mandir": 6,
    "Janakpuri West": 5, "Botanical Garden": 5, "Dwarka Sector 21": 6, "New Delhi": 8,
    "Azadpur": 6, "Anand Vihar": 6, "Karkarduma": 5, "Welcome": 6,
    "Mayur Vihar - I": 5, "Dilli Haat - INA": 6, "Punjabi Bagh West": 10, "Majlis Park": 5,
    "Maujpur - Babarpur": 4, "Dwarka - Kakrola": 5, "Haiderpur Badli Mor": 5, "Madhuban Chowk": 5,
}

# Travel time per hop = DWELL + km / SPEED * 60, per system. Calibrated against
# official app leg times (Yellow 11 hops = 23 min, Blue 6 hops = 12 min,
# Yellow 29 hops = 69 min, Rapid 5 hops = 9 min): DMRC runs a steady ~34 km/h
# including stops, so a separate dwell term does not improve the fit.
TIME = {
    "dmrc": (0.0, 34.3),
    "dmrc_airport": (1.0, 75.0),
    "rmgl": (0.0, 25.0),
    "nmrc": (0.0, 36.0),
    "rrts": (1.0, 110.0),
}

# Fares (INR). DMRC: by shortest network distance, two schedules.
FARES = {
    "dmrc": {
        "slabs_km": [2, 5, 12, 21, 32],
        "weekday": [11, 21, 32, 43, 54, 64],
        "sunday": [11, 11, 21, 32, 43, 54],
        "verified": True,
    },
    # Rapid Metro: flat within phase 1 (Sikanderpur + loop) or within phase 2,
    # higher across both. Official app: Adarsh Nagar -> Phase 3 = 54 + 20.
    "rmgl": {"within": 20, "across": 35, "phase1": ["Sikanderpur", "DLF Phase 2", "Belvedere Towers",
             "Cyber City", "Moulsari Avenue", "DLF Phase 3"], "verified": False},
    # NMRC: by number of stations travelled. Third-party source, not yet verified.
    "nmrc": {"stations": [1, 2, 6, 9, 16], "fares": [10, 15, 20, 30, 40, 50], "verified": False},
    "dmrc_airport": None,  # not published reliably yet; shown as unavailable
    "rrts": None,          # NCRTC fares not verified; shown as unavailable
}


def clean(name):
    name = re.sub(r"\s*\((Yellow|Red|Blue|Green|Violet|Pink|Magenta|Grey) Line\)\s*$", "", name)
    return RENAME.get(name, name)


def km(a, b):
    la1, lo1, la2, lo2 = map(math.radians, (a[0], a[1], b[0], b[1]))
    x = (lo2 - lo1) * math.cos((la1 + la2) / 2)
    return 6371 * math.hypot(x, la2 - la1)


def main():
    raw = json.loads(RAW.read_text())
    nodes = {e["id"]: e for e in raw["elements"] if e["type"] == "node"}
    rels = {e["id"]: e for e in raw["elements"] if e["type"] == "relation"}

    stations = {}  # id -> dict
    coords = {}    # id -> list of (lat, lon)

    def station(system, name, lat, lon, line):
        sysk = "dmrc" if system in ("dmrc", "dmrc_airport") else system
        sid = f"{sysk}:{name}"
        s = stations.setdefault(sid, {"id": sid, "name": name, "system": sysk, "lines": []})
        coords.setdefault(sid, []).append((lat, lon))
        if line not in s["lines"]:
            s["lines"].append(line)
        return sid

    patterns = []
    for rid, (line, towards) in PATTERNS.items():
        rel = rels[rid]
        system = SYSTEM_OF_LINE[line]
        ids = []
        for m in rel["members"]:
            if m["type"] == "node" and m["role"].startswith("stop"):
                n = nodes[m["ref"]]
                ids.append(station(system, clean(n["tags"]["name"]), n["lat"], n["lon"], line))
        patterns.append({"line": line, "stops": ids, "towards": towards})

    # Rapid Metro coordinates come from the OSM nodes of either relation.
    rapid_xy = {}
    for rid in (4481323, 7890002):
        for m in rels[rid]["members"]:
            if m["role"].startswith("stop"):
                n = nodes[m["ref"]]
                rapid_xy.setdefault(clean(n["tags"]["name"]), (n["lat"], n["lon"]))
    for names, towards in RAPID_PATTERNS:
        ids = [station("rmgl", nm, *rapid_xy[nm], "rapid") for nm in names]
        patterns.append({"line": "rapid", "stops": ids, "towards": towards})

    # Average coordinates per merged station.
    for sid, pts in coords.items():
        stations[sid]["lat"] = round(sum(p[0] for p in pts) / len(pts), 6)
        stations[sid]["lon"] = round(sum(p[1] for p in pts) / len(pts), 6)

    for p in patterns:
        system = SYSTEM_OF_LINE[p["line"]]
        dwell, speed = TIME[system]
        hops_km, hops_min = [], []
        for a, b in zip(p["stops"], p["stops"][1:]):
            d = km((stations[a]["lat"], stations[a]["lon"]), (stations[b]["lat"], stations[b]["lon"]))
            d *= 1.06  # track is longer than the straight line between stations
            hops_km.append(round(d, 3))
            hops_min.append(round(dwell + d / speed * 60, 2))
        p["km"] = hops_km
        p["min"] = hops_min
        if p["towards"] is None:
            p["towards"] = stations[p["stops"][-1]]["name"]

    for a, b, _ in WALKS:
        assert a in stations and b in stations, (a, b)

    out = {
        "version": raw["osm3s"]["timestamp_osm_base"][:10],
        "source": "OpenStreetMap contributors (ODbL)",
        "lines": [{"id": i, "name": n, "short": s, "color": c, "system": y} for i, n, s, c, y in LINES],
        "stations": sorted(stations.values(), key=lambda s: s["name"]),
        "patterns": patterns,
        "walks": [{"a": a, "b": b, "min": m} for a, b, m in WALKS],
        "interchange": {"default": INTERCHANGE_DEFAULT, "at": INTERCHANGE},
        "fares": FARES,
    }
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(out, ensure_ascii=False, separators=(",", ":")))
    print(f"{len(stations)} stations, {len(patterns)} patterns -> {OUT} ({OUT.stat().st_size // 1024} KB)")


if __name__ == "__main__":
    main()
