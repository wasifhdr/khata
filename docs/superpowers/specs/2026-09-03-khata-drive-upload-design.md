# Drive Upload — Design

**Date:** 2026-09-03
**Status:** Design decided. Implementation plan not yet written.
**Extends:** `2026-09-03-khata-backup-design.md` §8 (deferred), `2026-08-26-khata-wallet-design.md` §14
**Builds on:** the backup this uploads, and the worker base from
`2026-09-03-khata-background-work-design.md`.

The backup plan produced an encrypted file on the phone. This one gets it off the
phone, so that losing the phone is not losing the money.

---

## 1. What this adds, and what it refuses to touch

It adds an upload after each backup, a list of Drive backups beside the local ones,
and a download.

It does **not** touch restore. `BackupRepository.restore` and the passphrase dialog
already work and were walked end to end on hardware: the database was replaced
byte-for-byte, `khata.db.replaced` was kept, the process died and came back on a new
pid. Drive's only job is to hand those same bytes to that same function. Every
refusal in `2026-09-03-khata-backup-design.md` §4 keeps working unchanged, because
none of them know where the bytes came from.

The file is encrypted before it ever reaches this code. Drive holds ciphertext, so
Google is storage, not a party to the data.

---

## 2. One dependency

`com.google.android.gms:play-services-auth:22.0.0`, for the account and the token.

**Not `google-api-services-drive`.** The Drive client library drags in the whole
Google API client stack — an enormous addition to an app whose only other network
call is 30 lines of `HttpURLConnection`. The REST API is four requests. The scope is
a string, so even `DriveScopes.DRIVE_FILE` is not worth a library:

```
https://www.googleapis.com/auth/drive.file
```

Everything else is `HttpURLConnection` and `org.json`, matching `GeminiClient`
(`2026-09-03-khata-ai-fallback-design.md`). That is the house pattern for HTTP here,
and there is no reason for a second one.

---

## 3. The account boundary

`core/drive/DriveAuth.kt`. Two ways in, one `AuthorizationClient`.

**Connecting**, from Settings, where an Activity exists:

```
Identity.getAuthorizationClient(activity)
    .authorize(AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(DRIVE_FILE)))
        .build())
```

If the result reports `hasResolution()`, its `PendingIntent` is launched for consent;
`getAuthorizationResultFromIntent` then yields the grant. The account name is written
to DataStore. **That is the only thing stored** — no token, no refresh token.

**Nightly**, from the worker, where no Activity exists:

```
Identity.getAuthorizationClient(applicationContext)
    .authorize(AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(DRIVE_FILE)))
        .setAccount(Account(storedName, "com.google"))
        .build())
```

The `Context` overload of `getAuthorizationClient` is what makes an unattended upload
possible at all, and `setAccount` is what stops a picker appearing at 02:00. Both
were confirmed against the 22.0.0 artifact rather than the documentation, which
renders its signatures client-side and cannot be read.

If a worker's `authorize` comes back with `hasResolution()`, the grant is gone. A
worker cannot show consent UI, so it records that and stops; Settings reads
**"Reconnect to Drive"** and the next tap there has an Activity to work with.

Access tokens last an hour and are never persisted. Each run mints its own, which
means a stolen phone yields no long-lived Drive access — Play Services holds the
grant, and the app holds nothing worth taking.

---

## 4. The Drive layer

`core/drive/DriveClient.kt`, four calls against `drive/v3`, each with
`Authorization: Bearer <token>`.

| | Request |
|---|---|
| Folder | `POST /drive/v3/files` — `{"name":"Khata","mimeType":"application/vnd.google-apps.folder"}`. Once; the id is cached in DataStore |
| Upload | `POST /upload/drive/v3/files?uploadType=multipart` — `multipart/related`: a JSON metadata part naming the file and its parent, then the `.kbk` bytes |
| List | `GET /drive/v3/files?q=<folderId> in parents and trashed=false&fields=files(id,name,size)` |
| Download | `GET /drive/v3/files/<id>?alt=media` |
| Delete | `DELETE /drive/v3/files/<id>` |

**`drive.file` is what makes the list safe.** The scope grants per-file access to
files this OAuth client created, so a list request cannot see the rest of the user's
Drive even if it asked. The app is not trusted with a view of everything and does not
need to be.

The folder is visible in My Drive by choice, so a backup can be fetched from
drive.google.com on any machine without Khata's involvement. If the folder is later
moved or deleted, the cached id 404s and it is recreated — a stale id must not be a
dead feature.

**Multipart, not resumable.** Resumable upload exists for large files that cannot
afford to restart. These are a few hundred kilobytes; a failed upload is retried whole
by WorkManager, which is simpler than a session protocol and correct at this size.

---

## 5. When it runs, and what is shown

`BackupWorker` uploads after it writes, under a `NetworkType.CONNECTED` constraint.
"Back up now" takes the same path. **The local backup is written first and its success
does not depend on the upload** — an offline phone still gets its nightly file.

Drive keeps the same seven as the phone, oldest deleted after a successful upload. The
delete runs only on success, so a failed upload cannot prune the copy it failed to
replace.

**Settings shows when the last upload happened, and this is not decoration.** A backup
system that quietly stops is worse than one that never existed, because it is trusted.
Three states, from two stored values:

- Not connected — no account stored.
- `Last uploaded 3 Sep, 02:00` — `driveLastUploadAt`.
- Reconnect to Drive — `driveNeedsReconnect`, set when a worker's `authorize` came
  back needing resolution.

---

## 6. Restore from Drive

"Restore a backup" already lists the phone's own backups by date. Drive's join that
list under their own heading. Picking one downloads it and passes the bytes to
`SettingsViewModel.onRestore`, which is unchanged.

This works on a phone that has never seen this data because the OAuth client is the
same — same package, same certificate — so a fresh install can list and read the files
the old install created. That is the whole scenario backup exists for, and it needs no
extra machinery.

Downloading needs the network and the account; the list is empty and says so when
either is missing. Local backups remain listed regardless, so a restore is never
blocked on Drive being reachable.

---

## 7. What goes wrong, and the answer to each

| | |
|---|---|
| Not connected | Upload skipped, no error. Settings says "Not connected" |
| Grant revoked or expired | Worker records it, stops. Settings says "Reconnect to Drive" |
| Offline | WorkManager's network constraint defers the run |
| 401 | The token went stale mid-run. Mint once more, then give up |
| 403, 5xx | `Result.retry()` — quota and outages are temporary |
| Folder gone | Recreate it and upload into the new one |
| Upload fails | Local backup is untouched and already written. Nothing is pruned |

None of these can lose data, because none of them run before the local backup is
safely on disk.

---

## 8. Signing, and the setup that is not code

Drive authorises by package name and signing certificate, so this needs a signing key
that does not change. Khata had none: release was unsigned and debug used the
auto-generated `~/.android/debug.keystore`.

**One keystore now signs both build types**, so a release build does not silently
lose Drive access:

- `C:\Users\Wasif\.android\khata-release.jks`, alias `khata`, valid to 2054, kept
  outside the repository so it cannot be committed by accident.
- SHA-1 `6C:8E:26:F4:50:9C:46:C8:B2:C7:15:7A:C0:6C:79:E4:3F:B0:7D:54`. A certificate
  fingerprint identifies a key without being one, so it is not a secret.
- `build.gradle.kts` gains a `signingConfig` reading a gitignored
  `keystore.properties`; both `debug` and `release` use it.
- `.gitignore` gains `keystore.properties` and `*.jks`. It already has `*.keystore`,
  which does not match a `.jks`.

**Losing that file means being unable to sign an upgrade the phone will accept** —
Android refuses an update signed by a different key, so the only way back would be
uninstalling Khata and losing its data. It belongs off the machine, like the backup
passphrase.

In Google Cloud Console, project `khata-9479`:

1. An **Android** OAuth client, package `com.wasif.khata`, with that SHA-1. Android
   clients have no secret and nothing is pasted back into the app — Play Services
   matches on package and certificate.
2. **Data Access:** `drive.file`, and nothing else. A broader Drive scope would make
   the app verification-eligible and give it reach it has no use for.
3. **Audience: published, not Testing.** Grants under Testing status expire on a
   seven-day clock, which would mean reconnecting Drive every week forever. Because
   `drive.file` is non-sensitive, publishing requires no verification, no review and
   no demonstration video.

---

## 9. Testing

Following the precedent `GeminiClient` set: the network call itself is not
unit-tested, the parsing beside it is pure and is, and consumers depend on narrow
`fun interface`s so their tests need neither network nor account —
`DriveUploader` for the worker, `DriveBackups` for the view model, in the shape of
`RuleSuggester`.

- **`multipartBody(metadata, bytes, boundary)` is a pure function and is tested.**
  This is the load-bearing one. A malformed multipart body uploads a file that is the
  right size and the wrong bytes, and nothing notices until a restore fails — the same
  failure mode as the WAL checkpoint, which is why it gets the same treatment.
- **`parseFileList(json)`** — ids and names out, and null rather than a crash on
  malformed or empty responses.
- **`toDelete(names, keep)`** — pruning keeps seven and names the eighth, without a
  network to prove it.
- **The worker with a fake uploader:** uploads after a successful backup, skips when
  no account is stored, retries on failure, and never prunes after a failed upload.
- **Not unit-tested:** the consent screen, the live Drive calls, and the download.
  Verified on the device, as the backup walkthrough was — which is where the salt bug
  was caught, and it would not have been caught anywhere else.

---

## 10. Out of scope

- **Media.** There is still none.
- **More than one account**, and sharing the folder. One person, one phone.
- **Resumable upload** (§4) — wrong tool at this size.
- **Drive as the source of truth.** Nothing syncs Drive back down except an explicit
  restore the user asked for. A backup that reaches back into the live database on its
  own is a data-loss bug waiting for a schedule.
- **Partial restore**, still. This replaces the database or does nothing.
