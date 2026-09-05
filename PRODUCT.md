# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Stack

Kotlin · Jetpack Compose · Material 3 · Room · Hilt · Paging 3 · WorkManager · RemoteViews ·
Coil · kotlinx.serialization · Haze. `minSdk 33`, `compileSdk` / `targetSdk` 37.
Confirmed with the user while writing
`docs/superpowers/specs/2026-08-26-khata-wallet-design.md`; not delegated.

The home-screen widget is `RemoteViews`, not Glance (2026-09-03). Its face carries no
live data, so Glance would add a dependency and a second theming dialect to re-render
nothing — and it would not have supplied `KhataTheme` either, having its own
`ColorProviders`. The widget still follows the runtime tuner: `composeScheme` is a
plain function the provider calls directly. Rationale in
`docs/superpowers/specs/2026-09-03-khata-cash-widget-design.md` §2.

The floor was raised from 30 to 33 for the glass design system: API 31 adds
`RenderEffect`, without which `Modifier.blur` is a silent no-op, and 33 adds AGSL
`RuntimeShader` for procedural grain rather than bitmap fakes. Confirmed with the user
on 2026-09-02. The cost is nil — one user, one Pixel 6a on Android 17, sideloaded —
and it removes every capability check and fallback branch. Android 11 and 12 support
is explicitly no longer a requirement. Rationale in
`docs/superpowers/specs/2026-08-28-khata-petrol-design.md` §10.

## Users

A single user — the app's author. Personal use on one device (Pixel 6a, Android 17),
sideloaded, not distributed. No second audience exists. A future Play Store release
is an acknowledged possibility the architecture deliberately keeps open, but no user
has been defined for it and none should be invented.

## Product Purpose

Khata consolidates personal record-keeping that currently lives nowhere queryable.
Phase 1 solves money.

The specific problem: digital payments in Bangladesh already produce a complete
transaction record in the form of SMS, but that record cannot be summed,
categorized, charted, or searched. Meanwhile manual expense tracking fails for a
reliable reason — entry is a chore, and the chore gets abandoned within weeks.

Khata ingests those SMS automatically, so the user is never asked to record a
digital transaction. Cash, which SMS cannot see, is captured through a home-screen
widget fast enough to become habit. Success is being able to answer "where did my
money go?" without having done any bookkeeping to earn the answer.

## Positioning

Automatic capture from a message feed the user already receives, rather than manual
entry. Three mechanisms a neighboring product could not truthfully copy without
building the same thing:

- **Rules first, AI as fallback.** Templated bank and MFS messages are parsed
  offline and free. The AI is consulted only for a format no rule matches, and its
  job is to *write a new permanent rule* — so the same format is never paid for
  twice, and data egress trends to zero.
- **Parsing is idempotent and re-runnable.** Raw messages are retained forever, so a
  rule added months later retroactively repairs every message that previously failed
  or parsed wrong.
- **Correctness by arithmetic.** Bank messages report a running balance. Khata
  compares it against its own computed balance and surfaces the gap, instead of
  relying on the user to notice an error.

Beyond money, Khata is designed as a modular personal manager over a shared spine —
places, media, tags, search — that later modules reuse rather than reinvent.

## Operating Context

Bangladesh. Currency is BDT, displayed with ৳. Financial institutions: bKash (mobile
financial service) and Eastern Bank Limited (account and debit card), plus a cash
wallet. All day, month, and period boundaries are computed in `Asia/Dhaka`.

**Four confirmed usage situations, none dominant:**

1. A several-times-daily glance at balance and recent activity.
2. A sitting-down weekly or monthly review — categorizing, correcting, comparing.
3. An immediate check right after a purchase, to confirm it landed or fix it.
4. A targeted hunt for one specific past transaction.

Because no single situation dominates, the ledger cannot be tuned for only one of
them. Glanceable summary, scannable history, reachable most-recent item, and
prominent search all have to coexist.

**Physical conditions span the full range, all confirmed:** one-handed thumb-only
operation; bright outdoor Dhaka daylight; use at night in the dark; and two-handed
indoor use.

## Capabilities and Constraints

- Automatic transaction capture from bKash and EBL SMS. Ingestion sits behind a
  `MessageSource` interface with an SMS implementation and a notification-listener
  implementation, because `READ_SMS` is a Google-restricted permission that cannot
  ship publicly for this use case.
- Cash entry through a home-screen widget, targeted at three taps.
- Ledger, category insights, monthly budgets, and net worth over time.
- Every parsed transaction is recorded immediately with no review gate — the user's
  explicit choice. Mitigated by confidence markers, balance reconciliation, and
  re-runnable parsing rather than by a confirmation step.
- Money is always `Long` paisa. Never floating point. BDT only; no multi-currency.
- Local-only storage with a sync-ready schema (uuid, timestamps, soft deletes).
  No accounts required; optional Google sign-in enables Drive backup.
- Fully functional offline. Network and AI are enhancements, never dependencies.
- **Language:** English interface. Bengali script must render correctly wherever it
  appears — merchant names, user notes, text derived from SMS. Not bilingual; there
  is no language toggle and no translation layer.
  **One exception (user decision, 2026-08-28):** the brand lockup on the home screen is
  Bengali — the wordmark খাতা and the tagline সব হিসাব, এক খাতায়. Two fixed strings,
  never localised, never extended. Every other string in the interface is English.
- **Module order, settled three deep (2026-09-05):** restaurants, then car servicing, then
  movies and TV. Notes, deadlines, and a lending ledger remain candidates with no order
  assigned. The wallet's D11 deferral is spent; the next choice is made after these ship.

## Brand Commitments

**Name:** Khata (খাতা). The Bengali word for a ledger or account book — traditionally
the handwritten book a shopkeeper keeps of credit extended and payments received.
The name is binding; it is the one piece of identity that already exists.

No logo, wordmark, icon, or brand asset exists. No voice or personality has been
established. Neither may be treated as pre-existing.

**Pinned visual constraint (user-stated, binding, revised 2026-08-28):** dark-first
glassmorphism — a petrol card on a verdigris field over a teal-black ground, with a
pale-aqua accent and a serif on the numerals. Light mode is deferred, not cancelled.

The previous pin (a simple interface, warm editorial hues, orange accents) was reopened
by the user and replaced after they reviewed worked alternatives. It is superseded, not
merely unfulfilled. See `docs/superpowers/specs/2026-08-28-khata-petrol-design.md`.

**Wordmark:** খাতা, set in Li Swarnali Okkhor. Never transliterated — no Latin "Khata"
appears anywhere in the interface. It is shown on the home screen and no other.

**Tagline:** সব হিসাব, এক খাতায় — "all accounts, in one book." Native-speaker reviewed.

## Evidence on Hand

Nothing yet. No logo, icon, illustration, photography, screenshot, or brand asset
exists in the repository. No product copy has been written. Real bKash and EBL SMS
samples are to be supplied by the user and are not present.

Future work must not fabricate testimonials, users, benchmarks, screenshots, or
brand assets, and must not present the app as having any user other than its author.

## Product Principles

1. **Capture is automatic.** Any step that asks the user to do bookkeeping is a step
   that eventually gets skipped, and then the data is wrong.
2. **Correctness is checkable, not vigilant.** Because nothing gates a recorded
   transaction, the app proves itself by reconciling against reported balances.
3. **Nothing is ever lost.** Raw messages are retained, deletes are soft, parsing is
   repeatable. Mistakes are repairable in bulk rather than one at a time.
4. **The network is an enhancement.** The app is fully usable with no connection, no
   account, and the AI switched off.
5. **One spine, many modules.** Shared places, media, tags, and search mean later
   modules are UI work rather than new applications.

## Accessibility & Inclusion

Product-specific requirements, all confirmed by the user rather than assumed:

- **One-handed thumb operation.** Controls used routinely must fall within the
  bottom third of the screen. Primary actions cannot live in top corners.
- **Bright-sunlight legibility.** Contrast must exceed the WCAG AA minimum for any
  text that carries meaning, not merely meet it. Low-contrast grey is unavailable
  for anything the user needs to read.
- **Night use.** The dark palette is a primary design target, designed on its own
  terms — not derived by inverting the light one.
- **Bengali script must render correctly** at every size the interface uses, in
  merchant names, notes, and SMS-derived text.
