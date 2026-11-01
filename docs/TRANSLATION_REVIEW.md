# Translation review — release blocker

All Odia (`app/src/main/res/values-or/strings.xml`) and Hindi (`values-hi/strings.xml`) UI text,
and the `or` / `hi` fields in `app/src/main/assets/checklist.json`, are **machine drafts**.
A wrong word in an emergency causes confusion (PRD 9.4), so a native speaker must review every
string before any release.

Checklist for the reviewer:
- [ ] Odia UI strings reviewed by: ________ date: ________
- [ ] Odia checklist reviewed by: ________ date: ________
- [ ] Hindi UI strings reviewed by: ________ date: ________
- [ ] Hindi checklist reviewed by: ________ date: ________
- [ ] Checklist content checked against OSDMA / NDMA do's and don'ts, ideally by the district
      emergency officer (FR-CHK-04). Then update `checklist_reviewed` in all three strings files
      to "Reviewed on: <date>".
- [ ] Audio clips (if generated) listened to and approved.

Pay special attention to: SOS choices, the outbox states (they must never sound like "delivered"
or "rescue is coming"), "This app does not send rescue — call 112", and the three hard warnings.
