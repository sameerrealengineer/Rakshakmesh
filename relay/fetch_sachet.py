#!/usr/bin/env python3
"""
RakshakMesh alert relay (PRD 5.2).

Fetches NDMA SACHET CAP 1.2 alerts from the public RSS feed, keeps the ones that cover
Odisha districts, and writes one small JSON file per district:

    site/alerts/<District>.json   (schema: PRD 8.2)
    site/alerts/index.json

Runs every 30 minutes in GitHub Actions and is published on GitHub Pages. The phone downloads
only its own district file, so even 2G works. Standard library only.

IMD's own API needs a whitelisted static IP, so it is not called from here (see TODO below).
SACHET already carries IMD/CWC/OSDMA CAP alerts.
"""
from __future__ import annotations

import argparse
import datetime as dt
import json
import os
import re
import sys
import urllib.request
import xml.etree.ElementTree as ET
from typing import Callable, Iterable

DEFAULT_FEEDS = [
    "https://sachet.ndma.gov.in/cap_public_website/rss/rss_india.xml",
]
CAP_NS = {"cap": "urn:oasis:names:tc:emergency:cap:1.2"}
IST = dt.timezone(dt.timedelta(hours=5, minutes=30))
USER_AGENT = "RakshakMesh-relay/1.0 (+https://github.com/sameerrealengineer/Rakshakmesh)"

# 30 districts, with spellings seen in official text. Keys match the app's district names.
DISTRICTS: dict[str, list[str]] = {
    "Angul": ["angul", "anugul"],
    "Balangir": ["balangir", "bolangir"],
    "Balasore": ["balasore", "baleswar", "baleshwar"],
    "Bargarh": ["bargarh"],
    "Bhadrak": ["bhadrak"],
    "Boudh": ["boudh", "baudh"],
    "Cuttack": ["cuttack"],
    "Deogarh": ["deogarh", "debagarh"],
    "Dhenkanal": ["dhenkanal"],
    "Gajapati": ["gajapati"],
    "Ganjam": ["ganjam"],
    "Jagatsinghpur": ["jagatsinghpur", "jagatsinghapur"],
    "Jajpur": ["jajpur", "jajapur"],
    "Jharsuguda": ["jharsuguda"],
    "Kalahandi": ["kalahandi"],
    "Kandhamal": ["kandhamal", "phulbani"],
    "Kendrapara": ["kendrapara", "kendrapada"],
    "Kendujhar": ["kendujhar", "keonjhar"],
    "Khordha": ["khordha", "khurda", "khurdha", "bhubaneswar"],
    "Koraput": ["koraput"],
    "Malkangiri": ["malkangiri"],
    "Mayurbhanj": ["mayurbhanj"],
    "Nabarangpur": ["nabarangpur", "nabarangapur", "nowrangpur"],
    "Nayagarh": ["nayagarh"],
    "Nuapada": ["nuapada"],
    "Puri": ["puri"],
    "Rayagada": ["rayagada"],
    "Sambalpur": ["sambalpur"],
    "Subarnapur": ["subarnapur", "sonepur", "sonpur"],
    "Sundargarh": ["sundargarh"],
}

# Approximate district HQ (lat, lon), same as the app; used for CAP polygon matching.
DISTRICT_HQ: dict[str, tuple[float, float]] = {
    "Angul": (20.84, 85.10), "Balangir": (20.71, 83.48), "Balasore": (21.49, 86.93),
    "Bargarh": (21.33, 83.62), "Bhadrak": (21.06, 86.50), "Boudh": (20.84, 84.32),
    "Cuttack": (20.46, 85.88), "Deogarh": (21.54, 84.73), "Dhenkanal": (20.66, 85.60),
    "Gajapati": (18.85, 84.13), "Ganjam": (19.39, 84.88), "Jagatsinghpur": (20.26, 86.17),
    "Jajpur": (20.85, 86.33), "Jharsuguda": (21.86, 84.01), "Kalahandi": (19.91, 83.17),
    "Kandhamal": (20.47, 84.23), "Kendrapara": (20.50, 86.42), "Kendujhar": (21.63, 85.58),
    "Khordha": (20.18, 85.62), "Koraput": (18.81, 82.71), "Malkangiri": (18.35, 81.89),
    "Mayurbhanj": (21.94, 86.73), "Nabarangpur": (19.23, 82.55), "Nayagarh": (20.13, 85.10),
    "Nuapada": (20.82, 82.53), "Puri": (19.81, 85.83), "Rayagada": (19.17, 83.42),
    "Sambalpur": (21.47, 83.97), "Subarnapur": (20.84, 83.90), "Sundargarh": (22.12, 84.03),
}

STATE_WORDS = ("odisha", "orissa")
SEVERITY_LEVEL = {"extreme": "red", "severe": "orange", "moderate": "yellow", "minor": "green"}
LEVEL_RANK = {"green": 0, "yellow": 1, "orange": 2, "red": 3}

Fetcher = Callable[[str], bytes]


def http_get(url: str, timeout: int = 30) -> bytes:
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return r.read()


def parse_rss_links(xml_bytes: bytes) -> list[str]:
    """CAP XML links from an RSS 2.0 or Atom feed."""
    root = ET.fromstring(xml_bytes)
    links: list[str] = []
    for item in root.iter("item"):
        link = (item.findtext("link") or "").strip()
        if link:
            links.append(link)
    for entry in root.iter("{http://www.w3.org/2005/Atom}entry"):
        for l in entry.findall("{http://www.w3.org/2005/Atom}link"):
            href = l.get("href")
            if href:
                links.append(href)
    return links


def parse_time(s: str | None) -> dt.datetime | None:
    if not s:
        return None
    try:
        return dt.datetime.fromisoformat(s.strip().replace("Z", "+00:00"))
    except ValueError:
        return None


def iso(t: dt.datetime | None) -> str | None:
    return t.astimezone(IST).isoformat(timespec="seconds") if t else None


def point_in_polygon(lat: float, lon: float, poly: list[tuple[float, float]]) -> bool:
    inside = False
    j = len(poly) - 1
    for i in range(len(poly)):
        yi, xi = poly[i]
        yj, xj = poly[j]
        if (yi > lat) != (yj > lat) and lon < (xj - xi) * (lat - yi) / ((yj - yi) or 1e-12) + xi:
            inside = not inside
        j = i
    return inside


def parse_polygon(text: str) -> list[tuple[float, float]]:
    """CAP polygon: 'lat,lon lat,lon ...'."""
    pts = []
    for pair in text.split():
        try:
            a, b = pair.split(",")[:2]
            pts.append((float(a), float(b)))
        except ValueError:
            continue
    return pts if len(pts) >= 3 else []


def match_districts(area_desc: str, polygons: Iterable[list[tuple[float, float]]]) -> set[str]:
    text = area_desc.lower()
    found = {
        d for d, names in DISTRICTS.items()
        if any(re.search(rf"\b{re.escape(n)}\b", text) for n in names)
    }
    for poly in polygons:
        for d, (lat, lon) in DISTRICT_HQ.items():
            if point_in_polygon(lat, lon, poly):
                found.add(d)
    # Whole-state alert with no district named: applies to every district.
    if not found and any(w in text for w in STATE_WORDS):
        found = set(DISTRICTS)
    return found


def level_for(severity: str, text: str) -> str:
    t = text.lower()
    for colour in ("red", "orange", "yellow", "green"):
        if re.search(rf"\b{colour}\b\s*(alert|warning|zone|colour|color)", t):
            return colour
    return SEVERITY_LEVEL.get(severity.strip().lower(), "yellow")


def parse_cap(xml_bytes: bytes, now: dt.datetime) -> list[dict]:
    """One alert dict per CAP file (or none if expired / not for Odisha)."""
    root = ET.fromstring(xml_bytes)
    if root.tag != "{%s}alert" % CAP_NS["cap"]:
        return []
    ident = (root.findtext("cap:identifier", namespaces=CAP_NS) or "").strip()
    status = (root.findtext("cap:status", namespaces=CAP_NS) or "").strip()
    msg_type = (root.findtext("cap:msgType", namespaces=CAP_NS) or "").strip()
    if status and status != "Actual":
        return []
    if msg_type == "Cancel":
        return []
    sent = parse_time(root.findtext("cap:sent", namespaces=CAP_NS))
    infos = root.findall("cap:info", CAP_NS)
    if not ident or not infos:
        return []

    def lang(info):
        return (info.findtext("cap:language", namespaces=CAP_NS) or "en").lower()

    en = next((i for i in infos if lang(i).startswith("en")), infos[0])
    odia = next((i for i in infos if lang(i).startswith("or")), None)

    expires = parse_time(en.findtext("cap:expires", namespaces=CAP_NS))
    if expires and expires <= now:
        return []

    areas = [a for i in infos for a in i.findall("cap:area", CAP_NS)]
    area_desc = " ; ".join((a.findtext("cap:areaDesc", namespaces=CAP_NS) or "") for a in areas)
    polys = [parse_polygon(p.text or "") for a in areas for p in a.findall("cap:polygon", CAP_NS)]
    districts = match_districts(area_desc, [p for p in polys if p])
    if not districts:
        return []

    def body(info) -> str:
        parts = [info.findtext(f"cap:{k}", namespaces=CAP_NS) for k in ("description", "instruction")]
        return "\n\n".join(p.strip() for p in parts if p and p.strip())

    headline = (en.findtext("cap:headline", namespaces=CAP_NS) or en.findtext("cap:event", namespaces=CAP_NS) or "").strip()
    severity = en.findtext("cap:severity", namespaces=CAP_NS) or ""
    original = body(en)
    if odia is not None and body(odia):
        original = f"{original}\n\n— ଓଡ଼ିଆ —\n{body(odia)}" if original else body(odia)
    sender = (en.findtext("cap:senderName", namespaces=CAP_NS) or root.findtext("cap:sender", namespaces=CAP_NS) or "").strip()

    return [{
        "id": f"sachet-{ident}",
        "source": "SACHET",
        "agency": sender or None,
        "level": level_for(severity, f"{headline} {original}"),
        "headline": headline[:300],
        "issuedAt": iso(parse_time(en.findtext("cap:effective", namespaces=CAP_NS)) or sent),
        "validUntil": iso(expires),
        "originalText": original[:4000],
        "simplifiedOdia": None,  # P2: template + Sarvam Translate (PRD 7.1 A4)
        "_districts": sorted(districts),
    }]


def build(feeds: list[str], fetch: Fetcher, now: dt.datetime) -> tuple[dict[str, list[dict]], list[str]]:
    per_district: dict[str, list[dict]] = {d: [] for d in DISTRICTS}
    errors: list[str] = []
    seen: set[str] = set()
    for feed in feeds:
        try:
            links = parse_rss_links(fetch(feed))
        except Exception as e:  # noqa: BLE001 - keep going with other feeds
            errors.append(f"feed {feed}: {e}")
            continue
        for link in links:
            if link in seen:
                continue
            seen.add(link)
            try:
                alerts = parse_cap(fetch(link), now)
            except Exception as e:  # noqa: BLE001
                errors.append(f"cap {link}: {e}")
                continue
            for a in alerts:
                for d in a.pop("_districts"):
                    if all(x["id"] != a["id"] for x in per_district[d]):
                        per_district[d].append(dict(a))
    for d in per_district:
        per_district[d].sort(key=lambda a: (-LEVEL_RANK.get(a["level"], 0), a["issuedAt"] or ""))
    # TODO(IMD): when IMD whitelists a static IP, merge district warnings from api.imd.gov.in here
    # with source "IMD" (PRD 5.1). The warning colour shown in the app must come from IMD or SACHET.
    return per_district, errors


def write_site(out_dir: str, per_district: dict[str, list[dict]], now: dt.datetime, errors: list[str]) -> None:
    alerts_dir = os.path.join(out_dir, "alerts")
    os.makedirs(alerts_dir, exist_ok=True)
    fetched = iso(now)
    index = {"fetchedAt": fetched, "source": "NDMA SACHET CAP", "districts": {}, "errors": len(errors)}
    for d, alerts in per_district.items():
        doc = {"district": d, "fetchedAt": fetched, "source": "SACHET", "alerts": alerts}
        with open(os.path.join(alerts_dir, f"{d}.json"), "w", encoding="utf-8") as f:
            json.dump(doc, f, ensure_ascii=False, separators=(",", ":"))
        index["districts"][d] = {
            "count": len(alerts),
            "highest": max((a["level"] for a in alerts), key=lambda l: LEVEL_RANK.get(l, 0), default=None),
        }
    with open(os.path.join(alerts_dir, "index.json"), "w", encoding="utf-8") as f:
        json.dump(index, f, ensure_ascii=False, indent=1)
    with open(os.path.join(out_dir, "index.html"), "w", encoding="utf-8") as f:
        f.write(
            "<!doctype html><meta charset=utf-8><title>RakshakMesh alert relay</title>"
            "<p>Per-district JSON for the RakshakMesh app, built from NDMA SACHET CAP alerts. "
            f"Last fetched {fetched}. See <a href=alerts/index.json>alerts/index.json</a>.</p>"
        )


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--out", default="site")
    ap.add_argument("--feed", action="append", help="RSS feed URL (repeatable)")
    args = ap.parse_args(argv)
    feeds = args.feed or [f for f in os.environ.get("SACHET_FEEDS", "").split() if f] or DEFAULT_FEEDS
    now = dt.datetime.now(dt.timezone.utc)
    per_district, errors = build(feeds, http_get, now)
    for e in errors:
        print("WARN", e, file=sys.stderr)
    total_links_failed = all(e.startswith("feed ") for e in errors) and len(errors) == len(feeds)
    if total_links_failed:
        # Do not publish an empty "fresh" file when every feed failed: the app would wrongly
        # show "no alerts, updated just now". Keep the previous deployment instead.
        print("ERROR: all feeds failed; not writing output", file=sys.stderr)
        return 1
    write_site(args.out, per_district, now, errors)
    active = {d: len(a) for d, a in per_district.items() if a}
    print(f"OK: {sum(active.values())} district-alerts across {len(active)} districts: {active}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
