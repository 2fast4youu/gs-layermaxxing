# Android signing keys

## gs-layermaxxing

- Storage: GitHub Actions secrets in `2fast4youu/gs-layermaxxing`.
- Secret prefix: `ANDROID_RELEASE`; suffixes `KEYSTORE_B64`, `STORE_PASSWORD`, `KEY_PASSWORD`, `KEY_ALIAS`.
- Public certificate SHA-256: `1e8d34ee5627cc2c794dd862a89b3822b94956b8b19540bc8d0d22f94f334198`.
- Dedicated new release key. Existing debug/original keys are NOT changed. A different installed signer cannot be updated in place with this key. No app has been uninstalled or migrated.

## Use from another device

Private keys are not in Git and are not downloaded by cloning. Authorized Actions workflows in this repository can restore the per-app secrets into a temporary file and sign APKs. Secrets are repository-scoped, not automatically shared with other repositories. Do not print them, cache them, upload them as artifacts, or expose them to untrusted PR code.

The `Verify per-app Android signing key` workflow checks the full certificate fingerprint and performs a real private-key signing/verification round trip. It does not build or publish application APKs. Existing app build workflows are unchanged; connect their intended release build to these secrets before claiming automatic signed APK delivery. A fresh clone must never silently generate a replacement key.
