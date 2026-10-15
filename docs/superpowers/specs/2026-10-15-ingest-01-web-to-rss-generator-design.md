# INGEST-01 — Web-to-RSS Generator Design

## Context

RSS Cat Hub currently parses RSS/Atom using Rome in `RssHelper`, stores `Feed` and `Article` in Room, and refreshes local feeds through `AbstractRssRepository.syncSingleFeed()` and `SyncWorker`. It has no static-HTML article-list scraper. Existing Readability use extracts full article content; it does not discover repeating article lists. The current shared OkHttp client follows redirects and is built with trust-all TLS, while the manifest allows cleartext; neither is acceptable for fetching arbitrary user-provided pages under a public-HTTPS-only policy.

INGEST-01 should add a bounded, reviewable MVP for static blog/news listing pages, not promise support for every website. Preserve ordinary RSS/Atom behavior.

## Goals and non-goals

### Goals

- Let a user submit a public HTTPS page URL when adding a source and discover a repeated static article list.
- Show site title and article preview before saving; save only after user confirms.
- Persist web-source identity and enough source metadata in Room for regular refresh.
- Refresh web sources through existing SyncWorker cadence/constraints and deduplicate by canonical article URL through existing article/feed/account identity.
- Isolate parser/network errors to the single virtual feed; keep the rest of account sync working.
- Enforce public-network URL restrictions and bounded resource use.

### Non-goals

- JavaScript execution, WebView/headless browser, authenticated pages, paywall bypass, pagination/crawl depth, arbitrary site templates, user-edited selectors, or "any website works" guarantee.
- Changing all existing RSS backend/client security behavior as part of this feature. The new scraper path must use its own hardened client; review trust-all TLS/cleartext as a separate security work item.
- Importing a generated site as if it were a server-side RSS endpoint for Fever/Google Reader backends. MVP virtual web feeds are supported by local RSS account only; other backend account types must show an explicit unsupported message or route to existing server flow without saving a fake local feed.

## User flow

1. User enters a public HTTPS page URL in the existing add-source flow.
2. Existing RSS/Atom discovery is attempted first. If a valid RSS/Atom document is found, retain current subscription flow unchanged.
3. If URL responds with static HTML and no usable RSS/Atom feed, invoke web-list discovery.
4. Show preview containing discovered site name and a small sample of article title, canonical URL host/path, and date when available. User can confirm or cancel. Do not persist on cancel or on failed/ambiguous discovery.
5. On confirmation, create a local virtual `Feed` linked to the source page and persist scraper metadata; insert preview articles via normal article DAO flow.
6. On future local sync, scrape that source page, map new candidates to Articles, and deduplicate by existing `(feedId, accountId, link)` DAO query. Retain historical articles no longer present on page.

## Discovery contract

- Input accepts only HTTPS URLs with valid host for MVP; reject userinfo, unsupported schemes, malformed host, local/private/reserved IPs, localhost names, and unsafe DNS answers.
- The scraper fetch client has normal platform TLS verification, `followRedirects(false)`, short bounded connect/read/call timeouts and capped response bytes. Reject every 3xx response rather than following it. Use an injected OkHttp `Dns` implementation that resolves the host once per call, rejects the entire answer set if any address is private/reserved, then returns only the validated address set to OkHttp; never resolve-and-check separately from the connection client. Block private, loopback, link-local, multicast, unspecified, and reserved IPv4/IPv6 ranges. HTTPS-only avoids the app's broad cleartext allowance.
- Accept only successful HTML responses with supported content type and a bounded body. Do not process compressed/expanded content past the byte cap. Do not execute scripts or load CSS/images/subresources.
- Parse with existing Jsoup dependency. Detect repeated `<article>` candidates first, with a small fallback limited to repeated elements that each contain a link, non-empty heading/title, and optional machine-readable date. Candidate scoring must be deterministic; require at least 3 valid items and distinct canonical absolute HTTPS article URLs before presenting preview. If no single candidate group meets threshold or groups tie ambiguously, return an explicit unsupported/ambiguous result; do not silently choose.
- Resolve relative links against final requested page URL (redirects are rejected, so input URL is base). Canonicalize by removing fragment and normalizing scheme/host/default port; preserve query parameters except known tracking parameters only if a deterministic existing helper is available. Do not infer article body or fetch individual pages in MVP.
- Map date only from parseable `time[datetime]` or equivalent machine-readable metadata. Missing/unparseable dates use sync-time fallback consistent with current `Article` mapping.

## Architecture

- Keep current Rome parsing and RSS subscription behavior in `RssHelper` unchanged.
- Add infrastructure `WebFeedScraper` with injected dedicated HTTP client and Jsoup-based parser. Expose typed outcomes for success, unsupported/ambiguous HTML, invalid/unsafe URL, oversized response, HTTP error, and timeout; avoid leaking raw response bodies into UI/logs.
- Persist source kind plus source page URL in Room. Prefer adding a backward-compatible `Feed` column with a safe default for existing RSS records; web-source records use a distinct enum/string kind. Avoid a separate virtual-feed table unless Room relationship constraints show `Feed` cannot hold the minimum metadata.
- Add a local sync dispatch seam in `LocalRssSv`/`AbstractRssRepository` so RSS feeds continue through `RssHelper.queryRssXml`, while web feeds call `WebFeedScraper`. Only Local RSS service owns the web feed path.
- Reuse existing `Feed`/`Article`, `ArticleDao.insertListIfNotExist`, feed group/account relationships, notification/filter processing, WorkManager cadence, and local subscription path after preview confirmation. Ensure malformed web feed handling records failure via existing `FeedHealthDao` rather than appearing as successful empty parse.
- Add narrowly scoped add-source UI state for loading, preview, confirm/cancel, unsupported and retryable failure; no new multi-step wizard beyond preview.

## Error and security behavior

- Invalid/private/local URL: reject before network call.
- DNS returns any blocked address, response redirect, timeout, non-HTML, non-success, or body cap exceeded: fail closed; no feed/article row saved.
- HTML has fewer than 3 valid distinct article candidates or ambiguous groups: show “could not identify an article list” and preserve entered URL for correction; no feed row saved.
- Individual malformed candidate: skip candidate and continue parsing siblings. If fewer than 3 valid items remain, discovery fails threshold.
- During refresh, one malformed candidate is skipped; whole feed records error only for page-level fetch/parse failure. Other feeds continue under existing sync isolation.
- Log host and typed error only, not full HTML, article body, credentials, or query string.

## Testing

- Unit tests for HTTPS URL validation, userinfo, literal IPv4/IPv6 ranges, DNS mock answers including mixed public/private answers, redirects, TLS/HTTP status, timeout, size limit, content type, canonicalization, relative URLs, duplicate URLs, tracking fragments, dates, fewer-than-three threshold, ambiguous repeated groups, malformed candidate isolation.
- Parser fixtures for representative supported article-list HTML and negative cases (single article, nav/sidebar repeated links, missing title/link, script-only rendering). Assert stable candidate ordering and preview content.
- ViewModel/widget tests for loading, preview values, confirm persists once, cancel persists nothing, invalid URL/error and RSS flow remains unchanged.
- Room integration test verifies schema migration of preexisting feeds to RSS kind, web source metadata persistence, Article feed foreign key, and dedupe across repeated refresh.
- Repository/worker integration test uses MockWebServer plus in-memory Room and injected scraper client, confirms first refresh adds candidates, next refresh adds only new links, failure marks feed health and does not prevent a normal RSS feed from syncing.
- Tecno smoke on locked device only: add a public static article-list fixture/site, verify preview then confirmation, feed appears, initial articles appear, refresh adds a new candidate without duplicates. Do not use `connected*`; stop on full-screen ad and wait for user `done`.

## Acceptance decisions

- MVP supports static HTML only; JavaScript-rendered pages unsupported.
- Preview and explicit user confirmation required before persistence.
- Minimum threshold: 3 valid candidates with distinct canonical HTTPS URLs.
- Only public HTTPS destinations; private/local/redirects rejected.
- Refresh follows existing local sync cadence and network constraints.
- Existing RSS import and refresh remain behaviorally unchanged.
