# Background Work — Ingestion as Durable Work

**Date:** 2026-09-03
**Status:** Design decided. Implementation plan not yet written.
**Extends:** `2026-08-26-khata-wallet-design.md` §10 (historical backfill)
**Builds on:** `2026-09-02-plan2a-sms-ingestion-design.md` §9

Backfill and reparse are collected in `viewModelScope`. Leave the Settings screen
mid-pass and the pass dies, halfway through a multi-year inbox, with no record that it
was ever running. This moves them onto WorkManager, which is also the foundation the
nightly net-worth snapshot needs.

---

## 1. Scope

**In:** the WorkManager foundation, backfill and reparse as durable work, progress
read from `WorkInfo`, a first-launch backfill, and the SMS receiver enqueueing rather
than parsing inline.

**Not in, and deliberately a second plan on the same base:** `balance_snapshots`, the
schema v5 migration, the nightly 00:05 job, and switching the Wallet chart off its
derived walk. Those carry a migration and a change to a figure already on screen;
they are independently riskier than moving ingestion and should fail independently.

---

## 2. One worker, three modes

`BackfillUseCase` and `ReparseUseCase` already return the same type,
`Flow<IngestProgress>`. Two workers would be two copies of one `collect`-and-publish
loop, and the receiver would make a third.

**So: one `IngestionWorker`, with a `mode` input.**

| Mode | Runs | Input |
|---|---|---|
| `BACKFILL` | `BackfillUseCase` — the whole SMS inbox | none |
| `REPARSE` | `ReparseUseCase` — every stored raw message | none |
| `MESSAGE` | `IngestionPipeline.ingest` for one message | sender, body, receivedAt |

A failure returns `Result.retry()`; parsing is idempotent by construction (§7.1 of the
wallet spec), so a retried pass cannot double-record.

---

## 3. Unique work replaces a remembered guard

`SettingsViewModel.runPass` guards concurrent passes with
`if (_backfill.value?.isComplete == false) return`. That guard lives in memory, so it
is forgotten the moment the screen dies — and two whole-inbox passes racing on the
same tables is precisely what it exists to prevent.

**Enqueue `BACKFILL` and `REPARSE` under one unique name with
`ExistingWorkPolicy.KEEP.`** The constraint becomes structural rather than remembered,
and the ViewModel loses the guard rather than gaining a better one.

**`MESSAGE` stays off that name.** A message arriving during a backfill must not be
dropped for colliding with it, and it is not a whole-inbox pass — it contends for
nothing.

---

## 4. Progress through WorkInfo

The worker publishes `IngestProgress` via `setProgress`; Settings observes work by
unique name rather than by id, so it does not need to have been the screen that
started the pass.

This is the point of the change, not a detail of it: progress currently lives in a
`MutableStateFlow` inside the ViewModel, so returning to Settings during a backfill
shows nothing running. Reading `WorkInfo` means the screen reports what is actually
happening, whether or not it was there when it started.

`IngestProgress` is two ints and an `IngestSummary` of six. `Data` holds no nested
objects, so it is flattened into named ints on the way in and rebuilt on the way out.
**That pair is the one piece of real logic here, and it gets a round-trip test** — a
field dropped in the flattening is silent, and shows up only as a progress bar that
never fills.

---

## 5. First launch

Wallet spec §10: backfill runs "on first launch, and on demand afterwards". Only the
on-demand half exists, so a fresh install shows an empty app until the user finds the
button — the failure the spec called out.

`KhataPreferences` gains `hasBackfilled`, set when a backfill completes. When SMS
permission is first held and the flag is false, one backfill is enqueued.

**Where that check lives:** an application-scope collector in `KhataApplication`,
beside the seeder and the widget's theme push. It is the established shape for "the
app noticing something and reacting once", and it is the only place that sees both
preferences and permission without belonging to a screen — putting it in
`SettingsViewModel` would mean a first launch that never opens Settings never
backfills, which is the whole case being solved.

**The flag is the guard, not an empty ledger.** "No transactions yet" is also the
honest state of a user whose inbox holds no bank messages, and testing for it would
rescan the entire inbox on every launch, forever.

---

## 6. The receiver

`SmsReceiver` is **not** the fire-and-forget bug it might look like: it already calls
`goAsync()`, deliberately and with a comment. The pending result holds the process for
roughly ten seconds, which is ample for one parse.

The remaining gap is narrow. If a parse outruns that allowance, or the device is under
memory pressure, the message is dropped — recoverable only by a later backfill. That
recovery is real, because the SMS itself is still in the inbox and parsing is
re-runnable, so **this is a hardening, not a bug fix**, and it is recorded as such so
nobody later reads the change as urgent.

Enqueueing `MESSAGE` work buys retry with backoff and removes the ten-second ceiling.
The cost is the message body passing through WorkManager's own database. That is
acceptable here and nowhere near a new exposure: raw messages are already stored
forever, on purpose, in this app's own tables.

---

## 7. Wiring, and the trap in it

- `androidx.work:work-runtime-ktx` **2.11.2** (2.12.x is release-candidate only).
- `androidx.hilt:hilt-work` and `androidx.hilt:hilt-compiler` **1.4.0**, on KSP. This
  is the same group and version as the catalog's existing `hiltNav`, so that reference
  is renamed `androidxHilt` and serves all three rather than drifting apart later.
- `KhataApplication` implements `Configuration.Provider` and supplies the injected
  `HiltWorkerFactory`.

**The trap:** WorkManager also initialises itself through `androidx.startup`. Left in
place it wins the race, initialises with the default factory, and every `@HiltWorker`
fails to construct at run time with nothing wrong at compile time. The manifest must
remove **the `WorkManagerInitializer` meta-data specifically** — not the
`InitializationProvider` node wholesale, which would silently take every other startup
initializer with it.

---

## 8. Testing

- **The `Data` round-trip** (§4), as a plain JVM test. Every field of `IngestProgress`
  and `IngestSummary` survives a flatten and rebuild.
- **The worker**, through `TestListenableWorkerBuilder` against a real in-memory Room
  database, as the pipeline tests already do: `BACKFILL` records what the corpus
  implies, `MESSAGE` ingests one message, and a pipeline that throws yields
  `Result.retry()` rather than `failure()`.
- **Not unit-tested:** the unique-work policy and the first-launch enqueue. Both are
  wiring, and asserting them would mean faking `WorkManager` to watch it be called —
  a test of the mock. They are verified by hand on the device.

---

## 9. Out of scope

- Everything named in §1 as the second plan.
- Constraints of any kind — network, charging, battery. All of this is local work that
  should run when asked, and a constraint here would only delay it.
- A notification for a running backfill. It is minutes on a cold install and never
  again; the Settings row already reports it.
- Cancellation from the UI. Nothing in the spec asks for it, and a pass that cannot be
  stopped is not a problem while it is idempotent and re-runnable.
