"""Fetch metro/RRTS route relations for Delhi NCR from OpenStreetMap (Overpass).

Writes tools/raw/osm.json. Run once when the network changes; the app never
touches the network itself.
"""
import json
import pathlib
import urllib.parse
import urllib.request

BBOX = "28.3,76.8,29.1,77.75"  # Delhi NCR incl. Meerut
QUERY = f"""
[out:json][timeout:180];
relation["type"="route"]["route"~"^(subway|light_rail|train|monorail)$"]({BBOX})->.all;
(
  relation.all["operator"~"Delhi Metro|DMRC|NMRC|Noida|Rapid Metro|National Capital Region|NCRTC",i];
  relation.all["network"~"Delhi Metro|Noida Metro|Rapid Metro|Namo Bharat|RRTS",i];
  relation.all["name"~"Delhi Metro|Aqua Line|Rapid Metro|RRTS|Namo Bharat",i];
)->.r;
.r out body;
node(r.r);
out body;
"""

OUT = pathlib.Path(__file__).parent / "raw" / "osm.json"


def main():
    data = urllib.parse.urlencode({"data": QUERY}).encode()
    req = urllib.request.Request(
        "https://overpass-api.de/api/interpreter",
        data=data,
        headers={"User-Agent": "metro-offline-route-app/0.1 (personal, non-commercial)"},
    )
    with urllib.request.urlopen(req, timeout=240) as r:
        payload = json.load(r)
    OUT.write_text(json.dumps(payload, ensure_ascii=False))
    rels = [e for e in payload["elements"] if e["type"] == "relation"]
    nodes = [e for e in payload["elements"] if e["type"] == "node"]
    print(f"{len(rels)} route relations, {len(nodes)} nodes -> {OUT}")


if __name__ == "__main__":
    main()
