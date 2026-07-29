# Cue

On-device reply assistant. Reads your incoming messages (WhatsApp via notifications,
Tinder/Hinge via an accessibility scrape), stores them per contact, and surfaces
2–3 AI reply drafts you tap to send. Default mode is **SUGGEST**; auto-send is
optional, gated, and fragile.

Design docs: [`plan-dating-adapter.md`](plan-dating-adapter.md) (architecture, loops,
honesty box) and [`ui-need.md`](ui-need.md) (screen-by-screen UI spec).

## Status

Backend + pipeline are implemented. The UI is **placeholder** — plain Compose
screens that exist so the app compiles and the pipeline is testable end to end.
Replace them with the real design; build against the `CueViewModel` contract.

## Build

This machine has no JDK/SDK, so build in **Android Studio** (Ladybug or newer):

1. Open the project folder in Android Studio. It bundles a JDK and will prompt to
   install the required Android SDK (compileSdk 35) and **complete the Gradle
   wrapper** on first sync. (If you have Gradle on the CLI, `gradle wrapper` also
   generates the missing `gradle/wrapper/gradle-wrapper.jar` + `gradlew` scripts.)
2. Let Gradle sync, then Run on a device/emulator (minSdk 26).
3. Grant permissions from the Home screen: notification access, accessibility,
   overlay ("draw over other apps"), and battery-optimization exemption.
4. Set your Anthropic API key in settings (currently via DataStore — the placeholder
   Settings screen shows status only; add a key field or seed it in code to test).
5. Tap **Start Cue service**, then open a chat in WhatsApp/Tinder/Hinge.

## Architecture

```
MessageSource (interface)                 ReplySink (interface)
 ├─ NotificationMessageSource  (WhatsApp)  ├─ RemoteInputReplySink        (WhatsApp, protocol-safe)
 └─ AccessibilityMessageSource (Tinder/Hinge) └─ AccessibilityReplySink   (dating, Loop C send-verify)

OrchestratorService (foreground)
  merge(sources) → record (Room) → DraftGenerator (LLM) → overlay (SUGGEST)
                                                        → GuardrailEngine → sink (AUTO-SEND)
```

- **domain/** — models + `MessageSource`/`ReplySink` interfaces (source-agnostic).
- **data/** — Room (messages, conversations, activity log) + `SettingsRepository` (DataStore).
- **llm/** — `LlmClient` (Anthropic impl) + `DraftGenerator`.
- **policy/** — `GuardrailEngine`: kill switch, mode, allowlist, rate limit, loop-breaker, human-cadence.
- **source/accessibility/** — `CueAccessibilityService`, `ChatScraper`, and **`SelectorMap`**
  (the brittleness firewall — all app-version-specific view-ids live in one file).
- **source/notification/** — `CueNotificationListenerService` (MessagingStyle + RemoteInput capture).
- **sink/** — the two `ReplySink` implementations.
- **service/** — `OrchestratorService` + `OverlayController` (floating draft card).
- **diagnostics/** — live SelectorMap health for the Diagnostics screen.
- **di/** — `AppContainer` (hand-rolled DI, no Hilt).
- **ui/** — placeholder Compose screens + nav.

## The loops (from the plan)

- **Loop B (read):** `CueAccessibilityService` is event-driven (`WINDOW_*_CHANGED`) with a
  jittered, STALE_CAP-bounded poll fallback. Fixed timing is a bot tell, so the poll jitters.
- **Loop C (send):** `AccessibilityReplySink` runs set-text → verify → tap → verify-in-thread,
  bounded by `sendVerifyMax` (default 2). Never retries a send past the cap.
- **Guardrails:** every auto-send passes `GuardrailEngine.evaluateAutoSend` (kill/mode/allowlist/
  rate/loop-breaker) and a randomized human-cadence hold before acting.

## ⚠️ Honesty

The Tinder/Hinge path uses `AccessibilityService`, which those apps can detect, and
which breaks on their UI updates. The **`SelectorMap` view-ids are placeholders** and
must be verified against real APK layouts (Layout Inspector / `uiautomatorviewer`)
before D2's scrape gate can pass. Auto-send (D6) is ban-prone — SUGGEST mode is the
stable product. See the honesty box in `plan-dating-adapter.md` §6.
