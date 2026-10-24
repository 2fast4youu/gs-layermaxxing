# Island overview under seeded load (APK77, grid7)

This isolated repair starts at the delivered APK76 commit aaa1c52f3bd828d2efdbaef0ff0a36baff76a7c1. It does not include or publish the unfinished rule8 foundation work. No server or account/seed data change is needed.

## Demonstrated defect, not a handset stack diagnosis
The old overview placed all accepted friends on two fixed rings and synchronously planned all coasts/moorings/routes/boat lanes during composition. The legitimate seed adds20senders and120letters per existing owner. No phone ADB or crash report is available, so the user's exact crash exception is unknown.

The executable offline JVM production-geometry probe `python3 scripts/probes/island_map_scale.py` exposes the old scale failure:20friends produce7overlapping coast pairs and12usable routes with12585exact route/mooring clearance probes;50friends produce83overlaps and9routes/39178probes. A6friend page has0coast overlaps and6routes/3561probes. This is source geometry executed on a JVM, NOT a phone benchmark, Android app build, heap/OOM reproduction or proof of the reported exception.

## Repair
- At most6friends plus home enter overview geometry/rendering at once.
- Visible total/page counter, previous/next and lazy all-friends navigator. All friends remain reachable, including off-page peers; selection retains exact peer identity and complete per-peer mail.
- Full inbox/outbox are preserved; only map presentation is paginated. Boats still represent locked letters with excess counted by+N under existing routing rules.
- Existing server/account-scoped SaveableStateHolder retains page across tab exit/reentry. Invalid saved page indices clamp safely after friend list changes.
- Existing coast/hull/berth/grid7 semantics, bitmap resources and postal UI remain unchanged. Geometry remains synchronous but has bounded per-page work; do not describe it as asynchronous or claim a general ANR/phone-crash cure.

## Verification boundaries
Native Robolectric production IslandWorld tests cover0/1/12/20/50friends with120letters each, exact page coverage, boat/letter peer associations,+N accounting, unchanged polls/minute ticks, real paging buttons, tab exit/reentry and off-page lazy-list peer selection/Chat callback. The renderer uses synthetic test data, never real accounts.

Root fixed test-harness issues discovered during actual execution: captureToImage's native-JVM draw wait timed out; native activity View.draw capture is used instead. Paused-clock sheet animations and tab state require per-frame snapshot notifications/main-looper processing; bounded real swipe gestures replace unbounded performScrollToNode. No production assertions were removed and no heap budget was raised. The failed/interrupted early attempts remain in workspace/island-crash-20261023 logs. Final executed gate counts and CI status belong in delivery manifests; this document does not fabricate a passing result.

Known visual scope: this fixes map load/access scalability, not a wholesale map redesign. Existing overlaps between nameplates/island/boat/menu elements were seen in the synthetic first-page review and remain a separate visual issue. No success on Veit's phone is claimed before he installs and retests the GitHub-built signed update.
