# ECHO-02 — Contrasting Coverage From Your Archive Design

- **Date:** 2026-11-01
- **Branch:** `dev`
- **Status:** Owner-approved design; implementation not started.
- **Device lock:** Google Pixel 7 Pro, serial `2B051FDH3006MU` only.
- **Builds on:** ECHO-01 (`ArticleSignal`, `HeadlineAnalyzer`, `EchoSignalSection`, `LocalHeadlineSignalsEnabled`), BOUNTY-01 (`BountyInvestigationRepository`, `ArticleDao.queryRelatedArticleListWithFeed`, `BountyQuerySanitizer`).

## 1. Goal and Honest Scope

The original ECHO-02 idea was an AI-written "steelman" box listing three core risks and rebuttal arguments with credible sources. The owner chose a narrower, honest version.

What this task does: at the end of the article, when the reader's own saved archive contains an article **from a different source**, on the **same topic**, whose headline carries **contrarian wording**, show a card "Other angles in your archive" listing those real articles. The reader taps one to open it.

What it does not do, and says so:
- No AI, no network, no API key, no default key (FIX-09 stays deferred), no ads, no purchases, no Room change (DB stays v20).
- It does not write arguments, does not cite sources it invented, and does not judge who is right. An AI model has no network access, so any "credible source" it printed could be fabricated, and a "rebuttal" box is easily read as fact. This feature only points to real articles the user already saved.

Honest limits, shown in the card and repeated here:
- A contrarian word in a headline ("warns", "but", "critics") does not mean the article actually rebuts anything, and an article that does rebut may use none of the listed words.
- Matching is lexical, not semantic: it misses the same topic written with different words, and it only works in English and Vietnamese.
- It finds only what the user already saved. With a small or single-source archive the card is usually absent, by design. (The development archive on the Pixel has one feed, BBC News, so on that device the card will normally not appear.)

## 2. Owner Decisions (AskUserQuestion, 2026-11-01)

1. **Scope:** find articles from other sources in the user's own archive; no AI.
2. **Contrast signal:** a different source plus contrarian wording in the headline.
3. **Same topic:** shared proper-noun or distinctive phrases extracted from the headline (not the broad Second Brain concepts).
4. **Choosing the phrase:** extract 1 to 3 phrases automatically and let the reader switch between them.
5. **Placement:** a card at the end of the reading content, shown only when there is at least one result.

## 3. Pure Logic (`domain/steelman/`, JVM-testable)

### 3.1 `TopicPhraseExtractor`
`extract(title: String, feedName: String): List<String>` returns at most `MAX_PHRASES = 3` phrases from the headline of the article being read:
- Runs of 1 to 3 consecutive capitalized tokens (proper names), excluding a sentence-initial single common word, weekday and month names (English and Vietnamese), and common stop words, and excluding any phrase that equals or contains the source (feed) name.
- Standalone numbers with at least 2 digits are not used (too ambiguous).
- Phrases shorter than `MIN_PHRASE_LENGTH = 4` characters are dropped.
- Order: longer phrases first, then earlier position, deduplicated case-insensitively.
- Headlines in other languages, or with no qualifying phrase, return an empty list.

### 3.2 `ContrarianCueLexicon`
`find(title: String): String?` returns the first contrarian cue found in a headline using whole-word or whole-phrase matching (never substring; "but" must not fire inside "butter"). English cues include: but, however, despite, although, critics, criticised/criticized, warns, warning, risk(s), backlash, controversy, disputes, denies, fails, slams, rejects, doubts. Vietnamese cues include: nhưng, tuy nhiên, bất chấp, chỉ trích, cảnh báo, rủi ro, tranh cãi, phản đối, bác bỏ, nghi ngờ, thất bại. The returned cue is shown to the user as the reason.

### 3.3 `ContrastingCoverageFinder`
`find(current: ArticleListItemWithFeed, phrase: String, candidates: List<ArticleListItemWithFeed>): List<ContrastingArticle>`:
- Keeps a candidate only when ALL hold: different `feedId` than the current article; a different article id; the phrase matches as a whole phrase in the candidate title or short description (reuse the whole-phrase matching rules of `BountyQuerySanitizer`/BOUNTY-02, never substring); and `ContrarianCueLexicon.find(candidate.title)` is non-null.
- Returns at most `MAX_RESULTS = 5`, newest first.
- Each `ContrastingArticle(articleId, title, feedName, date, cue)` carries only data taken from that article.

## 4. Data access

For each selected phrase the ViewModel reuses `ArticleDao.queryRelatedArticleListWithFeed(accountId, currentArticleId, escapedKeyword, limit)` through `BountyInvestigationRepository` (title and short description only, `ARTICLE_LIST_COLUMNS`, FIX-17; no article body is read) and passes the rows to `ContrastingCoverageFinder`. The candidate limit is `CANDIDATE_LIMIT = 40` so the cue filter has enough rows; the DAO query is unchanged.

## 5. State and ViewModel (`ReadingViewModel`)

`contrastingCoverage: StateFlow<ContrastingCoverageState>` with `Idle`, `Searching`, `None`, and `Ready(phrases, selectedPhrase, items)`. One observer on `(articleId, content)`, the same pattern as the ECHO-01 observer, cancels stale work when the article changes. Work runs on the default dispatcher plus the repository call on IO. The state is in memory only. `selectContrastPhrase(phrase)` re-runs the search for that phrase. A failed lookup yields `None` and never crashes the reader. An optional last constructor parameter keeps existing `ReadingViewModel` tests compiling.

## 6. UI

`ContrastingCoverageCard`, placed in `Content.kt` after the article body items and before the quiz card:
- Shown only for `Ready`. For `Idle`, `Searching` and `None` nothing is drawn.
- Gated by the shared `Headline signals` switch through `LocalHeadlineSignalsEnabled` (the same switch ECHO-01 and EXC-04 use); no new setting.
- Title "Other angles in your archive", a row of chips for the extracted phrases, then up to 5 rows (source, headline, date, and the contrarian cue as a small label). Tapping a row opens that article through the same navigation BOUNTY-01 uses.
- A one-line limitation caption, touch targets of at least 48dp, content descriptions for TalkBack.

## 7. Localization

About 8 `echo_contrast_*` keys in all 38 `values*/strings.xml` (title, chips label, cue label, limitation caption, content descriptions, and the empty-state wording kept for tests): English and Vietnamese hand-written, the rest English fallback. Added in the task that first uses them; `StringResourceParityTest` must stay 6/6.

## 8. Tests

- **JVM:** `TopicPhraseExtractorTest` (proper-noun runs, sentence-initial word, weekdays/months in EN and VI, feed name excluded, cap of 3, ordering, empty for lowercase or non-supported text); `ContrarianCueLexiconTest` (each language, whole-word only: "but" not in "butter", "risk" not in "brisk", accent handling, no cue returns null); `ContrastingCoverageFinderTest` (same feed excluded, current article excluded, phrase must match as a whole phrase, cue required, cap of 5, newest first, cue reported per item, empty candidates).
- **Robolectric:** `ReadingViewModelContrastTest` (Searching then Ready, None when nothing qualifies, switching phrase, cancellation on article change, failed lookup gives None) with every existing `ReadingViewModel*` test unchanged; Compose tests for the card (hidden when not Ready, chips switch phrase, row tap opens, hidden when the shared switch is off).
- **Real-data probe (temporary, never committed):** run the extractor, lexicon and finder against a copy of the Pixel Room database (214 BBC articles) to measure how often a headline yields a phrase and how often a contrarian cue appears, and to check for junk phrases. Because the archive has a single feed, the "different source" branch cannot return results on it; report that fact instead of claiming the feature works on real data end to end.
- **Device smoke (Pixel `2B051FDH3006MU`):** back up Room (all three files) and DataStore (non-empty, `integrity_check` ok), seed a second feed with one or two crafted articles into a COPY of the database and push it, open an article from the first feed, confirm the card appears at the end with the right row and cue, tap the row to open it, confirm the card is hidden when the switch is off, then restore both backups and verify with `cmp`. Ads are closed automatically per the owner's standing instruction.

## 9. Risks and Limits

- False positives: a contrarian word in a headline about a different aspect of the topic still matches; the card says "may", not "rebuts".
- False negatives are expected and are the common case.
- A topic phrase that is too generic (a common surname) could surface unrelated articles; the minimum length, the whole-phrase rule and the chip row keep the reader in control.
- Seeding test data into a database copy is the only way to see the card on the device; the report must say so.

## 10. Non-goals

No AI-generated arguments, no source citations, no network, no BYOK option in this task (a possible later extension, out of scope here), no history, no new setting, no Room change, no new dependency.
