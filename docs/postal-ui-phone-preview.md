# Postal UI phone-test candidate

This CI-only snapshot was requested for installing and testing the current postal UI on a phone. It is not a release approval or completion of the 15-scene redesign.

- Version7.9.14-post-test1, code74; applicationId `at.gregor.layermaxxing.gerfried` unchanged.
- GitHub builds the APK. Existing debug signing certificate must match the previously delivered app; missing/different signing key aborts artifact delivery rather than recommending uninstall/data loss.
- Existing accounts/data are not replaced with fixtures. Android unit/review fixtures are test-only, not packaged as user data.
- Existing backend URL remains configured; server is NOT deployed or migrated by this preview workflow. Backend-dependent features not present on that server are not magically supplied by a frontend build.
- Focused postal tests and lint must pass before the APK is built/uploaded. Full Android regression runs independently and fails normally if any tests fail; the artifact is still a test candidate, not a green full-suite release.
- No release/latest tag is created. No push/merge to `gerfried-erweiterung` or `gregor-erweiterung` is performed by this CI-only branch/workflow. No deployment job or production-database access exists in the workflow.

The corrected foreground/background full-canvas measurement, perspective registration, cavity lighting, lift/rotation and actual time-runner paging are included. A real phone performance/crash fix is not claimed by JVM tests.
