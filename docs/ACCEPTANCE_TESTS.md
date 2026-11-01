# Acceptance tests (PRD 9.3)

Run on at least 3 phone models, including one low-end (2 GB RAM, Android 8–10). Log every run.

Automated in CI: AT-05 (SMS length, `core` unit tests), AT-11 logic (no shelter within 10 km),
AT-18 (APK scanned for keys), alert parsing, staleness labels, outbox state rules.

| ID | Test | Pass condition |
| --- | --- | --- |
| AT-01 | Open app → SOS in airplane mode | SOS saved, outbox shows "Not sent yet"; no crash |
| AT-02 | Turn airplane mode off with 2G only, reopen app | App offers to send unsent messages; state moves past "Saved" |
| AT-03 | Tap 112 / 1070 / 1077 | Dialer opens with the right number |
| AT-04 | SOS with no GPS fix in 20 s | Uses last fix with age, or saved landmark; never blank |
| AT-05 | SOS SMS length | ≤ 160 ASCII characters; readable without the app |
| AT-06 | SMS to 5 recipients | All 5 numbers pre-filled on the 3 test phones |
| AT-07 | Online alert fetch for district | Latest alert shown with source and time |
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
| AT-20 | Low-end phone | Cold start < 4 s; no crash |

## Run log

| Date | Tester | Phone model | Android | Test IDs | Result | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| | | | | | | |
