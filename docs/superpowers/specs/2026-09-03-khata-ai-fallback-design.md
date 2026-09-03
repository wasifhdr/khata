# AI Fallback — Design

**Date:** 2026-09-03
**Status:** Design decided. Implementation plan not yet written.
**Extends:** `2026-08-26-khata-wallet-design.md` §13 (AI layer, privacy, Play Store path)
**Builds on:** `2026-09-02-plan2a-sms-ingestion-design.md` (the rule engine) and `2026-09-03-khata-background-work-design.md` (the worker).

Plan 2b deferred this as "Plan 3". This is it.

---

## 1. The AI writes a rule, never a transaction

An unmatched message is sent to Gemini, which returns **a proposed parsing rule**. That
rule is stored beside the built-in and hand-written ones, and `ReparseUseCase` runs.

Everything good about this design follows from that one choice:

- **One call fixes a format forever.** Six years of an unrecognised bKash shape cost one
  request, not thousands. Egress trends to zero within weeks, which is the claim
  `PRODUCT.md` makes and this is the mechanism that makes it true.
- **A wrong answer is repairable in bulk.** A bad rule is visible in the rule editor,
  editable, disableable, and re-runnable — the same path a bad hand-written rule has. An
  AI that wrote transactions directly would scatter plausible wrong numbers through the
  ledger with nothing to find them by.
- **Nothing new is trusted.** The parsed result comes from the same `RuleEngine` that
  parses everything else. The AI's output is a *pattern*, and patterns are checkable.

---

## 2. Automatic, and applied immediately

A miss fires a request on its own; the resulting rule is enabled on arrival.

This follows D5 — everything is recorded with no review gate — and for the same reason:
a gate is a chore, and a chore gets skipped until the data is wrong. The mitigations are
the ones D5 already relies on: the rule is visible, the parse is re-runnable, and
reconciliation checks the arithmetic against the bank's own stated balance.

**AI rules sit below both built-in and user rules in priority.** A hand-written rule
always wins over a guessed one. `origin` is `"AI"`, beside the existing `"BUILTIN"` and
`"USER"`, so the rule editor can show where a rule came from.

---

## 3. What leaves the device

Only the body of a message that matched **no** rule, and only once per format.

**The body is sent as it arrived. There is no redaction** (user decision, 2026-09-03,
made after the trade-off below was put to them).

The privacy cost is real and is accepted knowingly: on the AI Studio free tier prompts
may be retained and human-reviewed, so EBL account tails and any phone numbers in an
unmatched message reach Google. This is a single-user personal build, the key is the
user's own, and the messages are their own.

The engineering argument ran the other way, which is why the decision is not merely a
shortcut. Replacing a mask like `115***352` with a placeholder means the model sees the
placeholder and may write a pattern matching *that* — which then matches nothing in the
real message. §6's drafter would discard the rule for not matching, the worker would
retry, and the feature would fail permanently with no visible reason. Substituting
different digits of the same shape would have avoided both problems; the user chose to
send the body as-is instead.

**The kill switch is the mitigation that remains, and it is a real one.** With no key
set nothing is sent at all: an unmatched message queues exactly as it does today and is
taught by hand in the rule editor. Nothing degrades; the work is just yours.

---

## 4. The key lives in Settings, and never in the repository

Entered and editable on the Settings screen, stored in **app-private DataStore**.

This amends spec §13.2, which said `EncryptedSharedPreferences`. That needs
`androidx.security:security-crypto`, whose last release is an alpha and which Google no
longer actively develops — a dependency that will age, bought for very little. App-private
storage is already sandboxed from every other app by the OS, and the realistic threat is
a stolen phone, which full-disk encryption answers and app-level encryption does not
meaningfully add to for a single-user sideloaded build.

**It is never in source and never committed**, which was always the load-bearing half of
§13.2.

The Settings field shows the key masked once set, with a Clear action. Clearing the key
is equivalent to switching AI off — there is no separate toggle to disagree with it.

---

## 5. No new dependencies

One POST to one endpoint, from a worker that already provides retry and backoff.
`HttpURLConnection` and `org.json` are in the JDK and the Android platform respectively.

An HTTP client earns its place in an app that talks to many endpoints; this one talks to
exactly one, rarely. The official Kotlin SDK would also put the request-building — and
so what is actually sent (§3) — behind a client that is not ours.

**Gemini Flash with structured output.** The request pins a `responseMimeType` of
`application/json` and a response schema, because this is field extraction and a prose
reply would need parsing of its own.

---

## 6. Where it runs

`IngestionWorker` already has a `mode` input. A fourth mode, `TEACH`, takes a raw
message id: request, write the rule, then reparse.

It is enqueued when the pipeline classifies a message `UNMATCHED` and a key is set. Not
under the whole-inbox unique name — a teach is small, and must not be dropped for
colliding with a backfill.

**Failure retries.** A network error, a rate limit, or a malformed response returns
`Result.retry()`; WorkManager's backoff handles the rest, and the message simply stays
unmatched until it succeeds. A response that parses but proposes a rule matching nothing
is discarded rather than stored — a rule that matches no message is not a rule.

**Backfill does not teach.** A first backfill over years of messages could otherwise fire
hundreds of requests before the user has seen a single screen. Unmatched messages from a
backfill are taught when they next arrive, or by hand.

---

## 7. Testing

- **The response parser**: a well-formed reply produces a rule; a malformed one, a
  refusal, and an empty candidate list each produce nothing rather than a broken rule.
- **A proposed rule that matches nothing is discarded.**
- **AI rules rank below user and built-in rules**, asserted through `RuleEngine`.
- **The worker's TEACH mode** end to end against a fake responder: unmatched message in,
  rule stored and message parsed out.
- **Not unit-tested:** the live API call. Verified once by hand against a real key.

---

## 8. Out of scope

- Merchant-to-category suggestion (§13.1's second job). It touches the merchant memory
  rather than the rule engine, and fires far more often than a rule miss — so the
  egress-trends-to-zero property does not hold for it. Its own design.
- Proxying the key for a public build (§13.3). Out of scope for a sideloaded app.
- Any AI involvement in categorising, reconciling, or writing transactions directly.
- Streaming responses. One small JSON object needs no stream.
