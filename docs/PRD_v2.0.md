# RakshakMesh — Odisha Flood & Cyclone Response App PRD v2.0

Nov 1, 2026 · @Real Engineer Sameer

## 1. Audit of the v1.2 script — verdict

Verdict: v1.2 is not buildable as written. It added six features without a single receiver for the data, skipped the two things Odisha residents actually need most (official cyclone/flood alerts and an SMS channel that works on one bar of 2G), and its patch script corrupts the document numbering.

### 1.1 Bugs in the patch script itself

| # | Problem | Effect |
| --- | --- | --- |
| B1 | Every edit is `text.replace()` on exact v1.1 strings, with no check that the string was found | Any edit whose text differs by one space or newline is skipped silently; the output looks finished but is a mix of v1.1 and v1.2 |
| B2 | Acceptance-test renumbering loop runs 24→25, then 25→26 … 28→29 in sequence | The same row is bumped five times: the new AT-24 ("Directory has no verified records") ends as AT-29, while the old AT-16, AT-17, AT-18 now duplicate the new AT-16, AT-17, AT-18 |
| B3 | Four new sections inserted as 5.1–5.4, old 5.1 renamed 5.5, but old 5.2, 5.3 … are not shifted | Duplicate 5.2 and 5.3 headings |
| B4 | New 6.1 Bluetooth section added while the old "Limited peer-to-peer experiment" (now 6.4) and Phase 2 Nearby Connections item are kept | The same feature appears three times with three different scopes |
| B5 | `FamilyCheckIn.status` enum has Safe / NeedHelp / Evacuating, but FR-FAM-02 also defines "I CANNOT CHECK IN" | Data model and requirements disagree |
| B6 | Hard-coded `/mnt/data/...` paths, overwrites with no backup | Runs only in one sandbox |

### 1.2 Product problems (why it feels like a dummy app)

1. **Requests go nowhere.** Resource requests, check-ins and hazard reports are "saved on this phone" with no named receiver (no control room, no panchayat volunteer, no dashboard). A request nobody reads is a dummy feature.
2. **No warning data.** For a cyclone/flood app, the core value is the official IMD/OSDMA warning in Odia before landfall. v1.2 has no weather or alert source at all.
3. **Wrong offline channel.** "Share via Sharesheet" usually means WhatsApp, which needs data. In a cyclone, data dies first; voice and SMS often survive longer on 2G. SMS is missing entirely.
4. **Bluetooth used for the wrong job.** Two people who are within Bluetooth range with the app open can simply talk. The useful case is relaying stored SOS messages to a phone that *does* have signal (a volunteer, a shelter, a rescue boat). That is the design kept in v2.0.
5. **Too much for 48 hours.** Six bottom tabs, medical profile, family groups, resources, hazards, evacuation and Bluetooth in 48 hours means each feature is half-built. Material guidance is 3–5 bottom tabs.
6. **No Odia language, no low-literacy design, no low-end device target** stated anywhere in the patch.
7. **Shelter directory has no data source.** "Verified assistance points" are required but nobody is named to supply them.

### 1.3 What v2.0 keeps, cuts and adds

| Area | v1.2 | v2.0 decision |
| --- | --- | --- |
| 112 one-tap call | Kept | **Keep** — P0 |
| Offline SOS record + GPS | Kept | **Keep**, add SMS send with GPS + landmark — P0 |
| Official alerts | Missing | **Add** — cached IMD/NDMA alerts in Odia — P0 |
| Nearest cyclone shelter | Directory with no data | **Add** with OSDMA shelter list, offline — P0 |
| Family check-in | Sharesheet only | **Keep**, send by SMS to 3–5 saved numbers — P1 |
| Medical profile | Separate screen | **Merge** into SOS (blood group, allergy, needs) — P1 |
| Evacuation checklist | Kept | **Keep**, Odia, from OSDMA/NDMA material — P1 |
| Resource requests | Saved locally | **Keep only with a receiver** (volunteer SMS number or dashboard) — P2 |
| Hazard reports | Saved locally | **Merge** into SOS/requests — cut as separate feature |
| Bluetooth | 2-device foreground transfer | **Change** to store-and-forward to a connected volunteer phone — P2 |
| AI (Gemini/Sarvam) | Not covered | **Online only, never on the SOS path** — see Section 7 |

## 2. Odisha ground reality — what the app must survive

Odisha's worst events are cyclones in two windows, **May** and **October–November**, plus river floods in the **July–September** monsoon. In all three, power and mobile data fail first, and people are moved to shelters 1–3 days *before* impact. The app must be useful in the days before landfall (warnings, shelter, family plan) and keep working on a dead network after it.

### 2.1 Recent events that define the requirements

| Event | When | What failed / what mattered | Requirement it creates |
| --- | --- | --- | --- |
| Cyclone Montha | Oct 2025 | IMD put 8 southern districts (Koraput, Malkangiri, Rayagada, Nabarangpur, Kalahandi, Ganjam, Gajapati, Kandhamal) in red zone; state opened 2,000+ relief centres and closed schools in 9 districts; landslides cut Gajapati–Ganjam roads ([OdishaBytes](https://odishabytes.com/odisha-braces-for-cyclone-montha-red-orange-alerts-sounded-in-15-districts/), [Daily Pioneer](https://www.dailypioneer.com/2025/india/cyclone-montha-rain-lashes-8-odisha-districts.html), [AIR](https://www.newsonair.gov.in/odisha-battles-cyclone-montha-relief-operations-in-full-swing)) | District-level alerts; inland/hilly districts too, not only coast; road-blocked status |
| Cyclone Dana | Oct 2024 | Coastal north Odisha evacuation (Kendrapara, Bhadrak, Balasore) | Evacuation checklist, shelter finder before landfall |
| Cyclone Fani | May 2019 | Mobile service in Puri went down within hours; only \~14% of towers were unaffected; Puri had no power 11+ days; police fell back to VHF radio ([WION](https://www.wionews.com/india-news/8-without-without-power-water-protests-break-out-in-cyclone-hit-odisha-217646), [Sphere India JRNA](https://sphereindia.org.in/sites/default/files/2021-08/cyclone-e2809cfanie2809d-joint-rapid-needs-assessment-report-2019.pdf), [ETV Bharat](https://etvbharat.com/english/state/odisha/odisha-department-estimates-rs-524-cr-damage-by-fani-1-1/na20190514234227836)) | Everything critical must work with zero network for days; battery saver; SMS queue that retries when one bar returns |
| Monsoon river floods (Mahanadi, Brahmani, Baitarani, Subarnarekha) | Jul–Sep | CWC runs 19 flood-forecasting stations in Odisha and pushes CAP alerts through SACHET ([MoES](https://moes.gov.in/sites/default/files/1312English.pdf)) | River-level warning for the user's district |

### 2.2 What already exists (do not rebuild it)

- **SACHET / Cell Broadcast (NDMA + C-DOT):** official geo-targeted SMS and cell-broadcast alerts in regional languages arrive on every phone without any app. Our app must not compete with it; it should *store and explain* those alerts and turn them into actions.
- **SATARK (OSDMA + RIMES):** Odisha's official multi-hazard early-warning app ([Orissa Post](https://www.orissapost.com/get-alerts-from-satark-ahead-of-disasters/amp/)). It is a warning app, not an SOS/family/shelter app — that gap is ours.
- **Shelters:** OSDMA has built roughly 800–880 multipurpose cyclone and flood shelters, each run by a village Cyclone Shelter Management & Maintenance Committee ([OSDMA ICZM](https://www.osdma.org/integrated-coastal-zone-management-project/), [OSDMA handbook](https://www.osdma.org/wp-content/uploads/2019/06/Multipurpose-Cyclone-Shelter-Hand-Book.pdf)).
- **Helplines:** 112 (ERSS), 1070 (State EOC, Bhubaneswar), 1077 (district control room), 108 ambulance, 101 fire ([Kalahandi district](https://kalahandi.odisha.gov.in/en/directory/disaster-management), [Koraput district](https://koraput.odisha.gov.in/en/taxonomy/term/212)).

### 2.3 Who uses it, on what phone

| User | Situation | Needs |
| --- | --- | --- |
| Coastal/rural family (primary) | Low-cost Android (2–4 GB RAM, Android 10+), Odia speaker, may not read English | Odia + icons + voice, big buttons, offline, works on 2G |
| Migrant worker outside Odisha | Family is in the cyclone zone | Family check-in status, district alerts for home village |
| Volunteer / CSMMC member / ASHA | Has a phone with signal or at a shelter | Receives SOS/requests, relays them, marks "helped" |
| District control room (later) | Has internet and a laptop | Sees a list of SOS/requests by village |

**Design rule:** if a feature does not help one of these people in the 72 hours before or after landfall, it is not in v2.0.

## 3. Product scope

**One line:** RakshakMesh is an Odia-first Android app that, before a cyclone or flood, tells a family what the official warning means and where their shelter is; and after it, lets them send SOS and "I am safe" by SMS even on one bar of 2G — with the internet and AI only as optional extras.

### 3.1 Principles (every requirement must pass these)

1. **Offline-first, SMS-second, internet-last.** The SOS path uses no internet and no AI. Ever.
2. **Never show a false success.** "Saved on this phone", "SMS sent", "SMS delivered", "Relayed to volunteer phone" and "Received by control room" are five different states and the UI never merges them.
3. **Official data only for warnings and shelters.** IMD/NDMA/OSDMA data with source and time stamp. No AI-generated warnings, no AI-generated shelter locations.
4. **Odia first, then Hindi, then English.** Every critical screen has an icon and a voice-readout button.
5. **Every request has a receiver.** A feature that creates a message must name who receives it (family numbers, volunteer number, 112/1070/1077, or the dashboard). If none, the feature is cut.
6. **Battery is life.** After landfall there may be no power for 1–2 weeks. No background GPS, no background Bluetooth scanning, dark UI option.
7. **Complement SACHET and SATARK, do not copy them.**

### 3.2 In and out of scope

| In scope (v2.0) | Out of scope (and why) |
| --- | --- |
| SOS: call 112 / 1070 / 1077, SMS with GPS + landmark to saved contacts | Automatic rescue dispatch (only government can promise this) |
| Official alert inbox for the user's district (IMD + SACHET), cached offline | Our own weather forecast or cyclone-track prediction |
| Nearest cyclone/flood shelter, offline, with distance and direction | Turn-by-turn routing (roads change; we cannot verify which are open) |
| Family check-in by SMS (Safe / Need help / At shelter) | Live family location tracking |
| Odia evacuation checklist + do's and don'ts, offline, with audio | Medical diagnosis or dosage advice |
| Store-and-forward relay of SOS to a phone that has signal (Nearby Connections) | Multi-hop mesh across a district, range claims |
| Volunteer mode + simple web dashboard (P2) | Login, Aadhaar, any ID documents |
| Online-only AI helpers: Odia voice-to-text, alert translation/simplification (P2) | AI on the SOS path; AI chatbot giving safety advice |

### 3.3 Success metrics for the pilot (one coastal block, one season)

| Metric | Target |
| --- | --- |
| Time from app open to SOS SMS composed | ≤ 10 seconds, ≤ 3 taps |
| SOS / check-in works with airplane mode on (saved + queued) | 100% of test runs |
| Alert inbox shows latest official alert for the district when online, and last cached copy when offline | 100% of test runs |
| Nearest shelter shown offline for any point in the pilot block | 100% |
| Users who complete Odia onboarding (contacts + village) | ≥ 70% of installs |
| False "delivered" states found in testing | 0 |

## 4. Feature requirements

P0 ships in the first build and must work fully offline. P1 ships before the pilot season. P2 is built only after P0/P1 pass device tests. Bottom navigation has **4 tabs**: Home (SOS), Alerts, Shelter, Family. Checklist and settings open from Home.

### 4.1 P0 — SOS (call + SMS)

- **FR-SOS-01** Home shows one large red SOS button and three call buttons: 112, 1070 (State EOC), 1077 (district control room). Calls use `ACTION_DIAL`, so no call permission is needed and the user presses call.
- **FR-SOS-02** SOS opens a 3-choice screen with icons: *Trapped by water*, *Injured / medical*, *Need evacuation*. One tap selects; no typing required.
- **FR-SOS-03** The app reads one GPS fix with a 20-second timeout. GPS works without network. If no fix, it uses the last known fix and labels its age ("location from 2 h ago"), or the village/landmark saved at onboarding.
- **FR-SOS-04** SMS body is short ASCII so it fits one 160-character SMS (Odia script uses Unicode, which cuts an SMS to 70 characters and costs 3 SMS): `SOS RM1 FLOOD-TRAPPED 4 ppl 1 elderly | 20.2961,85.8245 ±15m 14:32 | Ward 5 near Shiv temple, Astaranga | B+ | maps.google.com/?q=20.2961,85.8245`
- **FR-SOS-05** Recipients: up to 5 saved family/neighbour numbers plus an optional volunteer number for the village. 112/1070 are *called*, not texted, because those lines do not reliably take SMS.
- **FR-SOS-06** Default send method: `ACTION_SENDTO` (opens the phone's SMS app pre-filled; user taps send). Optional direct send with `SEND_SMS` only if Google Play approves the "physical safety/emergency alerts" exception (Section 6.2).
- **FR-SOS-07** Every SOS is saved locally first, with states: `Saved on phone` → `SMS handed to SMS app` → (direct-send build only) `SMS sent` → `SMS delivered`. If there is no signal, the SOS sits in an **outbox** and the app shows a banner: "Not sent yet — will try when signal returns."
- **FR-SOS-08** Optional fields stored once at onboarding and added only if the user ticks them: blood group, "needs wheelchair/cannot walk", "pregnant", "infant". No other medical data.

### 4.2 P0 — Official alerts inbox

- **FR-ALR-01** User picks district (and block) at onboarding; GPS can suggest it.
- **FR-ALR-02** When online, the app fetches district warnings (IMD) and CAP alerts for Odisha (SACHET RSS) every 3 hours, and every 30 minutes if the district has an orange/red warning (Section 5).
- **FR-ALR-03** Each alert shows: colour level, issuing agency, issue time, valid-until time, and the original text. A short Odia summary may be shown *below* the original, labelled "simplified" (P2, Section 7).
- **FR-ALR-04** Offline: show the last cached alert with "Last updated 6 h ago — may be old". Never hide that the data is stale.
- **FR-ALR-05** For red alerts, Home shows an action card: "Cyclone warning for Ganjam. Nearest shelter: 1.2 km NE. Open checklist."

### 4.3 P0 — Nearest shelter (offline)

- **FR-SHL-01** App ships with an offline list of OSDMA multipurpose cyclone/flood shelters for the pilot districts: name, village, lat/long, capacity if known, source, and "verified on" date.
- **FR-SHL-02** Shows the 3 nearest shelters by straight-line distance and compass direction from the GPS fix. No road routing is promised.
- **FR-SHL-03** Offline map: a pre-packaged vector map of the pilot districts (Section 5.3), with the user, shelters and the 112/1070 buttons visible.
- **FR-SHL-04** If the dataset has no shelter within 10 km, show "No verified shelter in our data — call 1077". Never invent or guess entries.

### 4.4 P1 — Family check-in

- **FR-FAM-01** Three buttons: **I am safe**, **I need help** (goes to SOS), **I am at shelter** (attaches nearest shelter name).
- **FR-FAM-02** Sends one SMS to the saved family list via the same outbox, e.g. `RM1 SAFE Rina at Astaranga MCS 15:10`.
- **FR-FAM-03** Messages are human-readable, so the receiver does not need the app. The app does **not** read incoming SMS (Play restricts `READ_SMS`).
- **FR-FAM-04** No tracking, no silent location, no "missing" label for people who have not checked in.

### 4.5 P1 — Evacuation checklist and safety guide

- **FR-CHK-01** Offline Odia/Hindi/English checklist based on OSDMA and NDMA cyclone/flood do's and don'ts: documents in a plastic bag, medicines, drinking water, dry food, torch, charged power bank, cattle untied, boats secured.
- **FR-CHK-02** Each item has an icon and a pre-recorded Odia audio clip (generated at build time, Section 7).
- **FR-CHK-03** Hard warnings shown at the top: do not enter floodwater, do not touch fallen wires, do not leave shelter until the official all-clear.
- **FR-CHK-04** Content is reviewed by an Odia speaker and, ideally, the district emergency officer before release; the screen shows "Reviewed on \<date>".

### 4.6 P1 — Battery and low-network mode

- **FR-BAT-01** "Cyclone mode" switch: dark theme, no background work, GPS only on SOS, alerts refresh off.
- **FR-BAT-02** Shows battery % and a tip at 30%: "Keep 20% for SOS. Turn off mobile data, keep calls/SMS."

### 4.7 P2 — Store-and-forward relay (Bluetooth / Wi-Fi Direct)

- **FR-RLY-01** A user with no signal can hand queued SOS/check-in messages to a nearby phone running the app in **Volunteer mode**, using Google Nearby Connections (Section 6.3).
- **FR-RLY-02** Volunteer phone stores them and sends them by SMS (or uploads to the dashboard) when it has signal, then shows "Relayed" to the volunteer. The sender's phone shows only "Handed to volunteer phone" — never "Delivered".
- **FR-RLY-03** Both phones must have the app open; both users confirm the 4-digit code. No background scanning.
- **FR-RLY-04** Packets: ID, schema version, created time, expiry (24 h), SHA-256 checksum; duplicates and expired packets rejected. Encryption is Nearby Connections' built-in channel; no custom crypto.

### 4.8 P2 — Resource requests + volunteer dashboard

- **FR-RES-01** Request types: drinking water, food, medicine, baby supplies, elderly/disabled help, animal help. Separate from SOS and never ranked above it.
- **FR-RES-02** Sent by SMS to the village volunteer number, or uploaded to the dashboard when online.
- **FR-RES-03** Dashboard (web, for NGO/volunteers/control room): list by village with status *New / Seen / Helped*. Only built if a named organisation agrees to watch it during the pilot.

## 5. Data sources — where each piece comes from, and what it costs

All data needed for v2.0 is available free. The catch: IMD's API needs IP whitelisting, so the phone cannot call IMD directly — a small free server ("alert relay") fetches IMD + SACHET and the app reads from it. The phone stores the last copy so it works offline.

### 5.1 Weather and disaster alerts

| Source | What it gives | Access | Cost | Use in app |
| --- | --- | --- | --- | --- |
| **IMD API** ([portal](https://mausam.imd.gov.in/responsive/apis.php), [api.imd.gov.in](https://api.imd.gov.in/)) | District-wise warnings, district nowcast, district rainfall, city 7-day forecast, port warnings | Request IP whitelisting / key from IMD (nodal contact on the portal); attribution required; IMD asks for client-side caching | Free | **Primary** for district warning colour and text. Called only from the alert relay server |
| **NDMA SACHET CAP RSS** ([sachet.ndma.gov.in](https://sachet.ndma.gov.in/), [explainer](https://thejeshgn.com/2025/05/28/common-alerting-protocol-cap-in-the-indian-context/)) | Official CAP 1.2 alerts from IMD, CWC, OSDMA etc., national and state-level RSS; each item links a CAP XML with area polygons | Public RSS, no key | Free | **Primary** for multi-hazard alerts (cyclone, flood, lightning, heat). Relay filters Odisha alerts by area polygon/district |
| **CWC flood forecasts** ([ffs.india-water.gov.in](https://ffs.india-water.gov.in/)) | River level forecasts at 19 Odisha stations; also published as CAP via SACHET | Public website + SACHET | Free | Use via SACHET; link out to the station page |
| **Google Flood Forecasting API** ([docs](https://developers.google.com/flood-forecasting)) | Riverine flood forecasts (same as Flood Hub), CC BY 4.0 | Waitlist, then API key | Free | P2 — extra river-flood signal for the user's location, labelled "Google Flood Hub", never shown as an official warning |
| **Open-Meteo** ([terms](https://open-meteo.com/en/terms)) | Hourly rain/wind forecast for any lat/long, no key | Open; under 10,000 calls/day; non-commercial only; must show "Weather data by Open-Meteo.com" | Free (non-commercial) | P1 — "Next 24 h rain and wind" card only. Not a warning. If the app is ever commercial, buy a plan or drop it |
| **SATARK** (OSDMA) | Odisha multi-hazard advisories | No public API found | — | Deep-link button "Open SATARK" if installed |

**Rule:** the warning colour on screen comes only from IMD or SACHET. Open-Meteo and Flood Hub are "extra information" and are visually separate.

### 5.2 Alert relay server (free hosting)

- A scheduled job (every 15–30 min) fetches IMD district warnings + SACHET RSS, keeps only Odisha, and writes one small JSON per district (≈2–5 KB).
- Host on any free tier: Cloudflare Workers + KV, or a GitHub Action writing static JSON to GitHub Pages. The phone downloads only its district file, so even 2G works.
- IMD whitelisting needs a fixed outbound IP, which serverless platforms usually do not give. Plan: run the IMD fetch on a ₹0–₹500/month small VM (or a college/NGO server) with a static IP; SACHET can be fetched from anywhere.
- Response includes `fetchedAt`, `source`, `validUntil`, so the app can say how old it is.

### 5.3 Maps (offline)

- **Do not** download tiles from `tile.openstreetmap.org` for offline use — the OSMF tile policy prohibits bulk/offline downloading ([OSMF policy](https://operations.osmfoundation.org/policies/tiles/)).
- **Do:** take OpenStreetMap *data* (ODbL, free) for the pilot districts from a Geofabrik India/Odisha extract, build a vector **PMTiles** file (Protomaps/Planetiler tooling), and either bundle it or let the user download it once from our own storage (Cloudflare R2/GitHub Release). Render with **MapLibre Native Android**.
- Expected size: one district at zoom 6–15 is typically tens of MB; measure for the pilot block and keep the download under \~50 MB.
- Show "© OpenStreetMap contributors".

### 5.4 Shelters

- There is **no public API** for OSDMA shelter locations that we found. Sources, in order of preference:
  1. The shelter list from OSDMA or the district emergency officer (ask formally; this also gives us a government contact for the pilot).
  2. District disaster-management plans published on `<district>.odisha.gov.in` (often list shelters by village).
  3. OpenStreetMap features tagged as shelters, then field-checked.
- Each record carries `source` and `verifiedOn`. Unverified records are hidden, not shown greyed out.

### 5.5 GPS / location

- GPS needs no network; it uses satellites. Use `FusedLocationProvider` with high accuracy for one fix on SOS, fallback to `LocationManager` GPS provider on phones without Play services.
- Cold start without A-GPS (no data) can take 30 s to a few minutes; that is why FR-SOS-03 uses a timeout, the last known fix and the saved landmark.
- Share lat/long with 4 decimals (\~11 m) plus accuracy. A Google Maps link is added because volunteers can open it, but the raw numbers come first so they work without internet.

## 6. Device technology — what works when the network is gone

SMS and voice survive longest after a cyclone, GPS always works, and Bluetooth/Wi-Fi Direct only reach phones within roughly tens of metres. So the order of channels is: call → SMS → nearby relay → internet.

### 6.1 Channel comparison

| Channel | Needs | Works after cyclone? | Range | Use in v2.0 |
| --- | --- | --- | --- | --- |
| Voice call 112/1070/1077 | Any network; 112 can go through another operator's tower | Often first to return when towers get diesel | Tower | P0 |
| SMS | 2G signal, even weak; store-and-forward in network | Usually works on one bar; queued in network if recipient is off | Tower | P0 — main data channel |
| Mobile data (WhatsApp, upload) | 3G/4G | Last to return | Tower | Optional only |
| GPS | Clear sky | Yes, no network needed | Satellites | P0 |
| Nearby Connections (Bluetooth + BLE + Wi-Fi) | Both apps open, Play services | Yes, no network needed | Short, walls/water reduce it — measure, do not promise | P2 relay |
| Cell Broadcast / SACHET SMS | Nothing from us | Yes, when tower is up | Tower | Not ours — we only explain |

### 6.2 SMS on Android — the policy trap

- Google Play restricts `SEND_SMS`. Apps that are not the default SMS app must remove it, unless Play approves an exception. "Physical safety/emergency alerts" that send SMS is a listed exception, subject to review ([Play policy](https://support.google.com/googleplay/android-developer/answer/9047303)). Humanitarian apps have been refused before (ODK Collect was not granted an exception — [ODK forum](https://forum.opendatakit.org/t/17973)).
- **Plan:** build v2.0 with `ACTION_SENDTO` (no permission, user taps send). Apply for the exception in parallel. Keep a separate *direct-send* build for the pilot, side-loaded through the NGO, where automatic retry in the outbox is allowed.
- Group SMS: open the SMS app with multiple recipients (`smsto:num1;num2`); behaviour differs by phone, so test on the 3 most common phones in the pilot area.
- Delivery reports (`SMS delivered`) are only available in the direct-send build.

### 6.3 Bluetooth / Wi-Fi — Nearby Connections

- Google Nearby Connections discovers and connects nearby devices fully offline, using Bluetooth, BLE and Wi-Fi underneath; connections are encrypted, and byte payloads are limited to 32 KB ([Google docs](https://developers.google.com/nearby/connections/)). Our packet is under 1 KB.
- Strategy: `P2P_STAR` — volunteer phone advertises, residents discover. Both sides accept the connection after comparing a code.
- Needs Google Play services (missing on some phones). Fallback: show "Relay not supported on this phone".
- Android 12+ needs `BLUETOOTH_SCAN`, `BLUETOOTH_ADVERTISE`, `BLUETOOTH_CONNECT`; older versions need location permission. Ask only when the user opens Relay.
- Test record per run: phone models, Android version, distance, indoors/outdoors, time to connect, success. Publish ranges only from many runs.

### 6.4 Stack

| Layer | Choice | Why |
| --- | --- | --- |
| Language / UI | Kotlin + Jetpack Compose | Standard, good TalkBack support |
| Min SDK | Android 8 (API 26) | Covers older low-cost phones |
| Local DB | Room (SQLite) | Outbox, alerts cache, shelters |
| Background | WorkManager (only for alert refresh when online and for outbox retry) | Battery-safe, survives restarts |
| Maps | MapLibre Native + bundled PMTiles | Offline, free |
| Location | Fused Location Provider + LocationManager fallback | Works with and without Play services |
| P2P | Nearby Connections | Tested, encrypted |
| Server | Cloudflare Worker / small VM + static JSON | Free or near-free |
| APK size | Target < 30 MB without map; map as one-time download | 2G friendly |

## 7. AI usage — Gemini and Sarvam

Decision: AI is **never** on the SOS path and never needed on the phone during the disaster. The most valuable use of both APIs is at **build time** and on the **server**, where one call serves every user and nothing breaks when the network dies.

### 7.1 Where AI helps (and where it runs)

| # | Use | Model | Runs | Priority | Why it is worth it |
| --- | --- | --- | --- | --- | --- |
| A1 | Generate Odia (and Hindi) audio for every checklist item, button and safety instruction, bundled in the APK | Sarvam **Bulbul v3** TTS | Build time | P1 | Low-literacy users can *hear* instructions offline. Whole script ≈ 5–10K characters ≈ ₹15–30 one-time |
| A2 | First-draft translation of all UI strings and checklist into Odia/Hindi, then human review | Sarvam Translate (or Gemini) | Build time | P0 | Fast and cheap; human review is mandatory before release |
| A3 | Turn district disaster-plan PDFs (often scanned, Odia/English) into a draft shelter table | Sarvam Document Digitization (₹0.5/page) + Gemini for structuring | Build time | P0 | Saves days of manual typing; every row still verified before `verifiedOn` is set |
| A4 | Plain-Odia summary of each official alert ("Red warning: very heavy rain and 90–100 km/h wind tomorrow. Go to shelter if told by officials.") | Fixed Odia templates per alert type + Sarvam Translate for free-text parts | Alert relay server | P2 | One call per alert per district, cached for all users; original text always shown above it |
| A5 | Odia voice note → text for the SOS landmark field ("near the big banyan tree behind school") | Sarvam **Saaras v3** STT | Phone, only when online | P2 | Helps people who cannot type Odia. If offline, the voice note is skipped — SOS still sends |
| A6 | Volunteer dashboard: group 200 requests into "Ward 5: 12 need water, 3 elderly need lift" | Gemini Flash | Dashboard server | P2 | Saves coordinator time; raw list always visible |

### 7.2 Where AI must not be used

- Deciding if a situation is an emergency, ranking SOS urgency, or telling someone to stay or leave.
- Generating warnings, shelter locations, or routes.
- A free chatbot giving safety or medical advice during a disaster (hallucination risk; needs data the user may not have).
- Anything that blocks sending an SOS while waiting for a model.

### 7.3 Cost and access facts (checked Nov 2026)

| Item | Fact |
| --- | --- |
| Sarvam prices | Speech-to-text ₹30/hour; Bulbul v3 TTS ₹30 per 10K characters; Translate ₹20 per 10K characters; Sarvam-30B chat ₹2.5 in / ₹10 out per 1M tokens ([Sarvam pricing](https://docs.sarvam.ai/api-reference-docs/pricing)) |
| Sarvam models | Current STT default is `saaras:v3`; chat models are `sarvam-30b` and `sarvam-105b`; `sarvam-m` is retired ([Sarvam changelog](https://docs.sarvam.ai/api/changelog)) |
| Sarvam Edge (on-device) | \~294 MB ASR and \~60 MB TTS models run offline ([Sarvam blog](https://www.sarvam.ai/blogs/sarvam-edge/)), but they are deployed through device-maker partnerships and benchmarked on flagship chips ([analysis](https://ucstrategies.com/news/indias-best-offline-ai-only-works-on-phones-80-of-indians-cant-afford/)). **Not for v2.0** on ₹8–15K phones; revisit later |
| Gemini Pro on the API | Third-party trackers report that since April 2026 the free API tier covers only Flash/Flash-Lite and Pro is paid-only ([agentdeals](https://agentdeals.dev/gemini-api-pricing-changes)). Your Pro key will work if billing is on; use **Flash** for anything that runs often |
| Gemini free tier data | Free-tier prompts may be used to improve Google products ([Dupple](https://dupple.com/learn/is-gemini-free)). Never send real SOS, phone numbers or locations on a free key |

### 7.4 Rules for keys and speed

- API keys live only on the server, never in the APK (an APK can be decompiled in minutes).
- Cache every AI output (alert summaries per alert ID, audio files per string). The phone never waits on a model.
- Every AI-produced text is marked "simplified by AI — read the official message above".
- Deepgram is parked; Sarvam covers Odia speech better for this use.

## 8. Architecture and data model

The phone holds everything it needs for the emergency; the relay server only refreshes alerts while there is internet, and AI only shapes content at build time or on the server.

&#91;embedded content: system architecture · phone, relay server, AI, receivers\]

The core features (SOS, family SMS, shelter, checklist) touch nothing above the phone at run time.

### 8.1 Local data model (Room)

| Table | Key fields | Notes |
| --- | --- | --- |
| `Profile` | name, village, block, district, landmark, bloodGroup?, mobilityNeed?, pregnant?, infant?, language | All optional except district; never uploaded |
| `Contact` | id, label, phone, role (family / volunteer) | Max 5 family + 1 volunteer |
| `OutboxMessage` | id (UUID), type (SOS / SAFE / AT\_SHELTER / REQUEST), body, recipients, lat?, lon?, accuracyM?, fixAgeSec?, createdAt, state (SAVED / HANDED\_TO\_SMS\_APP / SENT / DELIVERED / RELAYED / FAILED), attempts, lastError? | Never deleted automatically; user can clear |
| `AlertCache` | alertId, source (IMD / SACHET), district, level (green/yellow/orange/red), headline, originalText, simplifiedOdia?, issuedAt, validUntil, fetchedAt | Old alerts kept 7 days |
| `Shelter` | id, name, nameOdia, village, block, district, lat, lon, capacity?, type (cyclone/flood), source, verifiedOn | Shipped with APK; updatable when online |
| `ChecklistItem` | id, phase (before/during/after), textOdia, textHi, textEn, audioFile, done | Content reviewed before release |
| `RelayPacket` (P2) | packetId, schemaVersion, payload, createdAt, expiresAt, sha256, receivedFrom?, forwardedAt? | Duplicate IDs rejected |

### 8.2 Alert relay JSON (per district)

```json
{"district":"Ganjam","fetchedAt":"2026-10-27T05:30:00+05:30","alerts":[{"id":"imd-...","source":"IMD","level":"red","headline":"Extremely heavy rainfall, gusty winds 90-100 km/h","issuedAt":"...","validUntil":"...","originalText":"...","simplifiedOdia":null}]}
```

## 9. Build plan, tests, risks

The 48-hour build delivers only P0 (SOS, alerts cache, shelters) and must pass the airplane-mode tests; P1 follows in 2–3 weeks and P2 only after a real pilot partner is confirmed.

### 9.1 Before coding (start now, runs in parallel)

- [ ] Pick one pilot block (coastal, cyclone-prone) and one partner: NGO, college NSS unit, or CSMMC.
- [ ] Request the shelter list for that district from OSDMA / district emergency officer.
- [ ] Apply to IMD for API access and IP whitelisting.
- [ ] Submit Google Play permissions declaration for `SEND_SMS` (emergency alerts exception).
- [ ] Find one Odia speaker to review all strings and audio.
- [ ] Collect the 3 most common phone models in the village for testing.

### 9.2 48-hour build (P0)

1. **Hours 0–4:** Kotlin/Compose project, Room schema (Section 8.1), Odia/Hindi/English string files, 4-tab navigation.
2. **Hours 4–14:** SOS screen, 112/1070/1077 dial buttons, GPS one-shot with timeout, SMS body builder, outbox with states, `ACTION_SENDTO`.
3. **Hours 14–20:** Onboarding: language, district/block/village, landmark, contacts, optional SOS details.
4. **Hours 20–28:** Alert relay (SACHET RSS first; IMD when whitelisted) → district JSON; app fetch, cache, stale label, red-alert action card.
5. **Hours 28–36:** Shelter table import (CSV → Room), nearest-3 list with distance/direction, MapLibre with bundled PMTiles for the pilot block.
6. **Hours 36–44:** Device tests below on at least 2 real phones, airplane mode, no SIM, low battery.
7. **Hours 44–48:** Fix failures, record test results, demo script.

**Then P1 (2–3 weeks):** family check-in, checklist with Odia audio (Sarvam Bulbul at build time), cyclone/battery mode, Open-Meteo 24 h card, Play release. **P2 (after partner confirms):** Nearby relay, volunteer mode, resource requests, dashboard, AI alert summaries, Odia voice note.

### 9.3 Acceptance tests (P0 + P1)

| ID | Test | Pass condition |
| --- | --- | --- |
| AT-01 | Open app → SOS in airplane mode | SOS saved, outbox shows "Not sent yet"; no crash |
| AT-02 | Turn airplane mode off with 2G only | Outbox prompts / retries; state moves past "Saved" |
| AT-03 | Tap 112 / 1070 / 1077 | Dialer opens with the right number |
| AT-04 | SOS with no GPS fix in 20 s | Uses last fix with age, or saved landmark; never blank |
| AT-05 | SOS SMS length | ≤ 160 characters in ASCII; readable without the app |
| AT-06 | SMS to 5 recipients | All 5 numbers pre-filled on the 3 test phones |
| AT-07 | Online alert fetch for district | Latest IMD/SACHET alert shown with source and time |
| AT-08 | Offline after fetch | Last alert shown with "Last updated … may be old" |
| AT-09 | Red alert in district | Home action card shows shelter + checklist link |
| AT-10 | Nearest shelter offline | 3 nearest verified shelters with km and direction |
| AT-11 | Point with no shelter within 10 km | "No verified shelter — call 1077"; no fake entry |
| AT-12 | Offline map | Map renders for whole pilot block with no network |
| AT-13 | Family "I am safe" offline | Saved to outbox; sent when signal returns |
| AT-14 | Odia UI + audio | Every P0/P1 screen in Odia; checklist audio plays offline |
| AT-15 | TalkBack + largest font | SOS reachable and readable |
| AT-16 | App killed and restarted | Outbox and alerts still there |
| AT-17 | Battery at 15%, cyclone mode | No background GPS/Bluetooth; SOS still works |
| AT-18 | APK inspected | No API keys inside |
| AT-19 | Any screen | No state claims delivery or rescue that did not happen |
| AT-20 | Low-end phone (2 GB RAM, Android 8–10) | Cold start < 4 s; no crash |

### 9.4 Risks

| Risk | Effect | Mitigation |
| --- | --- | --- |
| Play refuses `SEND_SMS` exception | No automatic retry/delivery reports | `ACTION_SENDTO` is the default; side-loaded pilot build for automatic sending |
| IMD whitelisting slow or refused | No IMD district warnings | SACHET RSS already carries IMD CAP alerts; ship with SACHET first |
| No official shelter list | Shelter feature empty | Digitise district plans (A3) + field verification with partner; otherwise show only 1077 |
| Towers down for days | No SMS at all | Saved landmark, outbox, Nearby relay (P2); be honest in UI |
| Wrong Odia translation | Confusion in an emergency | Human review is a release blocker |
| Phones without Play services | No relay, weaker GPS | LocationManager fallback; relay hidden on those phones |
| People expect the app to bring rescue | Delay in calling 112 | Call buttons on top; text "This app does not send rescue — call 112" |

### 9.5 Definition of done (v2.0 pilot release)

- AT-01 to AT-20 run on at least 3 phone models, results logged with model, Android version, date.
- Shelter data has `source` + `verifiedOn` for every record shown.
- Odia content signed off by a reviewer.
- Partner has agreed who receives SOS/requests in the pilot village.
- No AI call anywhere on the SOS, check-in or shelter path.

### 9.6 Sources

IMD APIs: [mausam.imd.gov.in/responsive/apis.php](https://mausam.imd.gov.in/responsive/apis.php) · SACHET: [sachet.ndma.gov.in](https://sachet.ndma.gov.in/) · CAP in India: [thejeshgn.com](https://thejeshgn.com/2025/05/28/common-alerting-protocol-cap-in-the-indian-context/) · CWC in Odisha: [MoES](https://moes.gov.in/sites/default/files/1312English.pdf) · Google Flood Forecasting API: [developers.google.com/flood-forecasting](https://developers.google.com/flood-forecasting) · Open-Meteo terms: [open-meteo.com/en/terms](https://open-meteo.com/en/terms) · OSM tile policy: [operations.osmfoundation.org](https://operations.osmfoundation.org/policies/tiles/) · Nearby Connections: [developers.google.com/nearby/connections](https://developers.google.com/nearby/connections/) · Play SMS policy: [support.google.com](https://support.google.com/googleplay/android-developer/answer/9047303) · Sarvam pricing: [docs.sarvam.ai](https://docs.sarvam.ai/api-reference-docs/pricing) · Sarvam Edge: [sarvam.ai/blogs/sarvam-edge](https://www.sarvam.ai/blogs/sarvam-edge/) · Gemini API tiers: [agentdeals.dev](https://agentdeals.dev/gemini-api-pricing-changes) · Fani telecom impact: [WION](https://www.wionews.com/india-news/8-without-without-power-water-protests-break-out-in-cyclone-hit-odisha-217646) · Montha: [OdishaBytes](https://odishabytes.com/odisha-braces-for-cyclone-montha-red-orange-alerts-sounded-in-15-districts/) · OSDMA shelters: [osdma.org](https://www.osdma.org/integrated-coastal-zone-management-project/) · SATARK: [Orissa Post](https://www.orissapost.com/get-alerts-from-satark-ahead-of-disasters/amp/) · Helplines: [Kalahandi district](https://kalahandi.odisha.gov.in/en/directory/disaster-management)
