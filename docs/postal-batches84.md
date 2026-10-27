# APK85: whole arrived-mail spreads and reading text

Source candidate based on verified APK83 commit `578bdbe864b1b63db2a5ff65592e31b414f4cebf`. Version code **85**, version name **7.9.14-post-batches1**. Version 84 is already used by a separate optimisation preview; 85 avoids an update-version collision without changing that branch. Validation and delivery remain Root-owned; this document does not report a test pass or an APK build.

PostalBatches sorts direct incoming, server-arrived mail by createdAt descending and then ID descending, removes duplicate IDs, and chunks the entire sequence at the registered scene capacity. POST0 holds 9, POST1 holds 4, and POST2/3 hold 6. Thus twelve arrived messages at POST2/3 produce 1–6, then 7–12. Month/year is an annotation derived from each actual spread's newest and oldest dates, including a mixed-month/year range; it never splits a spread. Only the last spread can be short when the archive is built.

Arrival follows `Conversations.letterState(message, false).locked`, whose authoritative input is the server's `unlocked` flag. Local time, a read timestamp, cached plaintext, and a mutual/manual announcement cannot release an envelope. Arrived unread mail remains a sealed physical envelope. Pending direct incoming records remain available under **Unterwegs**, explicitly counted as **Ankündigungen**, with their original deliberate arrival-dialog gate action. They have no physical envelope, stamp, slot, or batch membership. Outgoing/group data is retained outside the physical received archive.

Previously, month grouping created premature short pages. The pointer owner also mapped DOWN directly to an absolute time fraction (including the engraved end arrows), while the accessible back arrow stepped once and the forward arrow reset to page zero. Every drag update rebuilt the archive. APK85 distinguishes an end tap from a handle/track drag in one touch owner: each end steps one complete spread, a handle snaps to whole-spread stops, and wheel events and keyboard arrows/PageUp/PageDown step one spread. Accessible arrow actions follow the same older/newer rule. Normal camera panning is unchanged.

Read/unread and network updates retain the entire saved archive partition and its slot positions. Ordinary next/previous actions, including between new arrivals, keep those exact chunks. A time gesture freezes that saved archive at DOWN through UP. Newly arrived messages enter only on explicit filter/query/status changes or deliberate reselection of the current knob/detent. Deletion or arrival revocation removes the live face immediately, leaving its slot empty throughout ordinary paging until that explicit refresh. The saved archive never recreates a deleted API record. The existing physical renderer, masks, envelope art, stamps, camera and registered filter/rail geometry are retained.

POST0 paints one combined compact line on its original upper wood, e.g. `1–9/28 · I–XII`, derived from saved positions, total and actual endpoint dates. The separate Canvas month engraving is suppressed only for POST0; its ticks and photographed knob remain. The line fits both wood width and the height above the knob; full month/year and range remain on that actual Text's accessibility description. POST1–3 presentation is unchanged.

The reading paper shows a 15sp sender and 12sp date, then the actual authorized AES-GCM/AAD-decrypted content in 20sp ink with 29sp line height. Optional title/cover metadata follows in 13sp. The generic sealed preview is no longer placed above the plaintext. The original paper scroll, retry, attachment, unread/read-once, person filter and previous/next actions remain.

## Root validation and capture entry points (UNRUN by source author)

Use the authorized checkout's `android` directory, Root's already validated JDK/SDK/Gradle environment, and the parent-held 714 lock. Do not reacquire it. All temporary output must remain under TMPDIR. Environment observed during authoring:

```sh
export TMPDIR=/home/veit/hermes-andrea-clean-20261016/home/profiles/andrea/cache/scratch
export ANDROID_HOME=/home/veit/Android/Sdk
export POST_BATCH84_SHOTS="$TMPDIR/post-batches84-native"
export POSTAL_REPAIR_SHOTS="$TMPDIR/post-batches84-repair"
export LAYERMAXXING_GREGOR_TEST_URL=https://gs-layermaxxing.duckdns.org/test-gerfried/
unset LAYERMAXXING_GREGOR_URL LAYERMAXXING_GERFRIED_URL
```

`JAVA_HOME` and `GRADLE_USER_HOME` were unset in the author's environment. Root must retain its proven Java 21 and cached test setup. New native tests use intercepted OkHttp only; the synthetic bearer/AAD/crypto path never needs a live account.

First compile/run the new focused production paths:

```sh
./gradlew --no-daemon --max-workers=2 \
  "-Dorg.gradle.jvmargs=-Xmx2048m -XX:MaxMetaspaceSize=768m -Dfile.encoding=UTF-8 -Djava.io.tmpdir=$TMPDIR" \
  -Pkotlin.compiler.execution.strategy=in-process testDebugUnitTest \
  --tests '*PostAllStagesProductionTest.wholeBatch84*'
```

Then run the related gates without dropping the existing workflow filters:

```sh
./gradlew --no-daemon --max-workers=2 \
  "-Dorg.gradle.jvmargs=-Xmx2048m -XX:MaxMetaspaceSize=768m -Dfile.encoding=UTF-8 -Djava.io.tmpdir=$TMPDIR" \
  -Pkotlin.compiler.execution.strategy=in-process testDebugUnitTest \
  --tests '*PostAllStagesProductionTest' --tests '*PostalBatchesTest' \
  --tests '*LetterTree*Test' --tests '*LetterSceneTest' --tests '*LetterOpenRecoveryTest' \
  --tests '*Post0RedesignPilotTest' --tests '*PostCleanPilotTest' \
  --tests '*PostalPhoneRepair*Test' --tests '*PostalPhoneFeedback*Test' \
  --tests '*PostUpperMotion*Test' --tests '*CodexLetter*Test' \
  --tests '*StageScene*Test' --tests '*StageStoryTest' --tests '*IslandWorldStagesNavigationTest' \
  --tests '*PostalCanvasRegistrationTest' --tests '*PhysicalPostalGeometryTest' \
  --tests '*PostalStorageProfileTest' --tests '*PostalStorageInteractionTest' \
  --tests '*ReleaseScheduleTest' --tests '*LetterDraft*Test' \
  --tests '*ShakeReport*Test' --tests '*CrashDiagnostics*Test' --tests '*IslandConstantGround*Test' lintDebug
```

Finally Root must run the unsuppressed full regression (`testDebugUnitTest lintDebug`, no `--tests` filter) and compare against the supplied APK83 baseline **1187 tests / 0 failures / 311 skips**. The new source adds 28 tests, with no new skips or removed tests. This is a source count, not a predicted passing result.

`PostAllStagesProductionTest.wholeBatch84*` exercises all four stages at normal 411dp/font1 and compact 320/360dp/font1.5: independent cross-year exact-ID oracle, three full spreads plus one final message, real engraved-end clicks in both directions, real handle DOWN/MOVE/UP (neighbour and multiple stops), held face completeness, one-unit mouse wheel, keyboard and accessible increments, restore, arrival between gestures and during a held gesture, frozen ordinary paging after live revocation, physical same-knob refresh, actual compact range/Roman TextLayout and brass-clearance assertions, pending-only composer/counts and authenticated first/second-tap reading. Existing production read-once, timeout/cancellation, mark-unread and release/draft gates remain in the broad suites. Old gate-navigation tests enter actual announcements and retain their callback/no-content assertions.

PNG path: `$POST_BATCH84_SHOTS/post<level>-<fontScale>/<method>/<frame>.png`. Examples:

- `post2-1.0/wholeBatch84Post2/batch84-0-page-0.png`
- `post3-1.5/wholeBatch84Post3Compact320/batch84-0-page-0.png`
- `post0-1.5/wholeBatch84ReadingPost0Compact/reading84-first-authenticated-text.png`
- `post1-1.5/wholeBatch84ReadingPost1Compact360/reading84-scrolled-actions.png`
- `post0-1.5/wholeBatch84PendingOnlyPost0/pending84-announcements-no-envelopes.png` (REST before composer; equivalent POST1–3 paths)
- `post2-1.0/wholeBatch84Post2/batch84-explicit-knob-refresh.png`

Each method directory also contains `actual-frames.csv`, `metadata.txt`, and (when applicable) `batch-frames.csv` with actual clock/page/IDs and `reading-layout.csv` with native text sizes/line heights/bounds/lengths. Captures are opt-in Android View.draw with explicit Compose frames and main-looper flushing; they are synthetic renderer evidence, not device/APK evidence. `POST_ALL_STAGE_SHOTS` remains a fallback if POST_BATCH84_SHOTS is unset.

Root must inspect actual PNGs and CSVs, repair any compilation/runtime/native failures, and retain the full failure ledger. Only GitHub builds the deliverable APK. Workflow manifest/signature guards expect code85/name7.9.14-post-batches1 and artifact `postal-ui-test-apk-85`; the full regression job and all previous focused filters remain. Root owns its APK85 verifier (actual compiled NEWEST=8/CONSTANT=8, unchanged default Grid7 fixture, live Grid8, packaged geometry/image bytes, existing signer), GitHub checks/artifact, signature verification, integration and delivery to both recipients. No APK83 resend.
