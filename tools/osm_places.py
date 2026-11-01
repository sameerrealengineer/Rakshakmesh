#!/usr/bin/env python3
"""Turns an osmium geojsonseq export into the app's shelters.csv (cyclone shelters, hospitals, police).

usage: osm_places.py places.geojsonseq "minlon,minlat,maxlon,maxlat" YYYY-MM-DD > shelters.csv
"""
import csv
import json
import re
import sys

SHELTER_NAME = re.compile(r"shelter|ashray|आश्रय|ଆଶ୍ରୟ", re.I)
CYCLONE_NAME = re.compile(
    r"cyclone|\bm\.?\s?p\.?\s?c\.?\s?s\b|\bm\.?c\.?s\b|multi[\s-]?purpose.{0,25}shelter|ବାତ୍ୟା|चक्रवात", re.I)
FLOOD_NAME = re.compile(r"flood.{0,25}(shelter|ashray)|ବନ୍ୟା.{0,10}ଆଶ୍ରୟ|बाढ़.{0,10}आश्रय", re.I)
NOT_A_SHELTER = re.compile(r"warning|office|centre for|research|control room|bus|road|street", re.I)
HEADER = ["id", "name", "name_odia", "village", "block", "district", "lat", "lon", "capacity", "type", "source", "verified_on"]


def centroid(geom):
    t, c = geom["type"], geom["coordinates"]
    if t == "Point":
        return c[1], c[0]
    if t == "Polygon":
        ring = c[0]
    elif t == "MultiPolygon":
        ring = max((p[0] for p in c), key=len)
    else:
        return None
    xs = [p[0] for p in ring]
    ys = [p[1] for p in ring]
    return sum(ys) / len(ys), sum(xs) / len(xs)


def classify(tags):
    name = " ".join(filter(None, [tags.get("name"), tags.get("name:en"), tags.get("name:or"), tags.get("official_name")]))
    amenity = tags.get("amenity", "")
    if amenity in ("hospital", "clinic", "doctors"):
        return "hospital"
    if amenity == "police":
        return "police"
    shelter_type = tags.get("shelter_type", "")
    if shelter_type in ("public_transport", "picnic_shelter", "field_shelter", "lean_to", "gazebo"):
        return None
    if tags.get("highway") or tags.get("route") or tags.get("place") or tags.get("boundary"):
        return None
    # By name, whatever the other tags are (buildings, schools, community halls ...).
    if FLOOD_NAME.search(name):
        return "flood_shelter"
    if CYCLONE_NAME.search(name) and (SHELTER_NAME.search(name) or not NOT_A_SHELTER.search(name)):
        return "cyclone_shelter"
    if amenity == "shelter" or tags.get("building") == "shelter" or tags.get("emergency") == "assembly_point":
        if shelter_type in ("emergency", "disaster") or SHELTER_NAME.search(name):
            return "cyclone_shelter"
    return None


def main():
    path, bounds, date = sys.argv[1], sys.argv[2], sys.argv[3]
    minlon, minlat, maxlon, maxlat = map(float, bounds.split(","))
    out = csv.writer(sys.stdout, lineterminator="\n")
    print("# Generated from OpenStreetMap (c) OpenStreetMap contributors, ODbL. Not a government list.")
    out.writerow(HEADER)
    seen = set()
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip().lstrip("\x1e")
            if not line:
                continue
            feat = json.loads(line)
            tags = feat.get("properties") or {}
            kind = classify(tags)
            if not kind:
                continue
            pt = centroid(feat["geometry"])
            if not pt:
                continue
            lat, lon = pt
            if not (minlat <= lat <= maxlat and minlon <= lon <= maxlon):
                continue
            osm_id = f"osm-{tags.get('@type', 'x')[0]}{tags.get('@id', len(seen))}"
            if osm_id in seen:
                continue
            seen.add(osm_id)
            default = {"hospital": "Hospital / clinic", "police": "Police station"}.get(kind)
            name = (tags.get("name:en") or tags.get("name") or default or "").strip()
            if not name:
                continue
            cap = re.sub(r"\D", "", tags.get("capacity", ""))[:6]
            out.writerow([
                osm_id, name, tags.get("name:or", ""),
                tags.get("addr:village") or tags.get("addr:city") or tags.get("addr:suburb") or "",
                tags.get("addr:subdistrict", ""), tags.get("addr:district", ""),
                f"{lat:.5f}", f"{lon:.5f}", cap, kind, "OpenStreetMap", date,
            ])


if __name__ == "__main__":
    main()
