# Device-local letter drafts

The production tree paper (all four post stages), chat-to-letter sheet and Mehr →
Entwürfe use LetterDraft and the same actual send pipeline. Entwürfe is also
reachable from both the recipient paper and the writing paper through the wax/pen.
Neuer Brief saves the current begun letter, then starts a distinct UUID. Opening
and closing a letter updates that UUID instead of appending duplicates. Empty
untouched composers do not create records; selecting a recipient begins a letter.

Storage is plaintext in Android app-private `files/letter-drafts/<SHA-256 scope>/`.
`allowBackup=false` stays enabled. Drafts are never uploaded automatically. Scope
includes server profile AND complete base URL, plus the server-confirmed user ID
when available on first creation, otherwise the server-unique account name. A
separate private preference maps the account name to that frozen identity, so
status refresh, logout and token rotation do not strand earlier drafts. An absent
account name falls back to a SHA-256 token identity; raw credentials are never
written into draft files or names. Logout hides the list but retains local drafts
for the same returning account. Removing the app/data removes the drafts.

All text/recipient/group/title/cover/one-time fields and the complete raw schedule
choices are stored, including currently hidden absolute and relative point/window
values and units. Relative values are resolved only by ReleaseSchedule at send.
Each atomic JSON record contains its own attachment name, MIME and private byte
copy (at most 2,000,000 bytes). Records are bounded to 4,000,000 UTF-8 bytes. Picker
reads are bounded before allocation, without relying on newer InputStream APIs.
AtomicFile handles interrupted replacement. Corrupt records stay on disk and a
readable error accompanies any remaining readable entries.

Edits debounce for 350 ms. Close/back/outside, pause/stop/dispose, changing the
active letter, and send synchronously flush the bounded pending record. Attachment
changes commit immediately. Physical back/outside and sheet dismissal refuse to
close after a failed flush; the composer displays Fehler and permits retry. Account,
server, logout and bottom navigation callbacks also flush before leaving. The
writer's repository never changes account underneath a pending callback.

The list sorts by persisted last-edit time, then UUID. Unchanged flushes retain
that time. In-memory references for the same draft ID share the canonical draft;
explicit deletion invalidates other views so they cannot resurrect a deleted file.
Löschen opens confirmation. During send, editing and a second send are refused.
Failure, cancellation and missing receipt IDs retain the record. A nonempty actual
API receipt writes an atomic receipt marker and deletes only that draft's JSON,
including its attachment. The marker contains message IDs, no body or credentials;
it prevents stale views from recreating a sent draft.

Limits needing runtime review: IO is bounded but final atomic flush runs on Main;
Root must inspect latency with a 2 MB attachment on a device. Abrupt process kill
inside the 350 ms edit debounce can lose those last keystrokes (normal pause/stop
flushes them). Network cancellation after server acceptance can leave an uncertain
send, as with the existing transport; there is no backend idempotency change in
this source-only task. If storage becomes unwritable precisely after an accepted
send and before its receipt marker can be written, memory refuses a second send
and shows an explicit success/cleanup warning; durable receipt recovery cannot be
guaranteed until storage works again. Root must verify actual receipts and avoid
blind retry. No device, build, runtime, pixel or test pass is claimed here.

See `../shake-debug-20261026/DRAFTS-RESULT.md` in the paired workspace for Root gate
filters, native-capture environment, expected output paths and review obligations.
