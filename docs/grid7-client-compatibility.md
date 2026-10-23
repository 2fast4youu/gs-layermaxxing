# Critical client/server rule7 correction — preview76

APK75/commit a8e4195 declared PlacementRule.CURRENT6 despite a live server publishing7. Its actual DEX was inspected and confirmed CURRENT=0x6; a new compiled-APK/live-metadata verifier rejects that real artifact. Matching server trees did NOT prove app compatibility. The previous full-compatibility claim was incorrect.

This update implements the existing server7 semantics on the client, not a cosmetic version/banner override:
- exact known7 and fail-closed unknown/future/malformed handling;
- unchanged ground6 geometry, harbour and decoration;
- lighthouse build/move/relocated upgrade must touch the open-sea meadow coast, excluding enclosed inland rock;
- existing inland locations remain saved and may upgrade in place when next-stage ground/occupancy fits;
- validation/targets/snapping/confirm/API submission agree using the original saved ledger, not a preview-overridden position.

Local actual execution:83 client rule/parity/API/UI cases passed;9 existing backend edge tests passed using isolated temporary test DBs. Combined rule+postal regression:225cases/0failures/0errors/0skips, lint0. Independent oracle covers all lattice candidates across6land sizes and4lighthouse stages. Production IslandWorld synthetic-data/injected-writer sequence verifies no-write before confirm/on cancel, exactly one confirmed coast move and no obsolete newer-rule warning. Native image review passes only the reviewed pixels, not phone/live-account E2E.

CI requires expanded rule+postal checks before building the same-signed update76 /7.9.14-post-test3. It also reads the actual APK PlacementRule DEX CURRENT field, compares it with server implementation and edge fixture, and checks packaged ground/edge bytes before artifact publication. Root must additionally check the downloaded actual APK against public live metadata. Ground JSON remains6, edge JSON7. No backend deployment, production DB/account operations, rule downgrade or automatic relocation.

Existing postal changes remain. Full Android suite is a separate unsuppressed hard-failing job; previous APK75 full run918/27failures/292skips is not this update's result. More physically visible POST3mail remains open;9selectable is not9visible. This is a test candidate, not overall release approval.
