import csv
import heapq
import json
import math
import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parent.parent
RAW = ROOT / "tools" / "raw" / "osm.json"
PLATFORMS = ROOT / "tools" / "raw" / "platforms.csv"
HOP_KM = ROOT / "tools" / "raw" / "hop_km.csv"  # DMRC's own distance between neighbouring stations; optional
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

PLATFORM_LINES = {
    "Red Line": "red", "Yellow Line": "yellow", "Blue Line": "blue", "Green Line": "green",
    "Violet Line": "violet", "Pink Line": "pink", "Magenta Line": "magenta", "Grey Line": "grey",
    "Orange Line": "airport", "Rapid Metro": "rapid",
}

# Platform-table names that don't normalise to Route's. North Pitampura and Prashant Vihar are left out:
# the table lists them as two stations where OSM has Haiderpur Village and North Pitampura - Prashant Vihar.
PLATFORM_NAMES = {
    "BADARPUR BORDER": "Badarpur", "BADKAL MOR": "Badhkal Mor", "BARAKHAMBA ROAD": "Barakhambha Road",
    "BRIG. HOSHIAR SINGH": "Brigadier Hoshiyar Singh", "GOLF COURSE": "Noida Golf Course",
    "GURU TEG BAHADUR NAGAR": "GTB Nagar", "IIT": "IIT Delhi", "JLN STADIUM": "Jawaharlal Nehru Stadium",
    "JAFRABAD": "Jaffrabad", "MAHARAJA SURAJMAL STADIUM": "Surajmal Stadium",
    "MAJOR MOHIT SHARMA RAJENDRA NAGAR": "Rajendra Nagar",
    "SHREE RAM MAYUR VIHAR MANDIR (earlier MAYUR VIHAR POCKET-1)": "Shree Ram Mandir Mayur Vihar",
    "PHASE-1": "DLF Phase 1", "PHASE-2": "DLF Phase 2", "PHASE-3": "DLF Phase 3",
    "ROHINI": "Rohini East", "DR. BABA SAHEB AMBEDKAR HOSPITAL": "Rohini West",
    "SECTOR - 34 NOIDA": "Noida Sector 34", "SECTOR - 52 NOIDA": "Noida Sector 52",
    "SECTOR - 59 NOIDA": "Noida Sector 59", "SECTOR - 61 NOIDA": "Noida Sector 61",
    "SECTOR - 62 NOIDA": "Noida Sector 62", "SONIA VIHAR": "Nanaksar - Sonia Vihar",
    "GHEVRA METRO STATION": "Ghevra",
}

# Stations where Rapid Metro trains run on into the next pattern, so a platform's
# "towards" can lie beyond the end of the pattern being boarded.
THROUGH_RUNNING = {("rapid", "Sikanderpur")}

# (line, station, next stop) left without a platform: from Maujpur towards Yamuna Vihar the table only
# lists the extension's platforms as "towards Maujpur-Babarpur", so no row names this direction.
NO_PLATFORM = {("pink", "Maujpur - Babarpur", "Yamuna Vihar")}

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


FARE_TABLE = ROOT / "tools" / "raw" / "dmrc_fares.csv"  # DMRC's own fare for each station pair; optional

# The Magenta Line extension is newer than DMRC's fare table, so pairs whose shortest path uses it keep the distance fare.
MAGENTA_EXTENSION = ["dmrc:Majlis Park", "dmrc:Bhalaswa", "dmrc:Haiderpur Badli Mor", "dmrc:Haiderpur Village",
                     "dmrc:North Pitampura - Prashant Vihar", "dmrc:Madhuban Chowk", "dmrc:Deepali Chowk"]


def fare_overrides(ordered, patterns):
    """[i, j, slab] for station pairs where DMRC's fare differs from the one its distance slab gives.

    Magenta Line fares don't follow the route distance (19.4 km already costs the 21 to 32 km fare), so for those
    pairs the table is used as is. i < j are indexes into the station list.
    """
    if not FARE_TABLE.exists():
        return []
    slabs = FARES["dmrc"]["slabs_km"]
    weekday = FARES["dmrc"]["weekday"]
    index = {s["id"]: i for i, s in enumerate(ordered)}
    system = {l[0]: l[4] for l in LINES}
    adj = {}
    for p in patterns:
        if system[p["line"]] != "dmrc":
            continue
        for k in range(len(p["stops"]) - 1):
            adj.setdefault(p["stops"][k], []).append((p["stops"][k + 1], p["km"][k]))
    extension = {(a, b) for a, b in zip(MAGENTA_EXTENSION, MAGENTA_EXTENSION[1:])}
    extension |= {(b, a) for a, b in extension}

    def shortest(src, skip):
        dist, queue = {src: 0.0}, [(0.0, src)]
        while queue:
            d, u = heapq.heappop(queue)
            if d > dist[u]:
                continue
            for v, w in adj.get(u, []):
                if (u, v) not in skip and d + w < dist.get(v, math.inf):
                    dist[v] = d + w
                    heapq.heappush(queue, (dist[v], v))
        return dist

    def slab(distance):
        return next((i for i, limit in enumerate(slabs) if distance <= limit), len(slabs))

    table = {}
    with FARE_TABLE.open(newline="") as f:
        for r in csv.DictReader(f):
            table[(r["from"], r["to"])] = weekday.index(int(r["fare"]))
    out = {}
    for a in adj:
        full, without = shortest(a, set()), shortest(a, extension)
        for b, d in full.items():
            if (a, b) not in table or slab(d) == table[(a, b)]:
                continue
            if without.get(b, math.inf) > d + 1e-6:
                continue  # the path uses the newer extension, which the table predates
            out[(min(index[a], index[b]), max(index[a], index[b]))] = table[(a, b)]
    return sorted([i, j, slab] for (i, j), slab in out.items())


def clean(name):
    name = re.sub(r"\s*\((Yellow|Red|Blue|Green|Violet|Pink|Magenta|Grey) Line\)\s*$", "", name)
    return RENAME.get(name, name)


def km(a, b):
    la1, lo1, la2, lo2 = map(math.radians, (a[0], a[1], b[0], b[1]))
    x = (lo2 - lo1) * math.cos((la1 + la2) / 2)
    return 6371 * math.hypot(x, la2 - la1)


def norm(name):
    return re.sub(r"[^a-z0-9]", "", name.lower().replace(" station", ""))


def add_platforms(stations, patterns):
    """Give each Delhi Metro/Rapid pattern stop the platform(s) its trains leave from, or [] if unknown."""
    by_name = {}
    for sid, s in stations.items():
        for key in {norm(s["name"]), *map(norm, s.get("aliases", []))}:
            by_name.setdefault(key, []).append(sid)

    def resolve(name, line):
        name = PLATFORM_NAMES.get(name, name)
        for key in (name, re.sub(r"\s*\(.*?\)", "", name), *re.findall(r"\(earlier (.*?)\)", name)):
            hits = [sid for sid in by_name.get(norm(key), []) if line in stations[sid]["lines"]]
            if hits:
                return hits[0]
        return None

    rows, unmatched = {}, set()
    with PLATFORMS.open(newline="") as f:
        for r in csv.DictReader(f):
            line = PLATFORM_LINES[r["line"]]
            sid = resolve(r["station_name"], line)
            if sid is None:
                unmatched.add(r["station_name"])
                continue
            named = [resolve(t.strip(), line) for t in r["train_towards"].split("/")]
            named = [t for t in named if t]
            towards = [t for t in named if t != sid]
            rows.setdefault((sid, line), []).append((towards, int(r["platform_code"][2:]), named))

    name = {sid: s["name"] for sid, s in stations.items()}
    covered = 0
    for p in patterns:
        line, stops = p["line"], p["stops"]
        ahead_all = list(stops)
        if p["towards"] != name[stops[-1]]:  # e.g. Vaishali trains end at Yamuna Bank but run on to Dwarka
            for q in patterns:
                if q["line"] == line and stops[-1] in q["stops"][:-1]:
                    rest = q["stops"][q["stops"].index(stops[-1]) + 1:]
                    if p["towards"] in (name[s] for s in rest):
                        ahead_all += rest
                        break
        if (line, name[stops[-1]]) in THROUGH_RUNNING:
            for q in patterns:
                if q is not p and q["line"] == line and q["stops"][0] == stops[-1] and q["stops"][1] != stops[-2]:
                    ahead_all += q["stops"][1:]
        platforms, dirs = [], []
        for i, sid in enumerate(stops):
            pos = {}
            for k, t in enumerate(ahead_all[i + 1:]):
                pos.setdefault(t, k)
            # Platform per direction, so a leg can pick the platform that serves where it gets off.
            # A platform whose destinations are all behind us in this pattern faces the other way round a loop
            # (the Pink Line ring), so leave it out: otherwise a ring station it names picks it although the
            # train is heading the other way. Destinations missing from the pattern don't count as behind: a
            # leg can carry on into another pattern of the line (Kirti Nagar to Brigadier Hoshiyar Singh).
            behind = set(stops[:i])
            dirs.append([{"p": n, "t": named} for towards, n, named in rows.get((sid, line), [])
                         if named and not (i < len(stops) - 1 and towards
                                           and all(t in behind and t not in pos for t in towards))])
            if i == len(stops) - 1 or (line, name[sid], name[stops[i + 1]]) in NO_PLATFORM:
                platforms.append([])
                continue
            # Prefer a platform whose first-named destination is ahead, then the nearest one.
            ranked = [(min((k > 0, pos[t]) for k, t in enumerate(towards) if t in pos), n)
                      for towards, n, _ in rows.get((sid, line), []) if any(t in pos for t in towards)]
            best = min((r for r, _ in ranked), default=None)
            platforms.append(sorted({n for r, n in ranked if r == best}))
            covered += bool(platforms[-1])
        if any(platforms):
            p["platforms"] = platforms
        if any(dirs):
            p["dirs"] = dirs
    print(f"platforms for {covered} pattern stops; unmatched platform-table stations: {sorted(unmatched)}")


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

    # Delhi Metro fares are slabs of the shortest distance, so use DMRC's own distance for each hop where we have it.
    # Times keep using the map estimate: the speeds above were calibrated against it.
    official_km = {}
    if HOP_KM.exists():
        with HOP_KM.open(newline="") as f:
            for r in csv.DictReader(f):
                official_km[(r["from"], r["to"])] = float(r["km"])

    for p in patterns:
        system = SYSTEM_OF_LINE[p["line"]]
        dwell, speed = TIME[system]
        hops_km, hops_min = [], []
        for a, b in zip(p["stops"], p["stops"][1:]):
            d = km((stations[a]["lat"], stations[a]["lon"]), (stations[b]["lat"], stations[b]["lon"]))
            d *= 1.06
            hops_km.append(round(official_km.get((a, b), d), 3))
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
    add_platforms(stations, patterns)

    ordered = sorted(stations.values(), key=lambda s: (s["name"].lower(), s["system"]))
    fares = {**FARES, "dmrc": {**FARES["dmrc"], "overrides": fare_overrides(ordered, patterns)}}

    out = {
        "version": raw["osm3s"]["timestamp_osm_base"][:10],
        "source": "OpenStreetMap contributors (ODbL)",
        "lines": [{"id": i, "name": n, "short": s, "color": c, "system": y} for i, n, s, c, y in LINES],
        "stations": ordered,
        "patterns": patterns,
        "walks": [{"a": a, "b": b, "min": m} for a, b, m in WALKS],
        "interchange": {"default": INTERCHANGE_DEFAULT, "at": INTERCHANGE},
        "fares": fares,
    }
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(out, ensure_ascii=False, separators=(",", ":")))
    print(f"{len(stations)} stations, {len(patterns)} patterns -> {OUT} ({OUT.stat().st_size // 1024} KB)")


if __name__ == "__main__":
    main()
