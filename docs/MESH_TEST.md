# Nearby (mesh) test on real phones — 15 minutes

Emulators have no Bluetooth, so the phone-to-phone part can only be tested on real phones.
Do this once with 2–3 Android phones (ideally one cheap phone, Android 8–10) before handing out.

## Setup (all phones)
1. Install the same RakshakMesh APK (`app-arm64-v8a-release.apk`, or `app-universal-release.apk` for old phones).
2. Finish the setup wizard and tap **Allow and start** (accept every permission).
3. Turn **mobile data OFF** and **Wi-Fi network off** (Wi-Fi radio can stay on, just not connected),
   or put the phone in **airplane mode and then switch Bluetooth and Wi-Fi back on**.
4. Android 8–11 only: keep **Location** switched on (Android needs it for Bluetooth scanning).

## Tests
| # | Do this | Pass if |
| --- | --- | --- |
| M1 | Open **Nearby** on phone A and B, 2 m apart | Within ~30 s both show "Connected to 1 nearby phone" and the name |
| M2 | A: type a message, send | Appears on B with A's name; B gets a notification |
| M3 | A: **SOS to everyone nearby** | B: loud alarm notification, red SOS card with distance/direction; "Show on map" shows a red dot |
| M4 | A: hold mic 5 s, release | B: voice message plays |
| M5 | A: send a photo, then a 20 s video | B: photo shows, video plays |
| M6 | Lock B's screen, put it in a pocket; A sends SOS | B still alarms (background mesh) |
| M7 | Three phones in a line: A — B — C, with A and C too far apart to connect (other rooms / 50+ m) | A's SOS reaches C, card says "passed on by 1 phone" |
| M8 | Turn B off, A sends SOS, then turn B on near A | B receives the SOS after it reconnects (store-and-forward) |
| M9 | Give B internet; open B's SOS card → **Forward by SMS** | SMS app opens with the SOS text and map link |
| M10 | Settings → turn Nearby sharing off | Notification disappears; others stop seeing this phone |

Note the distance at which M1 works indoors and outdoors for each phone pair. Bluetooth/Wi-Fi
range is short (roughly 10–100 m, less through walls and water), so do not promise long distances.

## Log
| Date | Phones (model / Android) | Test | Result | Distance | Notes |
| --- | --- | --- | --- | --- | --- |
| | | | | | |
