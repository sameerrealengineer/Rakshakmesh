#!/usr/bin/env bash
# Builds the offline data bundled inside the APK (run in CI; needs internet, Java 21, osmium-tool):
#   app/src/main/assets/map/odisha.pmtiles      OpenStreetMap vector map of Odisha (OpenMapTiles schema)
#   app/src/main/assets/map/glyphs/...           fonts for map labels
#   app/src/main/assets/shelters.csv             cyclone shelters, hospitals, police from OSM
# Outputs are gitignored. Map data (c) OpenStreetMap contributors, ODbL.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK="${MAP_WORK_DIR:-$ROOT/.mapwork}"
ASSETS="$ROOT/app/src/main/assets"
BOUNDS="81.3,17.7,87.6,22.6"           # Odisha (+ a little of the neighbouring states)
MAX_MB="${MAP_MAX_MB:-100}"
PBF_URL="https://download.geofabrik.de/asia/india/eastern-zone-latest.osm.pbf"
PLANETILER_URL="https://github.com/onthegomap/planetiler/releases/latest/download/planetiler.jar"
GLYPHS_BASE="https://raw.githubusercontent.com/protomaps/basemaps-assets/main/fonts"

mkdir -p "$WORK" "$ASSETS/map/glyphs"
cd "$WORK"

[ -s eastern.osm.pbf ] || curl -fL --retry 3 -o eastern.osm.pbf "$PBF_URL"
[ -s planetiler.jar ] || curl -fL --retry 3 -o planetiler.jar "$PLANETILER_URL"

build_tiles() {
  local zoom="$1"
  rm -f odisha.pmtiles
  java -Xmx5g -jar planetiler.jar \
    --osm-path=eastern.osm.pbf --download --download-threads=4 \
    --bounds="$BOUNDS" --maxzoom="$zoom" \
    --exclude-layers=building,housenumber,aerodrome_label,aeroway \
    --output=odisha.pmtiles --force
}

build_tiles 13
size_mb=$(( $(stat -c %s odisha.pmtiles) / 1024 / 1024 ))
if [ "$size_mb" -gt "$MAX_MB" ]; then
  echo "Map is ${size_mb} MB at z13 (limit ${MAX_MB}); rebuilding at z12"
  build_tiles 12
  size_mb=$(( $(stat -c %s odisha.pmtiles) / 1024 / 1024 ))
fi
cp odisha.pmtiles "$ASSETS/map/odisha.pmtiles"
echo "Map: ${size_mb} MB"

for font in "Noto Sans Regular" "Noto Sans Medium"; do
  mkdir -p "$ASSETS/map/glyphs/$font"
  for range in 0-255 256-511 512-767 768-1023 8192-8447; do
    f="$ASSETS/map/glyphs/$font/$range.pbf"
    [ -s "$f" ] || curl -fsL --retry 3 -o "$f" "$GLYPHS_BASE/${font// /%20}/$range.pbf"
  done
done

osmium tags-filter eastern.osm.pbf \
  nwr/amenity=shelter,hospital,clinic,doctors,police,school,community_centre \
  nwr/emergency=assembly_point nwr/building=shelter \
  -o places.osm.pbf --overwrite
osmium export places.osm.pbf -f geojsonseq --geometry-types=point,polygon \
  --attributes=type,id -o places.geojsonseq --overwrite
python3 -I "$ROOT/tools/osm_places.py" places.geojsonseq "$BOUNDS" "$(date -u +%Y-%m-%d)" > "$ASSETS/shelters.csv"
echo "Places: $(( $(grep -vc '^#' "$ASSETS/shelters.csv") - 1 )) rows"
