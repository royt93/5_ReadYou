# EXC-02: AI Podcast Studio (Single-Article Edition) — Design Spec

**Status:** Draft, awaiting user review  
**Date:** 2026-11-08  
**Author:** Claude Code + User  
**Target Branch:** `dev` (no push, no worktree)  
**Epic:** 05. Killer Features (Market Differentiators) — EXC-02  
**Device Target (if smoke required):** Tecno BG6 `118743744X002560` (locked for this session).

---

## 1. Context and Problem

`TtsManager.kt` reads text linearly in a single voice, which can become monotonous for long reads. EXC-02 turns articles into a lively, 2-host spoken dialogue between two co-hosts (Alex & Sam), inspired by NotebookLM-style audio overviews.

A related multi-article, budget-based morning bulletin already exists in the repo (`CommuteCast`, epic 14). However, the app previously had no way for users to turn a **specific, currently-open article** into a dual-voice discussion on demand, and `FlowPage` currently lacks a multi-selection mechanism.

## 2. Scope Decisions (Owner-Approved)

1. **Single-article first:** Instead of refactoring `FlowPage` to add a multi-select mode (which risks list-scrolling regressions and touch conflicts), EXC-02 attaches cleanly to `ReadingPage` via its existing `TopBar` overflow menu (`🎙️ Podcast Studio`).
2. **Gemini generation with privacy boundaries:**
   - Input sent to Gemini: strictly `title` plus `shortDescription` truncated to at most 250 characters.
   - Never sent: `fullContent`, article URL, feed name/URL, account identifiers, or user reading history.
   - Explicit consent: Before sending data to Gemini for the first time, a dialog asks for permission. If the user declines, or if offline / no API key is configured, the feature uses an on-device heuristic dialogue generator. The choice is stored in DataStore and can be changed in settings.
3. **Dedicated single-article prompt:** Uses a focused prompt designed for deep-diving into one specific news item (not the morning multi-headline bulletin prompt used by `CommuteCast`).
4. **Shared audio engine without persistence:**
   - Reuses `CommuteAudioPlayer` (which already handles true dual-voice selection when available, simulated pitch-shifted voices when only one voice exists, ducking, and audio focus).
   - The generated single-article episode is kept **in-memory only**; it does NOT call `CommuteEpisodeStore.save()`. This prevents overwriting the user's stored morning CommuteCast bulletin.
   - Known limitation: If the Android process is reclaimed by the OS during playback, the notification / lockscreen media session may resume the older stored `CommuteCast` episode rather than this ephemeral single-article podcast. This limitation is accepted and will be stated in the DONE report.
5. **Playback conflict confirmation:** If `CommuteAudioPlayer` is currently playing when the user launches Podcast Studio, the app prompts: "Stop current playback to play this podcast?" before replacing the episode. RSS podcast playback uses a separate `ExoPodcastEngine` with its own audio focus handling; `CommuteAudioPlayer` requests `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`, so RSS audio may only be ducked, not stopped. Podcast Studio does not control the RSS player, and implementation must verify the audible behavior on device rather than assume it pauses.
6. **Animated playback waveform:** Canvas-drawn dynamic bars that animate when audio is actively playing, pause when playback pauses, and change accent color to reflect which host (Alex vs. Sam) is currently speaking. Honest UI label: It is explicitly documented as a playback-state visualizer, not an acoustic PCM audio analyzer (since Android TTS does not expose a raw audio stream for analysis).

7. **Inherited player side effects (stated, not hidden):** `CommuteAudioPlayer.handleUtteranceStart` unconditionally starts the lofi `ambientLoop` and `CommuteMediaSessionService`. Shared-player playback therefore includes the ambient loop and a media notification. Podcast Studio keeps this behavior unchanged; adding a switch to disable ambient audio for single-article playback is out of scope unless a failing test or smoke shows it is unacceptable. Release notes and the DONE report must mention the ambient loop and notification.

## 3. Architecture and Data Flow

```
[ReadingPage: TopBar menu -> "🎙️ Podcast Studio"]
       │
       ▼ (1) Consent check (DataStore: PodcastStudioGeminiConsent)
       ├─ Not yet decided ──► [Consent Dialog: explain data sent]
       │                          │
       │                          ├─ User agrees: flag = true
       │                          └─ User declines: flag = false
       ▼ (2) Conflict check (audioPlayer.playerState.value.isPlaying)
       ├─ Currently playing? ──► [Conflict Dialog: confirm replacement]
       │
       ▼ (3)
[PodcastStudioViewModel]
       │
       ├─ (4) PodcastStudioScriptService.generate(article, useGemini: Boolean)
       │        ├─ If useGemini && hasKey ──► AiRequestGateway.execute(singleArticlePrompt)
       │        │                              └─ Parse JSON -> CommuteEpisode(source = GEMINI)
       │        └─ Fallback / Offline     ──► generateHeuristicDialogue(article)
       │                                       └─ CommuteEpisode(source = HEURISTIC)
       │
       ├─ (5) audioPlayer.playEpisode(episode)  [in-memory only, no episodeStore.save()]
       │
       ▼ (6)
[PodcastStudioSheet (ModalBottomSheet)]
       ├─ Article Title & Host Badges (Alex & Sam)
       ├─ Dialogue list with active line highlighted
       ├─ Canvas animated waveform (accent color matches current speaker)
       ├─ Honest voice badge: "Two distinct voices" vs "Single voice pitch-shifted"
       ├─ Honest script badge: "AI generated" vs "Offline heuristic"
       └─ Play/Pause, Skip dialogue, Speed controls
```

## 4. Components and File Structure

### New Files
- `app/src/main/java/com/mckimquyen/reader/domain/model/podcast/PodcastStudioModels.kt`:
  - `enum class PodcastScriptSource { GEMINI, HEURISTIC }`
  - `data class PodcastStudioEpisode(val episode: CommuteEpisode, val source: PodcastScriptSource)`
- `app/src/main/java/com/mckimquyen/reader/domain/sv/PodcastStudioScriptService.kt`:
  - Single-article dialogue prompt construction (title + shortDescription <= 250 chars).
  - Routes Gemini calls via `AiRequestGateway`.
  - Fallback heuristic dialogue generator for offline / no-key / declined consent.
- `app/src/main/java/com/mckimquyen/reader/ui/page/home/read/podcaststudio/PodcastStudioViewModel.kt`:
  - Owns episode generation, consent state, player state binding, and conflict resolution.
- `app/src/main/java/com/mckimquyen/reader/ui/page/home/read/podcaststudio/PodcastStudioSheet.kt`:
  - Bottom sheet composable with dynamic Canvas waveform, active dialogue tracking, voice/script transparency badges.

### Modified Files
- `app/src/main/java/com/mckimquyen/reader/ui/ext/ExtDataStore.kt`:
  - Add `DataStoreKeys.PodcastStudioGeminiConsent` (nullable Boolean; null = undecided).
- `app/src/main/java/com/mckimquyen/reader/ui/page/home/read/TopBar.kt`:
  - Add "🎙️ Podcast Studio" item to the overflow menu, wired to `onPodcastStudio: () -> Unit`.
- `app/src/main/java/com/mckimquyen/reader/ui/page/home/read/ReadingPage.kt`:
  - Wire TopBar event to open `PodcastStudioSheet`.
- String resources across all 38 locales (`values/strings.xml`, `values-vi/`, and 36 other locale dirs):
  - Menu item, sheet title, consent prompt, conflict prompt, speaker labels, voice mode badges, script source badges.

## 5. Privacy, Security, and Edge Cases

- **Privacy:** Exactly one article's title + snippet (<= 250 chars) is sent per user request. Zero telemetry, zero tracking, zero background harvesting.
- **Corrupt / Hallucinated JSON:** If Gemini returns non-JSON or invalid schema, the parser catches the error and silently falls back to the heuristic script with an honest "Offline heuristic" badge.
- **Short or Empty Content:** If an article has an empty title and description, the generator immediately uses an empty-state dialogue without contacting Gemini.
- **Device Voice Constraints:** If the device has only one voice for the target language, `CommuteVoiceSelector` falls back to `SIMULATED` mode (pitch 0.92f vs 1.28f). The sheet transparently displays "Single voice with pitch shift" instead of falsely marketing it as two separate voices.
- **Dismissing the Sheet:** Tapping outside or dragging down the bottom sheet pauses audio rather than unbinding/destroying the service, so the user does not leave audio unexpectedly blaring in the background.

## 6. Testing Strategy

1. **Unit Tests (JVM / Robolectric):**
   - Prompt sanitization: verifies <= 250 char truncation, no sensitive columns sent.
   - Dialogue JSON parser: handles malformed JSON, markdown ticks, empty candidates.
   - Heuristic dialogue generator: generates correct two-speaker structure across EN/VI.
   - ViewModel: verifies consent gating, conflict resolution dialog trigger, and asserts `CommuteEpisodeStore.save()` is NEVER invoked.
   - StringResourceParityTest: ensures parity across all 38 locales.
2. **Compose UI Tests (Robolectric):**
   - Sheet displays correct speaker badges and dialogue text.
   - Waveform stops animating when playerState is paused.
   - Consent dialog shows and remembers choice.
3. **On-Device Smoke (Tecno BG6 `118743744X002560`):**
   - Open an article in ReadingPage.
   - Tap menu -> Podcast Studio.
   - Verify consent prompt appears on first use.
   - Verify playback begins with honest voice badge and animated waveform.
   - Verify pausing stops waveform and closing sheet pauses playback.
   - Verify Room and DataStore integrity via backup/restore before and after smoke.
