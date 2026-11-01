# RakshakMesh

Odia-first Android app for Odisha cyclones and floods. Before landfall it shows what the official
warning means and where the nearest shelter is. After landfall it lets a family send SOS and
"I am safe" by **SMS**, even on one bar of 2G. Internet and AI are optional extras, never on the
SOS path. Product spec: [docs/PRD_v2.0.md](docs/PRD_v2.0.md).

## What the app does

| Feature | Works without internet |
| --- | --- |
| **SOS**: big button, 3 choices, GPS (20 s timeout → last fix → saved landmark), ≤160-char SMS to family via the phone's SMS app, **and** broadcast to nearby phones | Yes |
| **Nearby help (mesh)**: finds other RakshakMesh phones by Bluetooth/Wi-Fi automatically — no data, SIM or tower. Share SOS, text, voice notes, photos, videos. Messages hop phone-to-phone. Runs in background; loud alarm on nearby SOS; a phone with signal can forward an SOS by SMS | Yes |
| **Offline map of all Odisha** (OpenStreetMap, bundled in the APK) with cyclone shelters, hospitals, police stations and nearby SOS; tap a place for distance and a compass arrow | Yes |
| **Family check-in**: I am safe / at a shelter / need help — SMS + mesh | Yes |
| **Official alerts** for your district (IMD / NDMA SACHET), saved for offline use, also passed between phones over the mesh; built-in safety guide | Saved copy + guide |
| Calls to 112 / 1070 / 1077, evacuation checklist (read aloud), outbox with honest states, cyclone (battery) mode, Odia / Hindi / English | Yes |

**Permissions:** location, nearby devices (Bluetooth/Wi-Fi), notifications, microphone, internet.
Never: `SEND_SMS`, `READ_SMS`, `CALL_PHONE`, background location.

## Layout

```
core/    Plain Kotlin logic, no Android: SMS builder, distance/direction, nearest shelters,
         shelter CSV, alert feed parsing, staleness, outbox states. Unit-tested.
app/     Android app (Kotlin, Jetpack Compose, Room, WorkManager, MapLibre). minSdk 26.
relay/   SACHET → per-district JSON script (Python stdlib) + tests.
tools/   build_map_data.sh + osm_places.py: build the bundled Odisha map and places (CI).
         strings_source.py: source of all en/or/hi UI text (regenerates res/values*/strings.xml).
         generate_audio.py: optional build-time Odia audio with Sarvam Bulbul.
docs/    PRD, data guide, acceptance test log, translation review.
```

## Build

```
./gradlew :core:test                 # works anywhere with JDK 17+
./gradlew :app:assembleDebug         # needs the Android SDK (ANDROID_HOME or local.properties)
cd relay && python3 -m unittest      # relay tests
```

GitHub Actions (`.github/workflows/android.yml`) runs the tests, builds the debug APK, runs lint,
checks the APK for API keys (AT-18) and forbidden permissions, and uploads the APK as an artifact.

## Releases

Every push builds signed release APKs in GitHub Actions (artifact **RakshakMesh-release-apks**):
`app-arm64-v8a-release.apk` for most phones, `app-universal-release.apk` for any phone.
The offline map and places are rebuilt weekly from OpenStreetMap.

To keep one signing key across releases (needed so updates install over old versions), add
repository secrets `KEYSTORE_B64` (base64 of a .jks), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

Alerts come from the *Alert relay* workflow (every 30 min on `main`), which writes
`alerts/<District>.json` to the `alerts-data` branch.

## Before handing out

1. Run the real-phone mesh test: [docs/MESH_TEST.md](docs/MESH_TEST.md).
2. Odia / Hindi text is machine-drafted: have a native speaker review `tools/strings_source.py`
   ([docs/TRANSLATION_REVIEW.md](docs/TRANSLATION_REVIEW.md)).
3. Places come from OpenStreetMap and are labelled "not verified by government". Replace or add
   an official OSDMA list in `app/src/main/assets/shelters.csv` format when available ([docs/DATA.md](docs/DATA.md)).
