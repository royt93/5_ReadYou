# FRONT-03 — Ngân Hà Tri Thức (Knowledge Galaxy) — DONE

**Hoàn tất:** 2026-11-02 · **Nhánh:** `dev` · **Trạng thái push:** chưa push

## Phạm vi — nói thẳng
Todo gốc mô tả một tab "Vũ Trụ Tri Thức" riêng bằng Canvas hoặc OpenGL. Second Brain (BOUNTY-03) đã có đồ thị khái niệm, zoom/pan, thanh thời gian, nên theo quyết định của owner bản này là **chế độ xem thứ hai trong trang Second Brain**, không tạo tab mới:
- Nút chuyển **"Khái niệm | Ngân Hà"** (EN: Concepts / Galaxy). Ngân Hà dùng chung ý tưởng thanh thời gian với chế độ khái niệm.
- Mỗi **bài đã đọc** (`isUnread = 0`) là một ngôi sao; hai bài chung ít nhất một khái niệm được nối bằng đường mảnh. Khái niệm lấy offline từ `SemanticSearchEngine.detectConceptsStrict` và tag trong `aiSummary` đã lưu.
- Kéo một ngón để xoay, hai ngón để thu phóng, chạm đúp hoặc nút "Đặt lại góc nhìn" để về mặc định, chạm một sao để xem tiêu đề, nguồn, ngày và nút **"Mở bài"**.
- **Chiếu phối cảnh trên Compose Canvas, không phải engine 3D GPU.** Không OpenGL, không cảm biến, không AI, không API key, không quảng cáo/IAP, không đổi Room (vẫn v20).
- Trang Second Brain còn được sửa theo yêu cầu của owner giữa chừng: có **action bar với nút Back** (`BaseScaffold` + `FeedbackIconButton`) và **inset edge-to-edge**.

## Cấu trúc mã
- `domain/model/galaxy/`: `GalaxyStar/GalaxyLine/GalaxyGraph`, `GalaxyGraphBuilder` (`MAX_STARS=600`, `MAX_LINES=400`, `MAX_NEIGHBORS_PER_STAR=3`), `GalaxyLayout` (vị trí cố định theo id và khái niệm chính), `GalaxyCamera` (yaw, pitch kẹp, zoom kẹp, chiếu phối cảnh, thứ tự độ sâu, `hitTest`). Toàn bộ là Kotlin thuần.
- `ArticleDao.queryReadArticleListWithFeed(accountId, limit)`: chỉ `ARTICLE_LIST_COLUMNS`, `isUnread = 0`, `ORDER BY date DESC, id DESC` (FIX-17: không đọc cột body).
- `domain/sv/GalaxySv`: truy vấn bài đã đọc rồi dựng đồ thị trên Default dispatcher.
- `ui/page/secondbrain/galaxy/`: `GalaxyViewModel` (một `@Inject` constructor), `GalaxyUiState`, `GalaxyCanvas`, `GalaxyPanel`, `GalaxyStarSheet`; `SecondBrainPage` thêm tham số mới ở cuối với giá trị mặc định nên caller và test cũ không đổi.
- 10 chuỗi `galaxy_*` ở 38 locale (EN và VI viết tay, còn lại tiếng Anh).

## Quyết định thiết kế đáng nhớ
- Vị trí một sao chỉ phụ thuộc vào id và khái niệm chính của chính nó, không phụ thuộc các sao khác, nên đọc thêm một bài không làm cả ngân hà xáo trộn.
- Camera giữ nguyên khi kéo thanh thời gian (state không gắn với đồ thị).
- Khi cửa sổ thời gian không có sao nào, trang vẫn ở trạng thái Ready (thanh trượt còn dùng được) thay vì kẹt ở màn "trống".
- Độ sáng nền tăng theo số sao với trần (`GLOW_MAX_ALPHA`); đây chỉ là hiệu ứng thị giác có trần, không phải dữ liệu.

## Kiểm chứng
- **Unit:** 1.953 test, 0 lỗi, 0 skip (nền trước task 1.895). Thêm: `GalaxyGraphBuilderTest` 9, `GalaxyLayoutTest` 5, `GalaxyCameraTest` 12, `GalaxyArticleDaoIntegrationTest` 4, `GalaxySvTest` 5, `GalaxyViewModelTest` 10, `GalaxyPageTest` 13. `StringResourceParityTest` 6/6.
- **Lint:** 0 lỗi, 2.463 cảnh báo (không đổi). Hai cảnh báo do chính mình gây ra (`PluralsCandidate` ở "N stars", `UnusedResources` ở `galaxy_hint`) đã sửa. **Build:** `assembleDevDebug` và `assembleDevDebugAndroidTest` pass.
- **Mutation check:** tắt nhóm theo khái niệm của layout, tắt `loadJob?.cancel()` trong ViewModel, tắt chạm đúp đặt lại camera: mỗi lần đúng một test chuyển đỏ, rồi khôi phục. Test "cụm" ban đầu so trung bình khoảng cách nên **không bắt được** lỗi, đã viết lại thành chặn cứng đường kính cụm trước khi tin nó.
- **Instrumentation** `KnowledgeGalaxyDeviceTest` 2/2 bằng `am instrument -e class ...` (không dùng `connectedDevDebugAndroidTest`) trên **Tecno `115333744A005844`** và trên **Samsung A50s `R58MA6WYRPE`**.

## Smoke trên máy thật
Máy dùng: Tecno KJ7 (Android 14) rồi Samsung A50s SM-A507FN (Android 11) sau khi owner đổi máy. Dữ liệu seed: **bản sao** DB của Tecno với toàn bộ 152 bài đánh dấu đã đọc (DB gốc chỉ có 9 bài đã đọc); DB gốc nằm ở `/tmp/front03-backup`.
- Thấy được: nút chuyển chế độ, **152 sao và 5 đường nối**, nền phát sáng, nút Back ở action bar, tiêu đề và nội dung không chui dưới status bar hay thanh điều hướng, màu theo chủ đề Material You của từng máy (xanh dương trên Tecno, xanh lá trên A50s).
- Kéo xoay đĩa nghiêng đúng hướng; "Đặt lại góc nhìn" đưa về góc mặc định và tự tắt; chạm một sao hiện thẻ "BBC News · Oct 21, 2026" cùng nút "Open article"; **"Mở bài" mở đúng màn đọc**, đóng bài quay về Ngân Hà, nút Back đưa về Settings.
- **Số đo khung hình thật** (`dumpsys gfxinfo`, vuốt bằng `input swipe`, 152 sao):
  - Tecno: 545 khung, 20 khung giật (3,67%), p50 12 ms, p90 18 ms, p99 31 ms.
  - A50s, ba lượt kéo liên tục: giật **60,4% / 30,5% / 42,9%**, p50 14–17 ms, p90 23–25 ms, p99 32–36 ms.
  - **Kết luận không tô hồng: trên A50s ngân hà chưa mượt khi đang kéo** (nhiều khung vượt 16,7 ms, 30–60% bị tính là giật). Đã không tối ưu thêm vì ngoài phạm vi và chưa xác định nguyên nhân (số lượng draw call, nền gradient, hay máy yếu). Idle thì không vẽ lại.

## Giới hạn đã biết
1. Phối cảnh trên Canvas 2D, không phải 3D GPU; giới hạn 600 sao và 400 đường để giữ chi phí vẽ có trần, nhưng **A50s vẫn giật khi kéo** như số đo ở trên.
2. Quan hệ giữa các bài chỉ dựa trên bộ khái niệm của `SemanticSearchEngine` (đã bổ sung đường nối từ chung trong tiêu đề ở `FRONT-03b_galaxy-title-word-links_DONE.md`: 5 đường lên 43 trên cùng 152 bài, vẫn còn nhiều sao rời). Ở smoke chỉ có **5 đường nối trên 152 sao**, nên ngân hà trông là một đám sao rời; kho chủ đề lạ sẽ còn thưa hơn.
3. Chỉ tính bài đã đánh dấu đọc. DB thật của Tecno chỉ có 9 bài đã đọc nên phải smoke trên bản sao có đánh dấu 152 bài; **chưa quan sát ngân hà trên dữ liệu đọc thật của một người dùng**.
4. Chưa kiểm: thu phóng hai ngón trên máy thật (đã có test Robolectric), thanh thời gian trong chế độ Ngân Hà trên máy thật, tiêu điểm TalkBack, chế độ ngang, và trường hợp 600 sao đầy (mới thử 152).
5. Trên A50s dump giao diện không hiện thẻ chi tiết khi chạm hai lần đầu vì toạ độ chạm trượt khỏi sao (phải dựa vào ảnh để chọn toạ độ); lần thứ ba chọn đúng. Không phải lỗi mã nhưng cho thấy vùng chạm 24dp khá nhỏ khi sao dày đặc.
6. Quảng cáo toàn màn hình xuất hiện mỗi lần mở app trên cả hai máy; có lần tự đóng sau vài giây, có lần nút "Continue to app" bật lên rồi biến mất trước khi chạm nên không phải bấm.

## An toàn dữ liệu
- Tecno: sao lưu Room (3 file) và `settings.preferences_pb`; seed chỉ ghi lên bản sao; khôi phục xong `cmp` giống hệt cả 4 file, `integrity_check` ok, 152 bài/1 feed/9 đã đọc.
- A50s: máy chưa có Room và chưa có DataStore trước khi chạy; sau smoke chỉ xoá 3 file Room đã seed, giữ nguyên DataStore (50 byte do instrumentation tạo, `cmp` giống hệt bản đã lưu). Không bao giờ xoá DataStore.
- Không còn gói `com.mckimquyen.reader.test` trên hai máy.
