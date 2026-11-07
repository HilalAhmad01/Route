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
    17337287: ("rrts", None), 17337288: ("rrts", None),  # only registers the 14 stations OSM maps; see RRTS_* below
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
    "Moulsari Avenue Station": "Moulsari Avenue",
    "Phase 3 Station": "DLF Phase 3",
    "Sector 55–56": "Sector 55-56",
    "Maujpur-Babarpur": "Maujpur - Babarpur",
    "Chawri Bazaar": "Chawri Bazar",
    "Duhai Depot Station": "Duhai Depot",
    "Bhainsali": "Bhaisali",
    "Brahmapuri": "Brahampuri",
    "New Delhi Airport Express Terminal": "New Delhi",
    "Rohini West": "Dr. Baba Saheb Ambedkar Hospital",
    "Rohini East": "Rohini",
    "North Pitampura - Prashant Vihar": "Uttari Pitampura - Prashant Vihar",
    # The names on DMRC's station signs and in its app. The old names stay as search aliases below.
    "Badarpur": "Badarpur Border",
    "Badhkal Mor": "Badkal Mor",
    "GTB Nagar": "Guru Teg Bahadur Nagar",
    "Jaffrabad": "Jafrabad",
    "Barakhambha Road": "Barakhamba Road",
    "Rajendra Nagar": "Major Mohit Sharma Rajendra Nagar",
    "Tughlakabad": "Tughlakabad Station",
    "Brigadier Hoshiyar Singh": "Brig. Hoshiar Singh",
    "Ghevra": "Ghevra Metro Station",
    "Jawaharlal Nehru Stadium": "JLN Stadium",
    "Surajmal Stadium": "Maharaja Surajmal Stadium",
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
    "dmrc:Dr. Baba Saheb Ambedkar Hospital": ["Rohini West"],
    "dmrc:Rohini": ["Rohini East"],
    "dmrc:Uttari Pitampura - Prashant Vihar": ["North Pitampura - Prashant Vihar", "North Pitampura"],
    # The names these stations had before they were changed to DMRC's, so old habits and old links still find them.
    "dmrc:Badarpur Border": ["Badarpur"],
    "dmrc:Badkal Mor": ["Badhkal Mor"],
    "dmrc:Guru Teg Bahadur Nagar": ["GTB Nagar"],
    "dmrc:Jafrabad": ["Jaffrabad"],
    "dmrc:Barakhamba Road": ["Barakhambha Road"],
    "dmrc:Major Mohit Sharma Rajendra Nagar": ["Rajendra Nagar"],
    "dmrc:Tughlakabad Station": ["Tughlakabad"],
    "dmrc:Brig. Hoshiar Singh": ["Brigadier Hoshiyar Singh"],
    "dmrc:Ghevra Metro Station": ["Ghevra"],
    "dmrc:JLN Stadium": ["Jawaharlal Nehru Stadium"],
    "dmrc:Maharaja Surajmal Stadium": ["Surajmal Stadium"],
    "rrts:Bhaisali": ["Bhainsali"],
    "rrts:Brahampuri": ["Brahmapuri", "Brahmpuri"],
    "rrts:Duhai Depot": ["Duhai Depot Station"],
}

WALKS = [
    ("dmrc:Noida Sector 52", "nmrc:Noida Sector 51", 10),
    ("dmrc:Sikanderpur", "rmgl:Sikanderpur", 10),
    ("dmrc:Sarai Kale Khan - Nizamuddin", "rrts:Sarai Kale Khan", 10),
    ("dmrc:New Ashok Nagar", "rrts:New Ashok Nagar", 8),
    ("dmrc:Anand Vihar", "rrts:Anand Vihar", 10),
    # The app shows 5 min to change at each end of the 15 min footbridge (Delhi Aerocity -> Moti Bagh: 34 min in the app).
    ("dmrc:Dhaula Kuan", "dmrc:Durgabai Deshmukh South Campus", 25),
    # The official app treats these as one station: pick Ghaziabad and it becomes Shaheed Sthal (New Bus Adda), and its
    # totals include no walk. They are really two stations about 250 m apart joined by a foot overbridge (NCRTC), so
    # 5 minutes is our estimate of the walk; Route's trips from Ghaziabad run ~4 min longer than the app's for that reason.
    ("dmrc:Shaheed Sthal", "rrts:Ghaziabad", 5),
]

# Namo Bharat as the official app lists it (codes Z00, A01-A21, spur AS2), read from the live app on 2026-11-07:
# a trip over the whole line shows every station with the minutes from the previous one. OSM's relation only has 14 of
# the 22 stops, so the patterns are written out here. Coordinates of the added stations still come from OSM's stop nodes.
RRTS_MAIN = ["Sarai Kale Khan", "New Ashok Nagar", "Anand Vihar", "Sahibabad", "Ghaziabad", "Guldhar", "Duhai",
             "Murad Nagar", "Modinagar South", "Modinagar North", "Meerut South", "Partapur", "Rithani",
             "Shatabdi Nagar", "Brahampuri", "Meerut Central", "Bhaisali", "Begumpul", "MES Colony", "Daurli",
             "Meerut North", "Modipuram"]
RRTS_MAIN_MIN = [6, 5, 5, 4, 3, 3, 4, 4, 3, 5, 2, 1, 2, 1, 2, 1, 1, 1, 1, 1, 1]  # minutes between neighbours
RRTS_SPUR = ["Duhai", "Duhai Depot"]
RRTS_SPUR_MIN = [4]
# The app's whole-line total (1 h 4 m) is 8 minutes more than its 21 hop times add up to (56), so each stop adds about
# 0.4 min; checked against five other spans in the app (45, 18, 16, 15 min predicted within 1 min).
RRTS_DWELL = 0.4
RRTS_ADDED_OSM = {"Partapur", "Rithani", "Brahmapuri", "Meerut Central", "Bhainsali", "MES Colony", "Daurli",
                  "Meerut North", "Duhai Depot Station"}

INTERCHANGE_DEFAULT = 5
# Minutes to change trains, as the app shows them ("Change here (move towards X - N Min)"). Read on 2026-11-07 from
# one trip per station (dist/phone/recheck_ic.jsonl): the app only ever uses 5 or 10. Stations not listed use 5.
# Yamuna Bank 5 is from the DMRC website. Dwarka - Kakrola and New Delhi confirmed later the same day.
# Maujpur - Babarpur 5 from the website. Not checked: Madhuban Chowk (the website changes there; the app avoids it).
INTERCHANGE = {
    "Kirti Nagar": 5, "Ashok Park Main": 5, "Yamuna Bank": 5, "Mandi House": 5,
    "Central Secretariat": 5, "Hauz Khas": 10, "Rajouri Garden": 10, "Netaji Subhash Place": 10,
    "Kashmere Gate": 10, "Inderlok": 5, "Lajpat Nagar": 5, "Kalkaji Mandir": 10,
    "Janakpuri West": 10, "Botanical Garden": 5, "Dwarka Sector 21": 5, "New Delhi": 10,
    "Azadpur": 10, "Anand Vihar": 5, "Karkarduma": 5, "Welcome": 5,
    "Mayur Vihar - I": 5, "Dilli Haat - INA": 10, "Punjabi Bagh West": 5, "Majlis Park": 5,
    "Maujpur - Babarpur": 5, "Dwarka - Kakrola": 5, "Haiderpur Badli Mor": 5, "Madhuban Chowk": 5,
}

TIME = {
    "dmrc": (0.0, 34.3),
    "dmrc_airport": (1.0, 75.0),
    "rmgl": (0.0, 25.0),
    "nmrc": (0.0, 36.0),
    "rrts": (1.0, 110.0),
}

# Rapid Metro, checked against 10 app trips on 2026-11-07: the Cyber City loop matches 25 km/h (7/7, 8/7, 6/5, 4/4 min),
# but the Sector 55-56 line was 35% slow at that speed (17 vs 11, 11 vs 7). 38 km/h gives 11.0, 7.4, 11.0 and 2.1 min
# for the app's 11, 7, 11 and 3, and 16.4 for Sector 55-56 -> Cyber City (app 16). Fares matched on all of them.
RAPID_LINE_SPEED = 38.0

PLATFORM_LINES = {
    "Red Line": "red", "Yellow Line": "yellow", "Blue Line": "blue", "Green Line": "green",
    "Violet Line": "violet", "Pink Line": "pink", "Magenta Line": "magenta", "Grey Line": "grey",
    "Orange Line": "airport", "Rapid Metro": "rapid",
}

# Platform-table names that don't normalise to Route's. On the Magenta extension the table calls stations NPPR and
# PHVR "North Pitampura" and "Prashant Vihar"; the official app calls them Haiderpur Village and Uttari Pitampura -
# Prashant Vihar (same station codes, in the same order along the line).
PLATFORM_NAMES = {
    "GOLF COURSE": "Noida Golf Course", "IIT": "IIT Delhi",
    "SHREE RAM MAYUR VIHAR MANDIR (earlier MAYUR VIHAR POCKET-1)": "Shree Ram Mandir Mayur Vihar",
    "PHASE-1": "DLF Phase 1", "PHASE-2": "DLF Phase 2", "PHASE-3": "DLF Phase 3",
    "NORTH PITAMPURA": "Haiderpur Village", "PRASHANT VIHAR": "Uttari Pitampura - Prashant Vihar",
    "SECTOR - 34 NOIDA": "Noida Sector 34", "SECTOR - 52 NOIDA": "Noida Sector 52",
    "SECTOR - 59 NOIDA": "Noida Sector 59", "SECTOR - 61 NOIDA": "Noida Sector 61",
    "SECTOR - 62 NOIDA": "Noida Sector 62", "SONIA VIHAR": "Nanaksar - Sonia Vihar",
}

# Stations where Rapid Metro trains run on into the next pattern, so a platform's
# "towards" can lie beyond the end of the pattern being boarded.
THROUGH_RUNNING = {("rapid", "Sikanderpur")}

# (line, station, next stop) left without a platform: from Maujpur towards Yamuna Vihar the table only
# lists the extension's platforms as "towards Maujpur-Babarpur", so no row names this direction.
NO_PLATFORM = {("pink", "Maujpur - Babarpur", "Yamuna Vihar")}

# (line, station, next stop) -> platform shown by the live app where the platform table has none (read 2026-11-07).
# Satguru Ramsingh Marg's table row for the Ashok Park Main direction names Brig. Hoshiar Singh / Inderlok, which this
# 3-stop branch pattern never reaches; the Airport Express table has no Dwarka Sector 21 row at all.
PLATFORM_FILL = {
    ("green", "Satguru Ramsingh Marg", "Ashok Park Main"): 1,
    ("airport", "Dwarka Sector 21", "Yashobhoomi Dwarka Sector 25"): 2,
}

FARES = {
    "dmrc": {
        "slabs_km": [2, 5, 12, 21, 32],
        "weekday": [11, 21, 32, 43, 54, 64],
        "sunday": [11, 11, 21, 32, 43, 54],
        "verified": True,
    },
    # Rapid Metro, from 18 app trips (2026-11-07): Rs 35 only between the far end of the Sector 55-56 line (Sector 53-54,
    # 54 Chowk, 55-56) and the far side of the loop (Belvedere Towers, Cyber City, Moulsari Avenue, DLF Phase 3); every
    # other trip is Rs 20. "phase1" is the loop side, "shared" the stations that are Rs 20 to both sides.
    "rmgl": {"within": 20, "across": 35,
             "phase1": ["Sikanderpur", "DLF Phase 1", "DLF Phase 2", "Sector 42-43", "Belvedere Towers", "Cyber City",
                        "Moulsari Avenue", "DLF Phase 3"],
             "shared": ["Sikanderpur", "DLF Phase 1", "DLF Phase 2", "Sector 42-43"], "verified": True},
    "nmrc": {"stations": [1, 2, 6, 9, 16], "fares": [10, 15, 20, 30, 40, 50], "verified": False},
    "dmrc_airport": None,
    "rrts": None,
}


# hop_km.csv records 15 hops on the two newest extensions as exactly 2 km, which sits on the Rs 11 limit (slabs end at 2),
# and the live app charges Rs 21 for one hop each of Burari - Majlis Park, Majlis Park - Bhalaswa and Bhalaswa -
# Haiderpur Badli Mor (2026-11-07). Their true length isn't known (only that it's over 2 km), so rather than guess a
# distance, which would also move about 30 longer trips nobody checked, the fares seen in the app are pinned for those
# pairs. The other twelve 2 km hops matched the app at Rs 11 where checked.
APP_FARES = [("dmrc:Burari", "dmrc:Majlis Park", 21), ("dmrc:Majlis Park", "dmrc:Bhalaswa", 21),
             ("dmrc:Bhalaswa", "dmrc:Haiderpur Badli Mor", 21),
             # longer trips the app prices a slab higher than the distance gives (checked on the phone, same day)
             ("dmrc:Ashok Park Main", "dmrc:Bhalaswa", 43), ("dmrc:Ashok Park Main", "dmrc:Burari", 43),
             ("dmrc:Bhalaswa", "dmrc:Jor Bagh", 54), ("dmrc:Burari", "dmrc:Krishna Park Extension", 54)]

FARE_TABLE = ROOT / "tools" / "raw" / "dmrc_fares.csv"  # DMRC's own fare for each station pair; optional

# The Magenta Line extension is newer than DMRC's fare table, so pairs whose shortest path uses it keep the distance fare.
MAGENTA_EXTENSION = ["dmrc:Majlis Park", "dmrc:Bhalaswa", "dmrc:Haiderpur Badli Mor", "dmrc:Haiderpur Village",
                     "dmrc:Uttari Pitampura - Prashant Vihar", "dmrc:Madhuban Chowk", "dmrc:Deepali Chowk"]


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
        # The table names platforms by where trains used to go; a pattern can say where to look for that destination.
        towards_for_table = p.pop("platform_towards", p["towards"])
        if towards_for_table != name[stops[-1]]:  # e.g. Inderlok branch trains are listed "towards Brig. Hoshiar Singh"
            for q in patterns:
                if q["line"] == line and stops[-1] in q["stops"][:-1]:
                    rest = q["stops"][q["stops"].index(stops[-1]) + 1:]
                    if towards_for_table in (name[s] for s in rest):
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
            # leg can carry on into another pattern of the line (Kirti Nagar to Brig. Hoshiar Singh).
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
            fill = PLATFORM_FILL.get((line, name[sid], name[stops[i + 1]]))
            if fill and not platforms[-1]:
                platforms[-1] = [fill]
            covered += bool(platforms[-1])
        if any(platforms):
            p["platforms"] = platforms
        if any(dirs):
            p["dirs"] = dirs
    print(f"platforms for {covered} pattern stops; unmatched platform-table stations: {sorted(unmatched)}")


def split_green_line(patterns):
    """The app runs the Green Line as two services meeting at Ashok Park Main: "Green Line" from Brig. Hoshiar Singh
    to Kirti Nagar (through Satguru Ramsingh Marg) and "Green Line Branch" from Ashok Park Main to Inderlok. Going
    between Inderlok and anywhere else on the line means changing at Ashok Park Main (5 min in the app; checked on
    2026-11-07 with Punjabi Bagh, Kirti Nagar, Mundka -> Inderlok and Inderlok -> Kirti Nagar). OSM has one through
    pattern to Inderlok plus a 3-stop Kirti Nagar spur, so rebuild the four patterns the app's way."""
    apm = "dmrc:Ashok Park Main"
    green = [p for p in patterns if p["line"] == "green"]
    main_f = next(p for p in green if p["stops"][0] == "dmrc:Brig. Hoshiar Singh")
    main_r = next(p for p in green if p["stops"][0] == "dmrc:Inderlok")
    spur_f = next(p for p in green if p["stops"][0] == apm and len(p["stops"]) == 3)
    spur_r = next(p for p in green if p["stops"][-1] == apm and len(p["stops"]) == 3)
    k, m = main_f["stops"].index(apm), main_r["stops"].index(apm)
    new = [main_f["stops"][:k + 1] + spur_f["stops"][1:],        # Brig. Hoshiar Singh -> Kirti Nagar
           spur_r["stops"] + main_r["stops"][m + 1:],            # Kirti Nagar -> Brig. Hoshiar Singh
           main_f["stops"][k:],                                  # Ashok Park Main -> Inderlok (the branch)
           main_r["stops"][:m + 1]]                              # Inderlok -> Ashok Park Main
    first = patterns.index(main_f)
    for p in (main_f, main_r, spur_f, spur_r):
        patterns.remove(p)
    # The app labels the Inderlok -> Ashok Park Main branch leg "towards Brig. Hoshiar Singh" (and gives Inderlok
    # Platform 3 for it), so that pattern keeps that destination even though it ends at Ashok Park Main.
    towards = [None, None, None, "Brig. Hoshiar Singh"]
    # service 1 = the branch: Route's router makes you change trains between services of one line.
    patterns[first:first] = [{"line": "green", "stops": stops, "towards": t, **({"service": 1} if i >= 2 else {})}
                             for i, (stops, t) in enumerate(zip(new, towards))]


def split_blue_line(patterns):
    """Blue Line trains run Dwarka Sector 21 - Noida Electronic City and Dwarka Sector 21 - Vaishali, so going between
    the Noida side and the Vaishali side means changing at Yamuna Bank (5 min, Platform 3 towards Vaishali; the app and
    the DMRC website agree, 2026-11-07: Noida Sector 18 -> Laxmi Nagar changes there, Indraprastha -> Laxmi Nagar
    doesn't). OSM has the Vaishali side as a stub from Yamuna Bank that rode on for free, so give the Vaishali trains
    their own through patterns as a separate service."""
    yb = "dmrc:Yamuna Bank"
    blue = [p for p in patterns if p["line"] == "blue"]
    main_f = next(p for p in blue if p["stops"][0] == "dmrc:Dwarka Sector 21" and p["stops"][-1] == "dmrc:Noida Electronic City")
    main_r = next(p for p in blue if p["stops"][0] == "dmrc:Noida Electronic City")
    spur_f = next(p for p in blue if p["stops"][0] == yb and p["stops"][-1] == "dmrc:Vaishali")
    spur_r = next(p for p in blue if p["stops"][0] == "dmrc:Vaishali" and p["stops"][-1] == yb)
    k, m = main_f["stops"].index(yb), main_r["stops"].index(yb)
    at = patterns.index(spur_r)
    for p in (spur_f, spur_r):
        patterns.remove(p)
    patterns[at:at] = [
        {"line": "blue", "stops": spur_r["stops"] + main_r["stops"][m + 1:], "towards": None, "service": 1},
        {"line": "blue", "stops": main_f["stops"][:k + 1] + spur_f["stops"][1:], "towards": None, "service": 1},
    ]


def split_pink_line(patterns):
    """Maujpur - Gokulpuri - Johri Enclave - Shiv Vihar is its own service ("pink (ext.)" on the DMRC website): Jafrabad ->
    Gokulpuri and Gokulpuri -> Yamuna Vihar both change at Maujpur - Babarpur, while Jafrabad -> Yamuna Vihar rides
    straight through (website and app, 2026-11-07). OSM runs the loop on into Shiv Vihar, so cut that tail off as a
    separate service."""
    mj = "dmrc:Maujpur - Babarpur"
    pink = [p for p in patterns if p["line"] == "pink"]
    out = next(p for p in pink if p["stops"][-1] == "dmrc:Shiv Vihar")   # ... loop ... Maujpur -> Shiv Vihar
    back = next(p for p in pink if p["stops"][0] == "dmrc:Shiv Vihar")   # Shiv Vihar -> Maujpur ... loop ...
    j = len(out["stops"]) - 1 - out["stops"][::-1].index(mj)
    k = back["stops"].index(mj)
    at = patterns.index(out)
    for p in (out, back):
        patterns.remove(p)
    # The platform table still lists the loop's platforms "towards Shiv Vihar" and the Shiv Vihar side's "towards Majlis
    # Park" (from when trains ran through); the platforms are the same, so let it look past Maujpur for them.
    patterns[at:at] = [
        {**out, "stops": out["stops"][:j + 1], "towards": None, "platform_towards": "Shiv Vihar"},
        {"line": "pink", "stops": out["stops"][j:], "towards": None, "service": 1},
        {"line": "pink", "stops": back["stops"][:k + 1], "towards": None, "service": 1, "platform_towards": "Majlis Park"},
        {**back, "stops": back["stops"][k:], "towards": None},
    ]


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
        if line != "rrts":
            patterns.append({"line": line, "stops": ids, "towards": towards})

    for n in nodes.values():  # the added Namo Bharat stations: one stop node per direction, averaged like the others
        t = n.get("tags", {})
        if t.get("name") in RRTS_ADDED_OSM and t.get("railway") == "stop":
            station("rrts", clean(t["name"]), n["lat"], n["lon"], "rrts")
    for names, mins in ((RRTS_MAIN, RRTS_MAIN_MIN), (RRTS_MAIN[::-1], RRTS_MAIN_MIN[::-1]),
                        (RRTS_SPUR, RRTS_SPUR_MIN), (RRTS_SPUR[::-1], RRTS_SPUR_MIN[::-1])):
        assert len(mins) == len(names) - 1
        ids = [f"rrts:{nm}" for nm in names]
        assert all(i in stations for i in ids), [i for i in ids if i not in stations]
        patterns.append({"line": "rrts", "stops": ids, "towards": None, "fixed_min": mins})

    rapid_xy = {}
    for rid in (4481323, 7890002):
        for m in rels[rid]["members"]:
            if m["role"].startswith("stop"):
                n = nodes[m["ref"]]
                rapid_xy.setdefault(clean(n["tags"]["name"]), (n["lat"], n["lon"]))
    for names, towards in RAPID_PATTERNS:
        ids = [station("rmgl", nm, *rapid_xy[nm], "rapid") for nm in names]
        fast = "Sector 55-56" in names  # the Sector 55-56 line runs faster than the Cyber City loop
        patterns.append({"line": "rapid", "stops": ids, "towards": towards, **({"speed": RAPID_LINE_SPEED} if fast else {})})

    split_green_line(patterns)
    split_blue_line(patterns)
    split_pink_line(patterns)

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
        speed = p.pop("speed", speed)
        hops_km, hops_min = [], []
        fixed = p.pop("fixed_min", None)
        for i, (a, b) in enumerate(zip(p["stops"], p["stops"][1:])):
            d = km((stations[a]["lat"], stations[a]["lon"]), (stations[b]["lat"], stations[b]["lon"]))
            d *= 1.06
            hops_km.append(round(official_km.get((a, b), d), 3))
            hops_min.append(round(fixed[i] + RRTS_DWELL, 2) if fixed else round(dwell + d / speed * 60, 2))
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
    overrides = {(i, j): slab for i, j, slab in fare_overrides(ordered, patterns)}
    position = {s["id"]: i for i, s in enumerate(ordered)}
    for a, b, rupees in APP_FARES:
        overrides[(min(position[a], position[b]), max(position[a], position[b]))] = FARES["dmrc"]["weekday"].index(rupees)
    fares = {**FARES, "dmrc": {**FARES["dmrc"], "overrides": sorted([i, j, slab] for (i, j), slab in overrides.items())}}

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
