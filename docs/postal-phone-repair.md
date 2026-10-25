# Postal phone repair after APK78

Source changes are in the clean `ci-island-crash-20261023` workspace. The original WIP8 tree is not an input to this change. Version, signing, builds and release delivery belong to Root.

## Evidence and open lifecycle

The photo establishes the POST0 appearance, including the beige bottom panel. It does not establish the exact phone/network failure. No phone log was available or collected.

The production chain is `LayerHome.openLetter` → account `LetterOpener` → `openLetterContent` → authorized `ApiClient.content` → AES-GCM authentication of body/attachment with the existing AAD → observable `opened` → `WAITING / UNSEALING / EMERGING / READING`. IslandWorld and PostOffice already forwarded the real opener and its request map; those forwarding paths were not replaced.

Source-level liveness defects in APK78 were concrete:

* `WAITING` observed only verified content or `FAILED`. An absent callback, reset/removal of the request, or a callback that threw before creating a request had no recovery event.
* The coordinator wrote `RUNNING` before calling its fetch factory outside the coroutine. A throwing factory or a scope cancelled before coroutine entry could leave `RUNNING` behind.
* Content used synchronous `execute()` inside `withContext(IO)`. Cancelling the coroutine did not cancel the OkHttp call. Socket read/connect timeouts were not a total request deadline.

The repair keeps the same gates and success condition. Content uses cancellable OkHttp enqueue, a total 45-second call deadline, and no automatic connection retry for this potentially read-once GET. The opener has an independent 45-second watchdog, tracked jobs, explicit cancel/reset, generation plus per-attempt identity checks, and terminal failure cleanup. A cancelled/superseded result cannot populate content or overwrite a newer request. The account transport is captured before coroutine launch. Refresh/error callbacks cannot overwrite a verified success.

The UI returns to a retryable sealed state for failure, thrown callbacks, or a request still absent after a 250 ms snapshot grace period. That grace period classifies a missing request; it does not simulate network success. Only a new explicit central tap retries. Edge/system back leaves the view while the existing bounded request continues, preserving late-answer behavior. The waiting label explains that distinction. No new crash log event accepts IDs, contents, tokens or error text.

A timeout cannot prove whether a server already consumed a one-time key before its response was lost. Retry remains subject to the real endpoint's authorization/release/read-once decisions. System DNS itself may finish after call cancellation; stale results remain rejected.

## Input and scene layout

For SEALED and WAITING only, `tree-envelope` now covers screen space inset by 12 dp on every side. It is outside the transformed camera. WAITING consumes taps without calling `onOpen` again. `tree-outside` handles the outer band; system back also returns. The overlapping 48 dp chalk back target is omitted in these two phases so it cannot dismiss a valid central tap. The overlay is removed before reading/composition, leaving paper scrolling and existing input behavior intact.

POST0's full red wax bucket is registered separately at image fractions `(0.7575, 0.262)–(0.870, 0.367)`, transformed by StageGeometry and given a minimum 48 dp target. `tree-wax-bucket` enters exactly the pen's `TreeFocus.NEW` / `WRITE` flow and resumes the account draft. It works with empty mail. POST1–3 retain their padlock actions; individual travelling letters retain arrival information in every stage.

POST0 no longer renders the bottom control wall. Tap the small `Filter ▾` control above the upper wooden strap (`post-filter`). Its uncluttered menu has inline person/status selection, search, reset and a fixed close X. APK80 replaces APK79's generic white/SansSerif presentation with the scene's own approved painted paper, its real edges/folds, Kit.Body/Kit.Display type and warm brown ink. The quiet structure stays; this is not a return to thick stamp borders or a redesign of the envelopes. The person/status/mail actions retain their semantics IDs inside this menu. The strap uses ONE moving painted brass knob: a bounded derivative removes the old stationary knob and the extracted original knob moves on the original slot. The full actual band includes the knob in its drag target. Page/date and direction marks remain visible; held scrubbing renders whole pages without sliding fragments. Saveable filters/pages remain in the production component.

Nine POST0 faces remain nine real envelopes; 120 letters use 14 pages and the full mail selector. The user explicitly rejected replacement paper cards: the original NailedLetter/StoredSender renderer is restored exactly. Upper stages retain their actual page capacities and physical holders. Canvas registration, foreground masks, original envelope art/animation, island paging, crash-copy feature, server code and placement grid remain unchanged. The only new image assets are the bounded time-strap background derivative and its extracted painted knob.

Useful semantics IDs:

| ID | Actual action/state |
| --- | --- |
| `tree-letter-<id>` | Select real face; sealed first tap does not request content |
| `tree-envelope` | Broad screen-space central open/wait area |
| `tree-outside` | Selected sealed/waiting outer return band |
| `tree-seal-state` | Waiting/failure/open instruction |
| `tree-scene-<PHASE>` | Actual phase plus existing motion/page/visible-ID semantics |
| `tree-wax-bucket`, `tree-pen` | Shared native draft/composer entry |
| `tree-to-<id>`, `tree-write-text` | Actual recipient and draft controls |
| `post-filter` | Small Filter control above the wooden strap on POST0 |
| `post-person`, `post-status`, `post-mail` | Actions inside the clean filter menu |
| `post-time-rail`, `post-time-label` | Actual paging gesture/progress and page/date |
| `post-select-<id>`, `post-arrival`, `post-arrival-action` | Full selection and travelling-letter information |

`tree-time-back` and `tree-time-now` retain accessible step/now actions. On the painted strap these labels have semantics actions without competing pointer detectors; physical rail touches/drags map to time along the whole strap.

## Regression and renderer hooks

`LetterOpenRecoveryTest` exercises the actual coordinator for timeout/retry, cancellation, reset with a non-cooperative old answer, a throwing fetch factory, cancelled scope before launch, and a throwing refresh callback.

`PostalPhoneRepair*Test` runs native Robolectric production `LetterTreePlace` + real remembered opener + `ApiClient` + authenticated AES-GCM/AAD. Only HTTP is intercepted; the synthetic bearer header and content endpoint are checked locally. The unread test also uses the real `ApiClient.markUnread` endpoint through that interceptor. There is no socket, account or database. Inputs use actual pointer coordinates, with bounded explicit clock frames and snapshot/looper flushing. Coverage includes first/second tap, central/bottom/left/right/top interiors and all outer edges, success, corrupt content, denied authorization, offline retry, timeout and underlying call cancellation, missing callback, reset, leave/reenter/late answer, marked-unread resealing, one-time cached reopening, travelling gates, all 120 real selector entries/time pages, filters/restoration, and empty-mail wax-bucket draft resumption. It is inherited across POST0–3 at normal size and 320 dp/font 1.5. The wax-bucket-specific case applies to POST0; upper stages have their existing tested furniture.

Existing tests were retained. Obsolete POST0 rope/belt/padlock expectations were redirected to the real rope selectors and travelling-letter arrival action. The small-screen assertion now checks mail ink below upper rope controls instead of above the removed bottom panel.

Set `POSTAL_REPAIR_SHOTS` to a fresh scratch output directory when Root runs `PostalPhoneRepair*Test` to capture synthetic rest(0/120), sealed, waiting, success, error, composer and time states. Captures use Activity `View.draw`, not `captureToImage`, with explicit per-frame clock advance, Snapshot notifications and main-looper flushing. Per-test directories contain PNGs, actual clock/phase/request/pointer CSV and metadata; stdout records dispatched pixel coordinates and actual before/after phases. These are opt-in renderer results, not phone/APK screenshots. No captures or native phase/pointer results have been generated by the source author.

## Validation ownership

Root subsequently executed the actual normal411dp/font1 and compact360dp/font1.5 production pilots with real intercepted transport/AES-GCM, search/reset/person/status selection/120-mail picker/painted-knob drag and original-envelope guards:2tests/0failures/0errors/0skips/lint0. Latest local evidence is `filter-clean-20261025/MENU-FINAL-GATE.json`; actual frames and pixel review are in that sibling workspace directory. This is targeted JVM evidence, not a phone benchmark or full release approval. APK79 must be built on GitHub and verified separately; no local assemble is a delivery artifact. Earlier broad/full failures remain reported, not suppressed.

The source author ran `git diff --check` and a Python stdlib source/formula audit only. The formula audit checked time fraction/page round trips for 1–120 page counts; it does not execute Kotlin or validate native layout.

Kotlin compilation, Gradle/JUnit/lint, native rendering, APK79, signatures/class/grid verification and delivery remain unrun and belong to Root. Root should run `LetterOpenRecovery*Test`, `PostalPhoneRepair*Test`, existing postal/letter/motion/registration/physical-holder suites, and privacy/crash/grid regressions, then inspect normal/compact native captures and the actual phone result. No native test pass, exact phone diagnosis, APK build or delivery is claimed here.
