# Backup and Restore — Design

**Date:** 2026-09-03
**Status:** Design decided. Implementation plan not yet written.
**Extends:** `2026-08-26-khata-wallet-design.md` §14 (backup)
**Builds on:** `2026-09-03-khata-background-work-design.md` (the worker).

Everything in this app lives in one file on one sideloaded phone. This is the plan for
that file surviving the phone.

**Drive upload is deliberately a second plan** (§8). It uploads what this one produces,
needs Google Sign-In and the Drive API, and is meaningless until backups exist and
restore is proven.

---

## 1. What is backed up, and the trap in it

`khata.db` — and **not that file alone.**

Room runs in WAL mode, so recent writes live in `khata.db-wal` until a checkpoint folds
them in. Copying `khata.db` by itself would produce a backup that is silently missing
the most recent transactions — the ones most likely to matter, and the ones least likely
to be noticed absent until a restore.

**So a backup checkpoints first**: `PRAGMA wal_checkpoint(TRUNCATE)` runs before the
bytes are read, which makes `khata.db` complete on its own.

Media is out of scope because no media feature exists. §14's second tier has nothing to
back up yet.

---

## 2. The passphrase, and what it costs

The key is derived from a passphrase the user sets once — **PBKDF2WithHmacSHA256**, then
**AES-256-GCM**. Both are in the JDK; no dependency.

This is §14's central decision and it is worth restating why. A key generated in the
Android Keystore never leaves the device, which would make every backup unrestorable on
a replacement phone — precisely the scenario backup exists for. A passphrase is the only
thing that travels.

**Losing the passphrase means losing every backup**, and the app says exactly that,
in those words, on the screen where it is set. Not in a help page.

**The passphrase itself is never stored.** What is cached, in app-private storage, is
the *derived key*, so a nightly backup can run without prompting. A derived key cannot
be turned back into the passphrase, so a device compromise does not hand over the phrase
that unlocks every historical backup elsewhere.

That cache is the same trade-off taken for the API key
(`2026-09-03-khata-ai-fallback-design.md` §4) and for the same reasons: app-private
storage is already OS-sandboxed, and the threat this defends against is a backup file
that has left the device, not the device itself.

---

## 3. The file format

A header the restorer can read before it can decrypt anything, then the ciphertext.

| Field | Why it is in the header |
|---|---|
| Magic `KHATABK1` | So a wrong file is refused as a wrong file, not as a wrong passphrase |
| Format version | So a future format change is a clear message rather than a crash |
| Schema version | So a backup newer than the app is refused (§4) |
| Salt (16 bytes) | Per-backup, so two backups of the same data are not identical |
| IV (12 bytes) | GCM requires a unique one per encryption |

The header is plaintext by necessity — it is what makes a refusal specific. It contains
nothing about the user's money.

---

## 4. Restore is the half that must not be clever

Pick a file, enter the passphrase, and the database is replaced.

**Four refusals, each with its own message**, because "restore failed" is useless at the
moment it appears:

1. Not a Khata backup — the magic does not match.
2. A newer format than this app understands.
3. **A newer schema than this app can open.** Room migrates forward, never back, so a
   backup from a later version would either fail cryptically or corrupt. Refused by
   comparing the header's schema version against the app's.
4. Wrong passphrase — GCM authentication fails, which is a *detection*, not a guess.

**The replacement is not done under a live database.** The decrypted bytes are written
to a staging file, verified as openable, and only then swapped in — after which the
process restarts so Room reopens cleanly. A half-swapped database is the one outcome
worse than no restore.

**The current database is kept as `khata.db.replaced` until the next successful launch.**
A restore that goes wrong must not be the thing that loses the data.

---

## 5. When it runs

A nightly `PeriodicWorkRequest`, on the base the snapshot job already established, plus
a manual "Back up now" in Settings.

**Seven are kept, oldest pruned.** A single rolling file would mean a corruption written
last night is the only copy; seven days is enough to notice something wrong and reach
back past it, at a few hundred kilobytes each.

**No passphrase means no backup**, and Settings says so — the same shape as the AI
fallback's missing key. There is no separate toggle to disagree with it.

---

## 6. Sharing

A `FileProvider` and a plain Share sheet. The user sends the file wherever they choose:
Drive, email, a cable.

Nothing is uploaded automatically in this plan. The file is encrypted, so where it lands
is a matter of convenience rather than exposure.

---

## 7. Testing

- **Round trip**, the load-bearing test: a database with known rows, backed up,
  restored into an empty app, and every row still there.
- **A wrong passphrase is refused**, and is distinguishable from a corrupt file.
- **The four refusals of §4**, each producing its own message.
- **The WAL checkpoint**: rows written immediately before a backup are present in it.
  This is the one that would otherwise fail silently in the worst possible way.
- **Pruning keeps seven and removes the eighth.**
- **Not unit-tested:** the Share sheet, and the process restart after a restore.
  Verified on the device.

---

## 8. Out of scope

- **Drive upload and Google Sign-In.** The next plan; it uploads what this one makes.
- Media (§1) — there is no media.
- Restoring a single account or a date range. This replaces the database or does
  nothing; a partial restore is a merge, and a merge needs conflict rules nobody has
  asked for.
- Scheduled export to a user-chosen folder. Share covers it without a permission.
