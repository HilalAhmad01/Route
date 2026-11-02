"""Fetch boarding platform numbers from DMRC's journey planner.

For every (station, line, next stop) in network.json, asks DMRC for the route
station -> next stop and keeps the platform it names for the first leg.
Results are cached in tools/raw/dmrc_platforms.json, so a rerun only fetches
what is missing (delete the file to refresh everything). Run every few months.
"""
import datetime
import json
import pathlib
import re
import time
import urllib.parse
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parent.parent
NETWORK = ROOT / "app" / "src" / "main" / "assets" / "network.json"
OUT = ROOT / "tools" / "raw" / "dmrc_platforms.json"

API = "https://backend.delhimetrorail.com/api/v2/en"
HEADERS = {
    "User-Agent": "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/130 Safari/537.36",
    "Origin": "https://www.delhimetrorail.com",
    "Referer": "https://www.delhimetrorail.com/",
}
DELAY = 1.0
# How many stops ahead to try when DMRC answers with no platform or routes over another line.
TRIES = 5
# DMRC calls Airport Express the Orange Line.
LINE_NAMES = {"airport": ("airport", "orange")}

# Our station name -> DMRC's name, where normalising alone does not match.
NAMES = {
    "Anand Vihar": "ANAND VIHAR ISBT",
    "Badarpur": "BADARPUR BORDER",
    "Badhkal Mor": "BADKAL MOR",
    "Barakhambha Road": "BARAKHAMBA ROAD",
    "Brigadier Hoshiyar Singh": "BRIG. HOSHIAR SINGH",
    "Ghevra": "GHEVRA METRO STATION",
    "GTB Nagar": "GURU TEG BAHADUR NAGAR",
    "IGI Airport": "AIRPORT (T-3)",
    "IIT Delhi": "IIT",
    "Jaffrabad": "JAFRABAD",
    "Jawaharlal Nehru Stadium": "JLN STADIUM",
    "Mundka Industrial Area": "MUNDKA INDUSTRIAL AREA (MIA)",
    "Nanaksar - Sonia Vihar": "SONIA VIHAR",
    "New Delhi": "NEW DELHI (Yellow & Airport Line)",
    "Noida Golf Course": "GOLF COURSE",
    "Noida Sector 34": "SECTOR - 34 NOIDA",
    "Noida Sector 52": "SECTOR - 52 NOIDA",
    "Noida Sector 59": "SECTOR - 59 NOIDA",
    "Noida Sector 61": "SECTOR - 61 NOIDA",
    "Noida Sector 62": "SECTOR - 62 NOIDA",
    "Raja Nahar Singh": "RAJA NAHAR SINGH (BALLABHGARH)",
    "Rajendra Nagar": "MAJOR MOHIT SHARMA RAJENDRA NAGAR",
    "Rohini East": "ROHINI",
    "Shaheed Sthal": "SHAHEED STHAL ( NEW BUS ADDA)",
    "Shree Ram Mandir Mayur Vihar": "SHREE RAM MAYUR VIHAR MANDIR (earlier MAYUR VIHAR POCKET-1)",
    "Surajmal Stadium": "MAHARAJA SURAJMAL STADIUM",
    "Tughlakabad": "TUGHLAKABAD STATION",
}


def norm(s):
    s = re.sub(r"\(earlier.*?\)+", "", s, flags=re.I)
    return re.sub(r"[^a-z0-9]", "", s.lower())


def get(url):
    req = urllib.request.Request(url, headers=HEADERS)
    with urllib.request.urlopen(req, timeout=60) as r:
        return json.load(r)


def main():
    net = json.loads(NETWORK.read_text())
    lines = {l["id"]: l for l in net["lines"]}
    names = {s["id"]: s["name"] for s in net["stations"]}

    by_norm = {norm(x["station_name"]): x for x in get(f"{API}/station_list")}

    def lookup(sid):
        name = names[sid]
        return by_norm.get(norm(NAMES.get(name, name)))

    cache = json.loads(OUT.read_text()) if OUT.exists() else {"fetched": None, "platforms": {}}
    done = cache["platforms"]

    jobs = {}
    for p in net["patterns"]:
        if lines[p["line"]]["system"] not in ("dmrc", "dmrc_airport"):
            continue
        stops = p["stops"]
        for i, sid in enumerate(stops[:-1]):
            key = f"{sid}|{p['line']}|{stops[i + 1]}"
            if key in jobs or key in done or lookup(sid) is None:
                continue
            # Stops ahead that DMRC knows, nearest first, so an unknown neighbour doesn't block us.
            targets = [t for t in dict.fromkeys(stops[i + 1:]) if lookup(t) and t != sid][:TRIES]
            if targets:
                jobs[key] = (sid, p["line"], targets)

    unmatched = sorted({names[s] for p in net["patterns"] for s in p["stops"]
                        if s.startswith("dmrc:") and lookup(s) is None})
    print(f"{len(jobs)} to fetch, {len(done)} cached; no DMRC code for: {', '.join(unmatched)}")

    stamp = urllib.parse.quote(datetime.datetime.now().strftime("%Y-%m-%d %H:%M:00.000"))
    skipped = []
    for n, (key, (sid, line, targets)) in enumerate(jobs.items(), 1):
        want = LINE_NAMES.get(line, (lines[line]["name"].split()[0].lower(),))
        why = []
        for target in targets:
            a, b = lookup(sid)["station_code"], lookup(target)["station_code"]
            try:
                r = get(f"{API}/station_route/{a}/{b}/least-distance/{stamp}")
            except (OSError, ValueError) as e:
                why.append(f"{names[target]}: {e}")
                continue
            finally:
                time.sleep(DELAY)
            leg = (r.get("route") or [None])[0]
            if not leg or not leg.get("line", "").lower().startswith(want):
                why.append(f"{names[target]}: via {leg and leg.get('line')}")
                continue
            m = re.search(r"\d+", leg.get("platform_name") or "")
            if not m:
                why.append(f"{names[target]}: platform {leg.get('platform_name')!r}")
                continue
            done[key] = {"platform": m.group(), "towards": leg.get("towards_station")}
            break
        else:
            skipped.append(f"{key}: " + "; ".join(why))
        if n % 25 == 0:
            print(f"  {n}/{len(jobs)}")
            OUT.write_text(json.dumps(cache, ensure_ascii=False, indent=1, sort_keys=True))

    cache["fetched"] = datetime.date.today().isoformat()
    OUT.write_text(json.dumps(cache, ensure_ascii=False, indent=1, sort_keys=True))
    print(f"{len(done)} platforms -> {OUT}")
    for s in skipped:
        print("  skipped", s)


if __name__ == "__main__":
    main()
