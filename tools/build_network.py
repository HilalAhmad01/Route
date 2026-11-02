import json
import math
import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parent.parent
RAW = ROOT / "tools" / "raw" / "osm.json"
PLATFORMS = ROOT / "tools" / "raw" / "dmrc_platforms.json"
OUT = ROOT / "app" / "src" / "main" / "assets" / "network.json"

PATTERNS = {
    447214: ("red", None), 8037718: ("red", None),
    447210: ("yellow", None), 8037712: ("yellow", None),
    8037669: ("blue", None), 447209: ("blue", None),
    8037670: ("blue", "Dwarka Sector 21"), 2535795: ("blue", None),
    8037687: ("green", None), 8037666: ("green", None),
    8037675: ("green", None), 2535796: ("green", None),
    2535797: ("violet", None), 8037705: ("violet", None),
    8242437: ("pink", None), 8241298: ("pink", None),
    8385429: ("magenta", None), 8385430: ("magenta", None),
    20300114: ("magenta", None), 20300115: ("magenta", None),
    3537978: ("grey", None), 8051592: ("grey", None),
    2535798: ("airport", None), 8037715: ("airport", None),
    9268568: ("aqua", None), 9268569: ("aqua", None),
    17337287: ("rrts", None), 17337288: ("rrts", None),
}

LINES = [
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

RAPID_PATTERNS = [
    (["Sikanderpur", "DLF Phase 1", "Sector 42-43", "Sector 53-54", "Sector 54 Chowk", "Sector 55-56"], None),
    (["Sector 55-56", "Sector 54 Chowk", "Sector 53-54", "Sector 42-43", "DLF Phase 1", "Sikanderpur"], None),
    (["Sikanderpur", "DLF Phase 2", "Belvedere Towers", "Cyber City", "Moulsari Avenue", "DLF Phase 3",
      "DLF Phase 2", "Sikanderpur"], "DLF Phase 3"),
]

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

ALIASES = {
    "dmrc:Rajiv Chowk": ["CP", "Connaught Place"],
    "dmrc:Millennium City Centre Gurugram": ["HUDA City Centre", "HUDA", "MCC"],
    "dmrc:Lok Kalyan Marg": ["Race Course"],
    "dmrc:Seva Teerth": ["Udyog Bhawan"],
    "dmrc:Supreme Court": ["Pragati Maidan"],
    "dmrc:Netaji Subhash Place": ["NSP"],
    "dmrc:Jamia Millia Islamia": ["JMI", "Jamia"],
    "dmrc:Dilli Haat - INA": ["INA"],
    "dmrc:Vishwa Vidyalaya": ["DU", "North Campus", "Delhi University"],
    "dmrc:Durgabai Deshmukh South Campus": ["South Campus", "DDSC"],
    "dmrc:Sir M. Vishweshwaraiah Moti Bagh": ["Moti Bagh"],
    "dmrc:Kashmere Gate": ["ISBT Kashmere Gate", "Kashmiri Gate"],
    "dmrc:New Delhi": ["NDLS", "New Delhi Railway Station"],
    "dmrc:Chandni Chowk": ["Old Delhi Railway Station"],
    "dmrc:Sarai Kale Khan - Nizamuddin": ["Hazrat Nizamuddin", "Nizamuddin"],
    "dmrc:Anand Vihar": ["Anand Vihar ISBT"],
    "dmrc:IGI Airport": ["T3", "Terminal 3", "Airport T3"],
    "dmrc:Terminal 1 IGI Airport": ["T1", "Airport T1", "Domestic Airport"],
    "dmrc:Delhi Aerocity": ["Aerocity"],
    "dmrc:Yashobhoomi Dwarka Sector 25": ["Yashobhoomi", "IICC"],
    "dmrc:Mayur Vihar - I": ["Mayur Vihar Phase 1"],
    "dmrc:East Vinod Nagar - Mayur Vihar - II": ["Mayur Vihar Phase 2"],
    "dmrc:Ramakrishna Ashram Marg": ["RK Ashram"],
    "dmrc:Jasola Vihar Shaheen Bagh": ["Shaheen Bagh"],
    "dmrc:Shaheed Sthal": ["New Bus Adda"],
    "dmrc:Raja Nahar Singh": ["Ballabhgarh"],
    "dmrc:Sant Surdas (Sihi)": ["Sihi"],
}

WALKS = [
    ("dmrc:Noida Sector 52", "nmrc:Noida Sector 51", 10),
    ("dmrc:Sikanderpur", "rmgl:Sikanderpur", 10),
    ("dmrc:Sarai Kale Khan - Nizamuddin", "rrts:Sarai Kale Khan", 10),
    ("dmrc:New Ashok Nagar", "rrts:New Ashok Nagar", 8),
    ("dmrc:Anand Vihar", "rrts:Anand Vihar", 10),
    ("dmrc:Dhaula Kuan", "dmrc:Durgabai Deshmukh South Campus", 15),
]

# Platforms filled in by hand, keyed "station|line|next stop", checked 2026-11-02. These win over
# DMRC's journey planner. Rohini West, Haiderpur Village and North Pitampura - Prashant Vihar aren't
# in DMRC's station list, and DMRC gives an empty platform for most of the rest. Sources: yometro.com,
# delhimetrorail.info and Wikipedia station layouts, each checked against the platform DMRC gives
# for the opposite direction.
MANUAL_PLATFORMS = {
    "dmrc:Rohini West|red|dmrc:Rithala": "1",
    "dmrc:Rohini West|red|dmrc:Rohini East": "2",
    "dmrc:Haiderpur Village|magenta|dmrc:North Pitampura - Prashant Vihar": "1",
    "dmrc:Haiderpur Village|magenta|dmrc:Haiderpur Badli Mor": "2",
    "dmrc:North Pitampura - Prashant Vihar|magenta|dmrc:Madhuban Chowk": "1",
    "dmrc:North Pitampura - Prashant Vihar|magenta|dmrc:Haiderpur Village": "2",
    "dmrc:Rohini East|red|dmrc:Madhuban Chowk": "2",
    "dmrc:Rohini East|red|dmrc:Rohini West": "1",
    "dmrc:Madhuban Chowk|red|dmrc:Kohat Enclave": "2",
    "dmrc:Madhuban Chowk|red|dmrc:Rohini East": "1",
    "dmrc:Seva Teerth|yellow|dmrc:Lok Kalyan Marg": "1",
    "dmrc:Seva Teerth|yellow|dmrc:Central Secretariat": "2",
    "dmrc:Dwarka - Kakrola|blue|dmrc:Dwarka Mor": "1",
    "dmrc:Dwarka - Kakrola|blue|dmrc:Dwarka Sector 14": "2",
    "dmrc:Anand Vihar|blue|dmrc:Karkarduma": "2",
    "dmrc:Preet Vihar|blue|dmrc:Nirman Vihar": "2",
    "dmrc:Laxmi Nagar|blue|dmrc:Yamuna Bank": "2",
    "dmrc:Lajpat Nagar|pink|dmrc:Vinobapuri": "3",
    "dmrc:Mayur Vihar - I|pink|dmrc:Shree Ram Mandir Mayur Vihar": "3",
    "dmrc:Shree Ram Mandir Mayur Vihar|pink|dmrc:Trilokpuri Sanjay Lake": "1",
    "dmrc:Shree Ram Mandir Mayur Vihar|pink|dmrc:Mayur Vihar - I": "2",
    "dmrc:Anand Vihar|pink|dmrc:Karkarduma": "3",
    "dmrc:Karkarduma|pink|dmrc:Karkarduma Court": "3",
    "dmrc:Johri Enclave|pink|dmrc:Gokulpuri": "2",
    "dmrc:Gokulpuri|pink|dmrc:Maujpur - Babarpur": "2",
    "dmrc:Jagatpur - Wazirabad|pink|dmrc:Jharoda Majra": "1",
    "dmrc:Jagatpur - Wazirabad|pink|dmrc:Soorghat": "2",
    # Sources disagree here; these follow delhimetrorail.info.
    "dmrc:Welcome|pink|dmrc:Jaffrabad": "3",
    "dmrc:Welcome|pink|dmrc:East Azad Nagar": "4",
    "dmrc:Maujpur - Babarpur|pink|dmrc:Jaffrabad": "2",
    "dmrc:Dwarka Sector 21|airport|dmrc:IGI Airport": "3",
    "dmrc:IGI Airport|airport|dmrc:Delhi Aerocity": "3",
    "dmrc:IGI Airport|airport|dmrc:Dwarka Sector 21": "2",
    "dmrc:Delhi Aerocity|airport|dmrc:Dhaula Kuan": "1",
    "dmrc:Delhi Aerocity|airport|dmrc:IGI Airport": "2",
    "dmrc:Dhaula Kuan|airport|dmrc:Shivaji Stadium": "1",
    "dmrc:Dhaula Kuan|airport|dmrc:Delhi Aerocity": "2",
    "dmrc:Shivaji Stadium|airport|dmrc:New Delhi": "1",
    "dmrc:Shivaji Stadium|airport|dmrc:Dhaula Kuan": "2",
}
# DMRC's Airport Express platforms disagree with delhimetrorail.info and Wikipedia, so only the
# hand-checked ones above are used.
PLATFORM_SKIP_LINES = {"airport"}
# No platform is shown where no source can be trusted: DMRC gives Yamuna Vihar 2 in both directions,
# and the Maujpur - Babarpur to Yamuna Vihar stretch isn't on delhimetrorail.info yet.
PLATFORM_DOUBTS = {
    "dmrc:Yamuna Vihar|pink|dmrc:Bhajanpura",
    "dmrc:Yamuna Vihar|pink|dmrc:Maujpur - Babarpur",
}

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

TIME = {
    "dmrc": (0.0, 34.3),
    "dmrc_airport": (1.0, 75.0),
    "rmgl": (0.0, 25.0),
    "nmrc": (0.0, 36.0),
    "rrts": (1.0, 110.0),
}

FARES = {
    "dmrc": {
        "slabs_km": [2, 5, 12, 21, 32],
        "weekday": [11, 21, 32, 43, 54, 64],
        "sunday": [11, 11, 21, 32, 43, 54],
        "verified": True,
    },
    "rmgl": {"within": 20, "across": 35, "phase1": ["Sikanderpur", "DLF Phase 2", "Belvedere Towers",
             "Cyber City", "Moulsari Avenue", "DLF Phase 3"], "verified": False},
    "nmrc": {"stations": [1, 2, 6, 9, 16], "fares": [10, 15, 20, 30, 40, 50], "verified": False},
    "dmrc_airport": None,
    "rrts": None,
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

    stations = {}
    coords = {}

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

    rapid_xy = {}
    for rid in (4481323, 7890002):
        for m in rels[rid]["members"]:
            if m["role"].startswith("stop"):
                n = nodes[m["ref"]]
                rapid_xy.setdefault(clean(n["tags"]["name"]), (n["lat"], n["lon"]))
    for names, towards in RAPID_PATTERNS:
        ids = [station("rmgl", nm, *rapid_xy[nm], "rapid") for nm in names]
        patterns.append({"line": "rapid", "stops": ids, "towards": towards})

    for sid, pts in coords.items():
        stations[sid]["lat"] = round(sum(p[0] for p in pts) / len(pts), 6)
        stations[sid]["lon"] = round(sum(p[1] for p in pts) / len(pts), 6)

    for p in patterns:
        system = SYSTEM_OF_LINE[p["line"]]
        dwell, speed = TIME[system]
        hops_km, hops_min = [], []
        for a, b in zip(p["stops"], p["stops"][1:]):
            d = km((stations[a]["lat"], stations[a]["lon"]), (stations[b]["lat"], stations[b]["lon"]))
            d *= 1.06
            hops_km.append(round(d, 3))
            hops_min.append(round(dwell + d / speed * 60, 2))
        p["km"] = hops_km
        p["min"] = hops_min
        if p["towards"] is None:
            p["towards"] = stations[p["stops"][-1]]["name"]

    for a, b, _ in WALKS:
        assert a in stations and b in stations, (a, b)
    for sid, names in ALIASES.items():
        assert sid in stations, sid
        stations[sid]["aliases"] = names

    platforms = dict(MANUAL_PLATFORMS)
    if PLATFORMS.exists():
        for key, v in json.loads(PLATFORMS.read_text())["platforms"].items():
            if key.split("|")[1] not in PLATFORM_SKIP_LINES:
                platforms.setdefault(key, v["platform"])
    # At a terminus trains leave from either platform, so a single number would mislead.
    neighbours = {}
    for p in patterns:
        for a, b in zip(p["stops"], p["stops"][1:]):
            neighbours.setdefault((a, p["line"]), set()).add(b)
            neighbours.setdefault((b, p["line"]), set()).add(a)
    for key in list(platforms):
        sid, line, nxt = key.split("|")
        if (sid not in stations or nxt not in stations or key in PLATFORM_DOUBTS
                or len(neighbours.get((sid, line), ())) < 2):
            del platforms[key]

    out = {
        "version": raw["osm3s"]["timestamp_osm_base"][:10],
        "source": "OpenStreetMap contributors (ODbL)",
        "lines": [{"id": i, "name": n, "short": s, "color": c, "system": y} for i, n, s, c, y in LINES],
        "stations": sorted(stations.values(), key=lambda s: (s["name"].lower(), s["system"])),
        "patterns": patterns,
        "walks": [{"a": a, "b": b, "min": m} for a, b, m in WALKS],
        "interchange": {"default": INTERCHANGE_DEFAULT, "at": INTERCHANGE},
        "fares": FARES,
        "platforms": platforms,
    }
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(out, ensure_ascii=False, separators=(",", ":")))
    print(f"{len(stations)} stations, {len(patterns)} patterns, {len(platforms)} platforms -> {OUT} ({OUT.stat().st_size // 1024} KB)")


if __name__ == "__main__":
    main()
