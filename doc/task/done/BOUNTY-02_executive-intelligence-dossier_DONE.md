# BOUNTY-02 — Hồ sơ thông tin cục bộ (Local Intelligence Dossier) — DONE

**Hoàn tất:** 2026-11-01 · **Nhánh:** `dev` · **Trạng thái push:** chưa push

## Phạm vi — nói thẳng
Spec gốc yêu cầu Dossier có mục "tranh luận cộng đồng" lấy từ Reddit/Hacker News và tóm tắt bằng AI. Owner đã duyệt bản thu hẹp, trung thực hơn:
- **Có:** bản **tổng hợp cơ học từ kho bài đã lưu trong app**, mở từ nút `📁 Lập hồ sơ` trong sheet BOUNTY-01 (chỉ khi có >= 2 bài liên quan). Bốn tab Material 3: **Tóm tắt**, **Dòng thời gian**, **Tên gọi**, **Nguồn tin**. Xuất Markdown qua share sheet của Android.
- **Không có:** Reddit/Hacker News (mục "tranh luận cộng đồng" được thay bằng "góc nhìn khác nhau giữa các nguồn"), AI, mạng, API key, PDF, bảng Room (DB vẫn v20), lưu trữ lâu dài.
- Đây **không phải kiểm chứng sự thật**, không xếp hạng nguồn, không kết luận ai đúng. Dossier ghi dòng giới hạn này ngay trong sheet và trong tệp xuất.

## Nội dung bốn phần
- **Tóm tắt:** tối đa 3 câu **nguyên văn** chứa cụm truy vấn (khớp nguyên từ, không khớp chuỗi con), mỗi câu <= 240 ký tự không cắt giữa surrogate, mới nhất trước, loại câu gần trùng bằng Jaccard >= `SUMMARY_DEDUPE_JACCARD_THRESHOLD` (0,8; heuristic, không phải so khớp ngữ nghĩa). Mỗi câu kèm nguồn, ngày, chạm để mở bài.
- **Dòng thời gian:** gom theo ngày theo múi giờ thiết bị, ngày mới nhất trước.
- **Tên gọi ("Recurring names/phrases"):** cụm 1-4 từ viết hoa xuất hiện ở >= 2 bài khác nhau, tối đa 8. **Đếm văn bản, không phải nhận dạng thực thể (NER).** Loại trừ chính truy vấn, tên nguồn (feed), thứ/tháng và từ dừng; cụm ngắn bị cụm dài chứa nó loại bớt. Ngôn ngữ không viết hoa (CJK) cho danh sách rỗng theo thiết kế.
- **Nguồn tin:** gom theo feed, cần >= 2 feed, tối đa 3 tiêu đề mỗi nguồn; một nguồn thì hiển thị thông báo trung thực.

## Cấu trúc mã
- `domain/model/bounty/Dossier.kt`, `DossierBuilder.kt`, `DossierMarkdownBuilder.kt`: thuần Kotlin; chỉ đọc metadata danh sách (FIX-17, không đọc body).
- `infrastructure/bounty/DossierSharer.kt`: ghi `cacheDir/dossiers/dossier_<slug>_<millis>.md`, giữ 24 giờ, `FileProvider` có sẵn; slug ASCII <= 40 ký tự, không thể thoát khỏi thư mục.
- `ReadingViewModel.kt`: `dossier`, `openDossier()`, `dismissDossier()`, `prepareDossierShare()`, `showDossierSheet`; dựng trên `defaultDispatcher`; đóng sheet bounty hoặc đổi bài sẽ xóa dossier.
- `ui/page/home/read/bounty/DossierSheetContent.kt` và nút `Create dossier` trong `BountyInvestigationSheetContent`; `ReadingPage.kt` nối sheet và thứ tự Back: echo -> dossier -> bounty -> ...
- 16 khóa `dossier_*` ở 38 locale (EN và VI viết tay, còn lại tiếng Anh dự phòng).
- Markdown: escape ký tự điều khiển, ép về một dòng, không bao giờ phát sinh HTML thô; xuất bằng tiếng Anh vì tệp rời khỏi app.

## Phát hiện khi làm
1. **Tên nguồn nhiễu trên dữ liệu thật:** probe trên 176 bài BBC cho thấy "BBC" luôn đứng đầu danh sách "Tên gọi" (do dòng ghi nguồn), không phải chủ đề bài. Đã loại tên feed và thêm test hồi quy `recurringPhrases_excludesTheSourceFeedName`.
2. Probe cũng cho thấy với kho nhỏ và tin ngắn, phần lớn truy vấn chỉ ra 0-2 cụm lặp lại; đây là hành vi đúng chứ không phải lỗi.

## Kiểm chứng
- **Unit:** 1.640 test, 0 lỗi, 0 skip (nền trước task: 1.580; thêm 60). Gồm `DossierBuilderTest` 17, `DossierMarkdownBuilderTest` 13, `DossierSharerTest` 5, `ReadingViewModelDossierTest` 10, `DossierSheetContentTest` 13, thêm 2 test nút `Create dossier`. `StringResourceParityTest` 6/6.
- **Lint:** build thành công (`lintDevDebug --rerun-tasks`, 0 lỗi chặn). **Build:** `assembleDevDebug` + `assembleDevDebugAndroidTest` pass.
- **Instrumentation trên Pixel 7 Pro `2B051FDH3006MU`:** 10/10 (`DossierDeviceTest`, `BountyInvestigationSheetDeviceTest`, `EchoSignalDeviceTest`).
- **Smoke thật trên Pixel (212 bài BBC):** bài "Shops looted… protests" -> bôi đen `protests` -> `🕵️ Điều tra` -> 2 bài liên quan -> `📁 Lập hồ sơ` -> 4 tab: Tóm tắt (3 trích dẫn nguyên văn có nguồn/ngày), Dòng thời gian (2 ngày), Tên gọi (rỗng kèm ghi chú không phải NER), Nguồn tin (thông báo một nguồn) -> `Chia sẻ Markdown` mở chooser với `dossier_protests_….md`; tệp có đủ 4 mục; Back đóng dossier trước rồi về kết quả bounty; chạm bài ở tab Dòng thời gian mở đúng bài.

## Giới hạn đã biết
1. **Chỉ là tổng hợp cơ học từ kho cục bộ.** Có thể bỏ sót nhiều góc nhìn; chất lượng phụ thuộc số bài liên quan đã lưu.
2. "Tên gọi" đếm cụm viết hoa, **không phải NER**; chưa đo precision/recall trên kho lớn.
3. Truy vấn chỉ khớp tiêu đề + mô tả ngắn (không đọc body theo FIX-17), nên nhiều bài liên quan không được tính.
4. Smoke chỉ có một nguồn (BBC), nên tab "Nguồn tin" chỉ kiểm được nhánh một nguồn trên máy thật; nhánh nhiều nguồn được kiểm bằng test Compose và instrumentation.
5. Nút chia sẻ chỉ kiểm đến chooser, không gửi tệp tới ứng dụng nào.
6. Ads: trong smoke có hai lần `AdActivity` toàn màn hình (lúc mở app); đã tự đóng bằng nút "Tiếp tục đến ứng dụng" theo chỉ thị owner, không chạm nội dung quảng cáo.
7. Một lần bôi đen bằng long-press vào tiêu đề mở Chrome Custom Tab (tiêu đề là liên kết); tôi dừng, đóng bằng cách dừng app, và bôi đen ở thân bài. Không ảnh hưởng dữ liệu.

## An toàn dữ liệu thiết bị
Trước smoke đã sao lưu **cả Room lẫn DataStore** (kiểm tra không rỗng, `integrity_check` ok). Một lần ghi khôi phục đầu tiên lỗi do quote `run-as` sai nên chưa ghi gì; đã sửa và khôi phục hai lần sau smoke. Cuối cùng cả Room (212 bài, ok) và DataStore giống hệt bản sao lưu (`cmp`), cache `dossiers` đã xóa, test package đã gỡ.

## Commits trên nhánh `dev`
- `7c3d4bc2`, `315bfbfd` docs: thiết kế BOUNTY-02
- `6ee3b94f`, `09ef1974` docs: kế hoạch BOUNTY-02
- `8e3f4cdc` feat(bounty-02): build local dossier from investigation results
- `916a6860` feat(bounty-02): render dossier as escaped Markdown
- `5e2b7256` feat(bounty-02): share dossier Markdown through FileProvider
- `66b3d748` feat(bounty-02): manage dossier state and strings
- `c74c8ad0` feat(bounty-02): show dossier in four Material 3 tabs
- `804866b0` feat(bounty-02): open dossier from the investigation sheet
