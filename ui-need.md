# ui-need.md — Screen & UI Specification

**Companion to `plan-dating-adapter.md` (and the referenced `plan.md`).**
This document lists **every screen** the app needs to reach full functionality,
what each screen is *for*, the **UI components** on it, its **states** (empty /
loading / error / success), and how screens connect. Build it as a checklist:
each screen is one buildable unit.

**Product in one line:** an on-device assistant that reads your incoming messages
(WhatsApp via notifications, Tinder/Hinge via accessibility), stores them per
contact, and surfaces 2–3 AI reply drafts you tap to send. Default mode is
**SUGGEST**. Auto-send is optional, gated, and fragile.

**Assumed stack:** Kotlin + Jetpack Compose + Material 3, single-activity, Nav
Compose, Room, a floating overlay (`WindowManager` / `TYPE_APPLICATION_OVERLAY`)
for on-top drafts. Adjust names to your codebase.

---

## 0. Screen map (the whole app at a glance)

```
Launch
 └─ Splash / gate ───────────────► (perms OK?) ──► Home
                                   (perms missing?) ──► Onboarding

Onboarding (first run only)
 1. Welcome / what this does
 2. Notification access grant        (WhatsApp path)
 3. Accessibility service grant       (Tinder/Hinge path)
 4. Battery optimization exemption
 5. LLM / API key + model pick
 6. Mode choice (SUGGEST vs learn about AUTO-SEND)
 7. Done → Home

Main app (bottom nav: Home · Chats · Activity · Settings)
 A. Home / Dashboard
 B. Conversations list
 C. Conversation detail (thread + drafts)
 D. Draft Overlay (floating, over the other app — not a nav screen)
 E. Activity / audit log
 F. Settings (hub)
     F1. Reply mode & behavior
     F2. Per-service settings (WhatsApp / Tinder / Hinge)
     F3. LLM / model / prompt persona
     F4. Guardrails & safety
     F5. Allowlist manager
     F6. Privacy & data retention
     F7. Diagnostics / SelectorMap health
     F8. Blockers / status report
 G. Global Kill Switch (reachable everywhere)
```

Bottom navigation has **4 tabs**: Home, Chats, Activity, Settings. The Kill
Switch is a persistent element (app bar toggle + notification action), not a tab.

---

## 1. Splash / permission gate

**Purpose:** Decide where to send the user on launch. Not a marketing splash —
a router.

**UI:**
- App logo/name, centered, brief.
- Silent check: notification-listener enabled? accessibility service running?
  API key present?
- Routes to **Onboarding** if anything required is missing, else **Home**.

**States:** loading (checking) → route. No user input.

---

## 2. Onboarding (first-run wizard)

A stepper. Show progress (e.g. "Step 2 of 6"). Each step = one card with a title,
a plain-language explanation, and a single primary action button. Let the user
skip optional steps but clearly mark what breaks if skipped.

### 2.1 Welcome
- What the app does, in 2 sentences.
- Honesty note: "Reads your chats on-device to draft replies. Nothing sends
  without you unless you turn on auto-send later."
- Primary: **Get started**.

### 2.2 Notification access (WhatsApp source)
- Explain: needed to read incoming WhatsApp messages.
- Primary: **Grant notification access** → deep-links to the system
  Notification Listener settings screen.
- Live status chip: `Not granted` → `Granted ✓` (updates on return).

### 2.3 Accessibility service (Tinder/Hinge source)
- Explain honestly: needed to read dating-app chats; **detectable by those apps**;
  can break on app updates. (Pull this tone straight from the honesty box.)
- Primary: **Open Accessibility settings** → deep-link.
- Status chip. Mark this step **optional** ("skip if you only want WhatsApp").

### 2.4 Battery optimization
- Explain: prevents the OS killing the reader in the background.
- Primary: **Allow background running** → request ignore-battery-optimizations.
- Status chip.

### 2.5 LLM setup
- Fields: API key (masked), model picker (dropdown), optional endpoint/base-URL.
- **Test connection** button → spinner → ✓ / error with reason.
- Note where the key is stored (encrypted, on-device).

### 2.6 Mode choice
- Two large selectable cards:
  - **SUGGEST (recommended)** — drafts appear, you tap send. Low risk.
  - **AUTO-SEND (advanced)** — explain fragility, ban risk; requires allowlist
    later. Selecting it shows a warning and still defaults sends off until D6 is
    configured.
- Primary: **Finish setup** → Home.

**Onboarding states:** each step tracks granted/denied; a summary at the end lists
anything still missing with a "fix later in Settings" link.

---

## 3. A — Home / Dashboard

**Purpose:** At-a-glance system health + the last few things that happened.
This is the "is it working right now?" screen.

**UI, top to bottom:**
- **App bar:** app name; right side = **Kill Switch** toggle (big, obvious;
  red when off/killed) and a settings gear.
- **Status card (hero):** one line answer to "am I live?"
  - Reader: `Active` / `Paused` / `Killed`.
  - Sources: three status pills — WhatsApp, Tinder, Hinge — each
    `Connected ✓` / `Permission missing` / `Selectors broken` (tap → fix).
  - Current mode badge: `SUGGEST` or `AUTO-SEND (allowlist)`.
- **Guardrail meters row:** small readouts —
  - Replies this hour vs rate-limit (e.g. `4 / 20`).
  - Human-cadence floor: `On`.
  - A11y-detection self-check: `OK` / `Dropped to SUGGEST`.
- **Recent activity preview:** last 3–5 events (message received, draft shown,
  sent, blocked). "See all" → Activity tab.
- **Empty state:** if nothing yet — "Waiting for your first message. Open a chat
  in WhatsApp or Tinder to test." + a **Run self-test** button.

**States:** live / paused / killed / a source degraded (show a warning banner and
a one-tap route to the broken thing).

---

## 4. B — Conversations list (Chats tab)

**Purpose:** Every contact/match the app has seen, grouped so you can open a
thread and review history or drafts.

**UI:**
- **Filter/segment bar:** All · WhatsApp · Tinder · Hinge.
- **Search** field (by contact/match name).
- **List rows**, each:
  - Avatar or service icon.
  - Contact/match name (or `contactKey` fallback).
  - Last message snippet + timestamp.
  - Direction indicator (last was inbound/outbound).
  - Badges: `Allowlisted` (if auto-send eligible), unread-draft dot.
- Sort: most recent first.

**States:**
- Empty: "No conversations yet."
- Loading: skeleton rows.
- A row for a service with a broken selector shows a small ⚠ ("scraping may be
  stale for this app").

**Nav:** tap row → Conversation detail.

---

## 5. C — Conversation detail (thread + drafts)

**Purpose:** The core working screen. Shows the reconstructed thread and the
2–3 AI drafts for the latest inbound message.

**UI:**
- **App bar:** contact name, service icon, overflow menu (allowlist toggle,
  mute this thread, clear history for this thread, view raw log).
- **Message thread:** chat bubbles, inbound left / outbound right, timestamps,
  a subtle source tag. For dating apps, show a small note if the thread was
  reconstructed by scraping ("scraped — order may differ from app").
- **Draft panel (bottom sheet or pinned footer):**
  - Header: "Suggested replies" + a **regenerate** ↻ button.
  - **2–3 draft chips/cards**, each with the text and, on tap:
    - **Copy**, **Insert into app** (uses ReplySink), and in auto-send-eligible
      threads a **Send** button.
  - Tone/length controls (optional): chips like `Warmer` · `Shorter` · `Funnier`
    → regenerate with that steer.
  - Loading state: 3 shimmer placeholders while the LLM runs.
  - Error state: "Couldn't draft — retry" with the reason (no key, offline,
    rate-limited).
- **Mode indicator:** small line — "SUGGEST: nothing sends until you tap" or
  "AUTO-SEND ON for this match".

**States:**
- New inbound → drafts populate (< 3s target per D4 gate).
- No inbound yet → "Waiting for their reply" empty draft panel.
- Auto-send success → an inline `AUTO_SENT ✓` marker on the sent bubble.
- Send failure (Loop C exhausted) → `SEND_FAILED` banner: "Sent couldn't be
  verified — send manually." No auto-retry.

---

## 6. D — Draft overlay (floating, over Tinder/Hinge/WhatsApp)

**Purpose:** Because dating apps have no reply API, drafts must appear **on top of
the other app** so you can insert with one tap without switching apps.

**Not a nav destination** — it's a `WindowManager` overlay drawn by a foreground
service.

**UI:**
- A **compact floating bubble** (draggable, dismissible) that expands to a small
  card when a new inbound is detected.
- Expanded card:
  - 2–3 draft chips (tap = insert into the app's compose field via a11y).
  - ↻ regenerate, ✕ dismiss, and a "open full thread" that launches Conversation
    detail.
  - A tiny mode/rate indicator.
- Auto-collapses after inaction; never steals focus aggressively.

**States:** collapsed bubble · expanded with drafts · loading · error · hidden
(when killed or when a11y self-check dropped it).

**Design constraints:** small footprint, respects human-cadence (don't animate a
send instantly), and must be easy to turn off. Requires "draw over other apps"
permission — add a step/toggle for it (either in onboarding 2.3 area or Settings).

---

## 7. E — Activity / audit log (Activity tab)

**Purpose:** Honest, append-only record of what the system did. This is the trust
surface — every automated action is visible.

**UI:**
- **Filter chips:** event type — `RECEIVED` · `SUGGESTED` · `INSERTED` ·
  `AUTO_SENT` · `SEND_RETRY` · `SEND_FAILED` · `BLOCKED_NOT_ALLOWLISTED` ·
  `RATE_LIMITED` · `A11Y_DETECTED` · `KILLED`. Also filter by service/contact.
- **Timeline list**, newest first, each row: icon, event label, contact,
  service, timestamp, and expandable detail (the draft text, the reason for a
  block, retry count).
- **Export / clear** controls (respect retention settings).

**States:** empty ("no activity yet"), loading, filtered-empty.

---

## 8. F — Settings (hub)

A list that routes into sub-screens. Group them:

**Behavior**
- **F1 Reply mode & behavior**
- **F4 Guardrails & safety**
- **F5 Allowlist manager**

**Sources & model**
- **F2 Per-service settings**
- **F3 LLM / model / persona**

**System**
- **F6 Privacy & data retention**
- **F7 Diagnostics / SelectorMap health**
- **F8 Blockers / status report**

Plus a top-level **Kill Switch** and an "About / honesty" entry that surfaces the
§6 honesty box text verbatim.

### 8.1 F1 — Reply mode & behavior
- Radio: **SUGGEST** / **AUTO-SEND**. Choosing auto-send opens a confirm dialog
  citing the risks and forces allowlist setup.
- Draft count slider (2–3).
- Default tone/persona shortcut (links to F3).
- "Regenerate on every inbound" toggle vs "only when I open the thread".

### 8.2 F2 — Per-service settings
One expandable section per source: **WhatsApp**, **Tinder**, **Hinge**.
- Enable/disable this source.
- Permission status + fix button.
- For dating apps: poll interval range (the jittered 800–1500ms window),
  STALE_CAP value, and a read-only "last scrape success" timestamp.
- Link to that service's SelectorMap health (F7).

### 8.3 F3 — LLM / model / persona
- API key (masked, edit), model picker, endpoint.
- **Persona / system prompt** editor (multi-line) — "how should replies sound?"
  with preset personas (Warm, Witty, Direct, Minimal).
- Max draft length.
- **Test** button.

### 8.4 F4 — Guardrails & safety
- **Rate limit:** replies per hour/day (numeric).
- **Human-cadence floor:** minimum delay before any auto-action (slider, e.g.
  8–60s) — enforced randomized floor.
- **Loop-breaker:** stop after N consecutive automated exchanges without human
  input.
- **A11y-detection self-check:** toggle "auto-drop to SUGGEST if the app seems to
  probe accessibility." (Recommended ON.)
- **Send-verify cap:** fixed at 2 (show as read-only, per Loop C) with an
  explanation.

### 8.5 F5 — Allowlist manager
- Only meaningful when auto-send is on.
- Searchable list of contacts/matches; toggle per person to allow auto-send.
- Empty state explains: "Auto-send only fires for people you add here.
  Everyone else stays SUGGEST."
- Bulk "remove all" (safety).

### 8.6 F6 — Privacy & data retention
- Retention window for messages/logs (e.g. 7/30/90 days / forever).
- **Delete all data now** (destructive, confirm).
- Where data lives (on-device, encrypted) — informational.
- Toggle: store message text vs metadata-only.

### 8.7 F7 — Diagnostics / SelectorMap health
**Purpose:** surface the brittleness firewall. When a dating app updates and
scraping breaks, this is where you see it and fix it.
- Per service: current app version detected, selector set in use, last successful
  scrape, and a status: `OK` / `Degraded` / `Broken (WONT-FIX-THIS-VERSION)`.
- **Run scrape test** on the currently open chat → shows what it parsed.
- Link to update selectors (or note that a version can't be mapped).

### 8.8 F8 — Blockers / status report
- Renders the `BLOCKERS.md` concept as a screen: milestones that hit their
  3-attempt cap, unmapped app versions, and escalations.
- Each entry: what failed, observed-vs-expected, when, and suggested action.

---

## 9. G — Global kill switch

**Purpose:** Instantly stop all reading and sending.

**Reachable from:**
- Home app bar (primary).
- The persistent foreground-service **notification** (an action button).
- Settings top-level.

**Behavior:** one tap → all sources paused, overlay hidden, no drafts, no sends.
Confirm on re-enable. Visually unmistakable (red when killed). Logs a `KILLED`
event.

---

## 10. Cross-cutting UI requirements

- **Every automated action is visible and reversible-in-spirit:** nothing sends
  silently in SUGGEST; auto-send always writes an Activity entry.
- **Honesty surfaces stay in the product**, not just docs: the auto-send warning,
  the "scraped thread may be inaccurate" note, and the detection risk are shown
  where the user makes the choice.
- **Degraded states are first-class:** a broken selector, a missing permission, a
  rate-limit hit, or an a11y-detection drop each has a clear banner and a one-tap
  route to the fix. Never fail silently.
- **No fake progress / no infinite spinners:** loading states are bounded and map
  to the real loops (read poll, LLM call, send-verify). If something is retrying,
  show the attempt count; when it stops, say why.
- **Accessibility (ironic but required):** the app's own UI must be screen-reader
  friendly, large-tap-target, high-contrast, and respect dynamic type.
- **Theming:** Material 3, light/dark, dynamic color optional.

---

## 11. Build order for the UI (mirrors the milestones)

1. **Splash gate + Onboarding** (perms are the hard prerequisite — D1).
2. **Home/Dashboard** (see the service become "Connected").
3. **Conversations list + Conversation detail (read-only thread)** (D2/D3).
4. **Draft panel + Draft overlay** in SUGGEST mode (D4 — *stop here, ship this*).
5. **Activity log** (trust surface, pairs with D4).
6. **Settings F1–F3** then **F4 Guardrails + F5 Allowlist + G Kill switch** (D5).
7. **Auto-send UI bits** in Conversation detail + overlay, plus **F7/F8
   diagnostics** (D6 — optional, fragile).

Ship a usable product at step 4–5 (SUGGEST). Steps 6–7 harden and optionally add
auto-send.

---

## 12. Screen checklist (tick as you build)

- [x] Splash / permission gate (`CueApp` start-route gate)
- [x] Onboarding: Welcome
- [x] Onboarding: Notification access
- [x] Onboarding: Accessibility grant
- [x] Onboarding: Battery optimization (combined with overlay grant, step 4)
- [x] Onboarding: LLM setup
- [x] Onboarding: Mode choice
- [x] Home / Dashboard
- [x] Conversations list
- [x] Conversation detail (thread + drafts)
- [x] Draft overlay (floating)
- [x] Activity / audit log
- [x] Settings hub (single scrolling screen, not sub-routed yet)
- [x] F1 Reply mode & behavior (mode + confirm dialog; no draft-count slider yet)
- [x] F2 Per-service settings (enable toggles + permission status; no poll tuning yet)
- [x] F3 LLM / model / persona (model, key, persona editor, test)
- [x] F4 Guardrails & safety (values shown read-only + a11y auto-drop toggle)
- [ ] F5 Allowlist manager (per-thread toggle exists in Conversation detail; no dedicated screen)
- [x] F6 Privacy & data retention (retention picker + delete-all with confirm)
- [ ] F7 Diagnostics / SelectorMap health
- [ ] F8 Blockers / status report
- [x] Global kill switch (app bar + notification action)
