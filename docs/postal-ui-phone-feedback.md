# Postal phone feedback — isolated test candidate 2

This preview branch contains the native postal interaction corrections following real phone feedback1253. It is NOT the completed15-scene release. The app remains `at.gregor.layermaxxing.gerfried`; version75 / `7.9.14-post-test2` updates the same-signed test1 installation without uninstalling.

## Native changes
- Locked-envelope taps show real arrival/release metadata with truthful manual/unknown/unconfirmed states, never leaked content or invented unlocks.
- Unhandled background taps settle decorative motion; server WAITING/auth/release gates are not skipped. Outside-paper taps return from reading; paper scrolling and controls retain ownership.
- Named person/status controls, searchable person/mail selection and real counts, named close and visible scroll hints replace hidden affordances. Large brass timeline grip pages actual mail; numeric date remains readable at360dp/font1.5.
- Readable sender patches stay on their source-registered physical envelope faces. Old detached count ink and broken vertical writing label removed. Small POST0 travelling-letter status typography stays above resting controls.
- POST3 groups up to9real letters per page into3registered trays, with every member selectable from its own real source tray. **Only3physical representative envelopes remain visible. Extra simultaneously visible mail is explicitly OPEN; this is not9visible envelopes.** No phantom or duplicate decorative messages.

## Evidence and boundaries
The original artwork, full-canvas foreground registration and physical source geometry are preserved. All test/capture data is synthetic; no phone stability or production-account E2E claim is made.

GitHub must require backend regression, the expanded focused postal tests and lint before building/signing the APK. The complete Android regression is a separate hard-failing, unsuppressed job, not silently ignored. No local APK assembly, server deployment or release creation is part of this preview workflow. Optional pip/Gradle Actions caches are disabled after the account cache-egress limit stalled the earlier server-sync workflow; tests are not weakened.

The live Gerfried test server already matches the unchanged server tree from preview1: `b2d06079a1c57cd43cef4718b5a96a322cebb852`, deployed commit`404015bcabc90fd51d44db877b28e8617c468f53`, grid7. This preview merges that ops update but does not deploy a different backend or alter Gregor's branch/server.
