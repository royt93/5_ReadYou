# FRONT-01 — AI Deep Dive (thiết kế)

**Ngày:** 2026-11-11 · **Nhánh:** `dev` · **Task gốc:** `doc/task/todo/FRONT-01_ai-multi-agent-deep-dive.md`

## Mục tiêu
Trong màn đọc, người dùng chọn `🔬 AI Deep Dive` ở menu ⋮ để nhận một báo cáo một trang gồm: **Bối cảnh**, **Phản biện** (góc nhìn đối lập), **Ma trận tác động** (ngắn hạn / dài hạn). Báo cáo xem được trong sheet và xuất được **PDF A4 một trang**.

## Phạm vi trung thực
- "3 agent" thực chất là **một request Gemini** trả JSON ba phần theo ba vai (Context, Critical Thinking, Synthesis). Không phải chuỗi agent thật; Synthesis không đọc đầu ra đã kiểm của hai vai trước. Owner chọn đúng phương án này để tiết kiệm quota (key chung đang `RateLimited`, key #1 `API_KEY_INVALID`, FIX-09 hoãn).
- **Không tra cứu web.** "Bối cảnh lịch sử" chỉ dựa trên kiến thức của mô hình. Prompt cấm bịa số liệu và nguồn.
- Kiểm tra chỉ là **cấu trúc**, không kiểm sự thật. UI và PDF ghi nhãn "AI soạn từ tiêu đề và trích đoạn bài; không tra cứu web; có thể sai".
- Khác BOUNTY-01/02 (tổng hợp cơ học cục bộ, không AI) và EXC-06 (chat nhiều lượt).
- Không làm: lưu bền, Room, migration, dependency mới, quảng cáo/IAP, chuỗi 3 request.

## Dữ liệu gửi và consent
- Gửi: tiêu đề + **trích thân bài ≤ 6.000 ký tự** (bỏ HTML, không cắt giữa surrogate). Không gửi link, feed, account.
- Consent riêng: khóa `deep_dive_gemini_consent` (`Boolean?`, null = chưa hỏi), khác khóa quiz và Podcast Studio. Hỏi trong sheet ở lần đầu, nêu rõ có gửi một phần thân bài. "Để sau" không lưu gì. Công tắc thêm vào `PrivacySettingsPage`.

## Thành phần
- `domain/ai/agents/DeepDivePolicy` (thuần): hằng số giới hạn, `sanitizeExcerpt`, `buildPrompt(title, excerpt, languageTag)`, `buildRequestBody` (JSON mode `responseMimeType=application/json`), `parseAndValidate(raw): DeepDiveParse` (`Valid` / `Rejected` kèm enum lý do). Bóc fence markdown.
  - `context`: 3–5 ý. `critical`: 2–4 góc nhìn, mỗi góc có nhãn trường phái. `impact`: 2–5 dòng gồm lĩnh vực, ngắn hạn, dài hạn.
  - Từ chối khi: không phải JSON, thiếu phần, phần rỗng, số ý ngoài giới hạn, chuỗi quá dài (giới hạn ký tự từng trường đặt thành hằng số có tên).
- `domain/ai/agents/DeepDiveReport` (model).
- `domain/sv/AiDeepDiveService.generate(articleId, title, excerpt, languageTag): AiDeepDiveResult` (`Ready` / `Failed(NOTHING_TO_ASK, NO_KEY, NETWORK, REJECTED)`), qua `AiRequestGateway` với use case `deep_dive`. Chỉ nhận chuỗi, không nhận article. Log chỉ loại exception hoặc lý do từ chối.
- `DeepDiveCache`: Hilt `@Singleton`, LRU 10 bài theo `articleId` (cache phải sống lâu hơn ViewModel vì `hiltViewModel()` tạo mới mỗi lần mở bài). Lỗi không cache.
- `ReadingViewModel`: `showDeepDiveSheet`, `deepDiveState` (`AwaitingConsent`, `ConsentDeclined`, `Loading`, `Failed`, `Ready`), `openDeepDive()`, `onDeepDiveConsent(Boolean)`, `retryDeepDive()`, `dismissDeepDive()`. Một request mỗi bài; bấm lần hai khi đang tải không làm gì; đổi bài hoặc đóng sheet hủy request. Thứ tự Back: dossier → deep dive → các sheet khác.
- UI: mục menu trong `TopBar.kt` (tham số `onDeepDive`, ngay dưới Hỏi đáp AI); `ui/page/home/read/deepdive/DeepDiveSheetContent.kt` (ba khối, bảng ma trận 3 cột, nút Thử lại và Xuất PDF, nhãn AI); nối trong `ReadingPage.kt`.
- PDF: `DeepDivePdfRenderer` dùng `android.graphics.pdf.PdfDocument`, A4 595×842 pt, layout bằng `StaticLayout`. Hàm layout thuần tách riêng. Tràn trang: cắt ý theo thứ tự ưu tiên, giảm cỡ chữ tối đa một bậc, thêm "…" và dòng "xem đủ trong app"; không tạo trang 2. Ghi `cacheDir/deepdive/deepdive_<slug>_<millis>.pdf` (slug ASCII ≤ 40, giữ 24 giờ), chia sẻ qua `FileProvider` có sẵn (`cache-path "."`), `application/pdf`. PDF có nhãn AI và ngày tạo; không có link hay tên nguồn.
- Chuỗi: ~18 khóa `deep_dive_*` × 38 locale (EN và VI viết tay, còn lại tiếng Anh; không đặt `%d` ngay trước danh từ số nhiều). `StringResourceParityTest` phải pass.

## Kiểm chứng
- JVM, TDD đỏ→xanh (đọc lý do đỏ từ XML kết quả), mutation check có backup và `cmp`: `DeepDivePolicyTest`, `AiDeepDiveServiceTest`, `DeepDiveCacheTest` (gồm test `@Singleton` và ViewModel mới dùng lại cache), `ReadingViewModelDeepDiveTest` (dispatcher unconfined + chờ thời gian thực có hạn), `DeepDivePdfLayoutTest`, `DeepDivePdfRendererTest` (Robolectric: đúng 1 trang, A4, slug an toàn, dọn tệp cũ), test nguồn (menu, Back, khóa consent riêng), `DeepDiveSheetContentTest`, parity chuỗi.
- androidTest cho sheet, chạy bằng `adb -s <serial> shell am instrument` (không dùng `connected*`).
- Máy thật **sớm**, ngay khi sheet + PDF chạy được và trước khi hoàn tất 38 locale: cổng thiết bị, sao lưu byte-exact Room/`-shm`/`-wal`/DataStore/`shared_prefs`; ca: consent, "Để sau" không ghi, "Đồng ý" ghi khóa, 1 request, `Ready`, mở lại bài ra cache, xuất PDF và mở, máy bay + Thử lại, tắt công tắc. Chỉ Pixel `2B051FDH3006MU` hoặc Tecno KJ7 `115333744A005844`. Quảng cáo toàn màn hình: chỉ Back.
- Quota: tối đa ~5 request thật. Nếu vẫn `RateLimited`, dừng và hỏi owner đặt key riêng; báo cáo ghi rõ ca chưa kiểm.

## Giới hạn đã biết (sẽ ghi vào báo cáo DONE)
- Một request ba vai, không phải chuỗi agent; không tra cứu web; kiểm cấu trúc không kiểm sự thật; AI có thể sai hoặc bịa chi tiết.
- Cache chỉ trong bộ nhớ, mất khi đóng app.
- PDF dùng font hệ thống; CJK/RTL chưa kiểm.
- Thân bài rời máy (≤ 6.000 ký tự) khi consent; được ghi rõ trong consent.
- Key mặc định chung và key #1 hỏng (FIX-09) nên có thể thất bại do quota.
