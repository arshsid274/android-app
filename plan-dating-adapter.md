# plan-dating-adapter.md — Tinder / Hinge Input Adapter

**Add-on to `plan.md`.** Adds dating apps as an input source. Everything
downstream — Room logging, per-service history, guardrails, UI, security — is
**reused unchanged** from `plan.md`. Only the *input* and *output* adapters are
new, because these apps expose no notification reply path.

**Default mode: SUGGEST** (read chat → surface 2–3 drafts → you tap to send).
**Auto-send: documented, gated, and flagged fragile.** See §6 honesty box.

---

## 0. Why a separate adapter

| | WhatsApp path (plan.md) | Tinder / Hinge path (this file) |
|---|---|---|
| Read messages | Notification `MessagingStyle` | **AccessibilityService** scrape of view tree |
| Send | `RemoteInput` (protocol-safe) | **AccessibilityService** set-text + tap Send |
| Detectable by app | No | **Yes** (they query enabled a11y services) |
| Stability | High | **Low** (breaks on app UI updates) |
| Context | Given by API | Must scroll + scrape |

The clean notification route does **not** exist here — no `RemoteInput`, message
text usually absent from the notification. Accessibility is the only on-device
option, so the adapter is isolated behind an interface and the failure modes are
contained.

---

## 1. Interface (so both paths share downstream code)

```kotlin
interface MessageSource {
    fun observe(): Flow<IncomingMessage>   // packageName, contactKey, text, ts
}
interface ReplySink {
    suspend fun deliver(threadKey: String, text: String): DeliveryResult
    // WhatsApp: RemoteInput.send()   Dating: a11y set-text + tap
}
```

`NotificationMessageSource` (plan.md) and `AccessibilityMessageSource` (here)
both implement `MessageSource`. Room, LLM, policy gate, UI don't know or care
which one produced the message.

---

## 2. The loop engineering (this is the core of your request)

There are **three nested loops**, each bounded, each with a stop-and-diagnose
rule. Blind "retry until it works" is explicitly rejected — on a detectable,
brittle surface it just gets you banned faster.

### Loop A — Build loop (per milestone, from plan.md §0)
```
attempt = 0 ; MAX = 3
while milestone not verified:
    attempt++
    build / fix
    run verification gate
    if pass: commit, next milestone            # exit
    else:
        record observed-vs-expected
        if attempt >= MAX:
            STOP → write BLOCKERS.md → escalate # hard exit
            break
```

### Loop B — Runtime read loop (the a11y scrape, per open chat)
This is where "loop until required result is received" actually lives — but
**bounded by attempts AND by a freshness check**, not spinning.
```
POLL_INTERVAL = jittered 800–1500ms   # NOT fixed — fixed timing = bot signature
STALE_CAP     = 8 empty polls
reads = 0
loop:
    tree = accessibilitySnapshot()
    msgs = parseChatNodes(tree)
    if msgs == lastSeen:                 # no new content
        reads++
        if reads >= STALE_CAP: PAUSE, wait for window-state-change event  # exit-ish
        continue
    reads = 0
    if newInboundMessage(msgs):
        emit(msg)                        # required result received -> downstream
    sleep(jitter(POLL_INTERVAL))
```
Key points:
- **Event-driven, not busy-wait.** Prefer `onAccessibilityEvent`
  (`TYPE_WINDOW_CONTENT_CHANGED`) to trigger a snapshot; the poll is only a
  fallback. Continuous polling drains battery and reads as automation.
- **Jitter is mandatory.** Regular intervals are the single easiest bot tell.
- **STALE_CAP** stops the loop from running forever on an idle chat.

### Loop C — Send-verify loop (auto-send only; suggest mode skips this)
Sending via accessibility can silently fail (node moved, keyboard not focused,
Send button re-labelled). So each send is a **bounded verify-retry**:
```
SEND_MAX = 2
for attempt in 1..SEND_MAX:
    setText(composeNode, reply)
    if composeText() != reply:  continue          # set failed, retry once
    tap(sendNode)
    if messageAppearsInThread(reply, timeout=2s):
        log AUTO_SENT ; break                      # required result received -> exit
    else:
        log SEND_RETRY
if not verified after SEND_MAX:
    log SEND_FAILED ; surface to you ; DO NOT keep retrying
```
Never loop a send more than twice. Repeated failed sends against a moved UI is
exactly the burst pattern that trips anti-bot.

---

## 3. Milestones (Loop A applies to each)

### D1 — AccessibilityService skeleton
**Goal:** Service enabled; receives events from `com.tinder` / `co.hinge.app`.
**Gate:** Opening a chat logs a `TYPE_WINDOW_STATE_CHANGED` with the right package.
**Fail modes:** missing `canRetrieveWindowContent="true"` in config;
`accessibilityEventTypes` too narrow.

### D2 — Chat scrape → text
**Goal:** From an open match, extract ordered messages + who sent each.
**Gate:** Open a real chat; log reproduces the visible messages in order with
correct inbound/outbound tagging.
**How:** walk `rootInActiveWindow`, match message bubbles by view-id/class,
infer direction by node position/alignment. **Expect this to break on app
updates** — isolate the selectors in one `SelectorMap` file so a UI change is a
one-file fix (feeds BLOCKERS if a version defeats it).

### D3 — Normalize into the shared pipeline
**Goal:** Scraped messages become `IncomingMessage` on the same `MessageSource`
flow as WhatsApp.
**Gate:** A dating message lands in Room with correct `packageName`,
`contactKey`, `INCOMING` — using the **unchanged** plan.md schema.

### D4 — SUGGEST mode (build and stop here first)
**Goal:** 2–3 drafts surface in your keyboard/overlay for the open match; you tap.
**Gate:** New inbound → drafts appear < 3s → tap inserts into compose field;
`SUGGESTED` logged. Nothing auto-sends.
**This is the stable product.** No send automation = no fixed-timing signature =
minimal detection surface.

### D5 — Guardrails (reuse plan.md M7 + dating-specific)
**Gate — each verified:**
- Reuse: rate-limit, loop-breaker, allowlist opt-in, kill switch.
- **Add human-cadence gate:** no auto-action faster than a human could
  plausibly read+type; enforce randomized delay floor.
- **Add "a11y-detected" self-check:** if the app appears to be probing
  accessibility state, auto-drop to SUGGEST mode.

### D6 — AUTO-SEND (optional, fragile — Loop C)
**Goal:** For allowlisted matches only, send via a11y with send-verify loop.
**Gate:** Allowlist one match; inbound → verified send → `AUTO_SENT`. Non-listed
→ `BLOCKED_NOT_ALLOWLISTED`. Forced send-failure → `SEND_FAILED`, no runaway retry.
**Status:** ship-at-your-own-risk. See §6.

---

## 4. SelectorMap (the brittleness firewall)

All app-version-specific node identifiers live in ONE file:
```
SelectorMap {
  tinder = { chatContainerId, bubbleInId, bubbleOutId, composeId, sendId }
  hinge  = { ... }
}
```
When an update breaks scraping (D2 gate fails), Loop A caps at 3 attempts →
you update SelectorMap → re-verify. If a version can't be mapped at all (e.g.
Compose-rendered nodes with no stable ids), that's a **WONT-FIX-THIS-VERSION**
BLOCKERS entry, not an infinite loop.

---

## 5. Per-milestone exit / stop conditions (summary)

| Loop | Runs until | Hard stop |
|------|-----------|-----------|
| A (build) | gate passes | 3 attempts → BLOCKERS + escalate |
| B (read) | new message emitted | STALE_CAP empty polls → pause, wait for event |
| C (send) | reply verified in thread | 2 attempts → SEND_FAILED, surface, stop |

No loop in this system runs unbounded. "Required result not received" always
resolves to either *emit/commit* or *stop-and-log* — never *retry forever*.

---

## 6. Honesty box — read before building D6

Auto-**send** on Tinder/Hinge is buildable and will work for a while. It is also:

- **Detectable.** Match Group apps enumerate enabled accessibility services and
  watch for automation/timing regularity. This is among their most-tuned systems.
- **Ban-prone.** Enforcement is device- + payment-fingerprinted, so a fresh
  account on the same phone commonly dies too.
- **Brittle.** Every app update can break D2/D6 selectors.

The engineering above (jitter, human-cadence floor, send-verify cap, auto-drop
on detection) reduces but does **not** remove that risk. The stable, low-risk
product is **D4 SUGGEST mode** — same reading, same logging, same drafts, you
tap send. I'd run D1–D5, live on it, and treat D6 as optional and disposable.

Not covered anywhere in this plan: adding/messaging strangers you haven't
matched with. That's a different thing and I'm not building it.

---

## 7. Build order

D1 → D2 → D3 → **D4 (stop, use it)** → D5 → (optional) D6.

Reused from plan.md with zero changes: Room schema (M3), LLM call (M4), UI (M8),
security/retention (M9). This adapter only adds D1–D3 (input) and D6 (output).
