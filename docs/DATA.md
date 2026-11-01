# Data guide

## Shelters (`app/src/main/assets/shelters.csv`)

Ships **empty**. Until real rows are added, the Shelter tab says
"No verified shelter in our data — call 1077". The app never guesses (FR-SHL-04).

Format (UTF-8, one header row, `#` lines are comments):

```
id,name,name_odia,village,block,district,lat,lon,capacity,type,source,verified_on
PUR-AST-01,"Astaranga MCS, Ward 5",ଅସ୍ତରଙ୍ଗ ବହୁମୁଖୀ ବାତ୍ୟା ଆଶ୍ରୟସ୍ଥଳ,Astaranga,Astaranga,Puri,19.9800,86.2700,1500,cyclone,OSDMA list 2026,2026-10-01
```
(The row above is a format example, not a real shelter.)

Rules (enforced by `core/.../Shelters.kt`):
- `source` and `verified_on` are required. Rows without them are **dropped**, not shown greyed out.
- `lat`/`lon` in decimal degrees. Rows with bad coordinates are dropped.
- `type`: `cyclone` or `flood`. `capacity` may be blank.

Where to get rows, in order (PRD 5.4):
1. Shelter list from OSDMA or the district emergency officer (ask formally).
2. District disaster-management plans on `<district>.odisha.gov.in`.
3. OpenStreetMap shelters, field-checked by the partner.

The CSV is loaded into the app's database on every start, so a new APK replaces the old list.

## Offline map (PMTiles)

The map shows OpenStreetMap data from a local `.pmtiles` file in the **OpenMapTiles** schema.
We never download tiles from `tile.openstreetmap.org` (OSMF tile policy).

Build one for the pilot area with [Planetiler](https://github.com/onthegomap/planetiler):

```
java -jar planetiler.jar --download --area=india \
  --bounds=85.6,19.7,86.5,20.4 --maxzoom=15 --output=region.pmtiles
```

Keep it under ~50 MB (one block at zoom 6–15 is usually tens of MB). Then either:
- host it (GitHub Release, Cloudflare R2) and put the URL in **Settings → Map download address**;
  users tap *Download map* once on Wi-Fi, or
- copy the file to the phone (NGO laptop, SD card) and use **Load map file from phone**.

The style has no text labels (labels need font files). Shelters are green dots, the user is a
blue dot, and names are in the list under the map. Attribution "© OpenStreetMap contributors" is
shown.

## Alerts relay

`relay/fetch_sachet.py` reads the SACHET RSS feed (default
`https://sachet.ndma.gov.in/cap_public_website/rss/rss_india.xml`; override with the
`SACHET_FEEDS` repo variable), follows each CAP 1.2 file, keeps alerts whose area names an Odisha
district (spelling variants included) or whose polygon covers a district HQ, and writes
`alerts/<District>.json` (PRD 8.2 schema). A whole-state alert goes to every district. Expired
and cancelled alerts are dropped. If every feed fails, nothing is published, so the app keeps
showing the last good copy with its real age.

Colour comes from the alert text ("red alert", "orange warning") or else CAP severity:
Extreme → red, Severe → orange, Moderate → yellow, Minor → green.

IMD's API needs a whitelisted static IP; see the TODO in the script.
