# RakshakMesh

Odia-first Android app for Odisha cyclones and floods. Before landfall it shows what the official
warning means and where the nearest shelter is. After landfall it lets a family send SOS and
"I am safe" by **SMS**, even on one bar of 2G. Internet and AI are optional extras, never on the
SOS path. Product spec: [docs/PRD_v2.0.md](docs/PRD_v2.0.md).

## What is in this build (PRD P0 + P1)

| Feature | PRD | Works offline |
| --- | --- | --- |
| SOS: 112 / 1070 / 1077 dial buttons, 3-choice SOS, one-shot GPS (20 s timeout → last fix with age → saved landmark), ≤160-char ASCII SMS to up to 5 family + 1 volunteer, saved to an outbox first | FR-SOS-01..08 | Yes |
| Outbox with separate states: saved / handed to SMS app / sent / delivered / failed. Prompts to send when the app reopens out of airplane mode | FR-SOS-07 | Yes |
| Official alerts inbox for the user's district (from the relay below), cached, with "last updated … may be old"; red/orange action card on Home | FR-ALR-01..05 | Cached copy |
| Nearest 3 verified shelters with km + compass direction; "No verified shelter — call 1077" otherwise; MapLibre offline map from a PMTiles file | FR-SHL-01..04 | Yes |
| Family check-in by SMS: I am safe / I need help / I am at a shelter | FR-FAM-01..04 | Yes |
| Evacuation checklist (Odia / Hindi / English), hard warnings on top, audio when clips are bundled | FR-CHK-01..04 | Yes |
| Cyclone mode (dark UI, no background work) and battery tip at 30% | FR-BAT-01..02 | Yes |
| Alert relay: GitHub Action → SACHET CAP → one JSON per district on GitHub Pages | PRD 5.2 | — |

Not in this build (P2): Nearby Connections relay, volunteer mode, resource requests, dashboard, AI
alert summaries, Odia voice notes.

**Permissions:** location (asked when needed), internet, network state. No `SEND_SMS`,
`READ_SMS`, `CALL_PHONE`, Bluetooth or background location. SMS is sent through the phone's own
SMS app (`ACTION_SENDTO`); calls through the dialer (`ACTION_DIAL`).

## Layout

```
core/    Plain Kotlin logic, no Android: SMS builder, distance/direction, nearest shelters,
         shelter CSV, alert feed parsing, staleness, outbox states. Unit-tested.
app/     Android app (Kotlin, Jetpack Compose, Room, WorkManager, MapLibre). minSdk 26.
relay/   SACHET → per-district JSON script (Python stdlib) + tests.
tools/   generate_audio.py: build-time Odia audio with Sarvam Bulbul.
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

## Before a pilot

1. **Turn on the alert relay:** repo Settings → Pages → Source: *GitHub Actions*, then run the
   *Alert relay* workflow once. The app's default relay address is
   `https://sameerrealengineer.github.io/Rakshakmesh` (changeable in Settings).
2. **Add verified shelters** to `app/src/main/assets/shelters.csv` — it ships empty. See
   [docs/DATA.md](docs/DATA.md).
3. **Build the offline map** (PMTiles) for the pilot block and host it, or side-load it. See
   [docs/DATA.md](docs/DATA.md).
4. **Review translations:** Odia and Hindi text is a machine draft. See
   [docs/TRANSLATION_REVIEW.md](docs/TRANSLATION_REVIEW.md). This blocks release.
5. **Generate checklist audio** with `tools/generate_audio.py` (needs a Sarvam key, kept out of
   the APK).
6. **Run the acceptance tests** on 3 real phones: [docs/ACCEPTANCE_TESTS.md](docs/ACCEPTANCE_TESTS.md).
