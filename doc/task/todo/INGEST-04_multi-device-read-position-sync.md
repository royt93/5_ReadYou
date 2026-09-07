# [INGEST-04] Multi-device Read Position Sync

- **Type:** New Feature / Sync
- **Priority:** `P2 (Medium)`
- **Estimation:** `8 Story Points`
- **Epic:** [07. INGESTION — Biến Mọi Web Thành RSS & Đọc Sau](07_UNIVERSAL_INGESTION_AND_SYNC.md)
- **Location:** [`app/src/main/java/com/mckimquyen/reader/domain/sv/AbstractRssRepository.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/domain/sv/AbstractRssRepository.kt), [`app/src/main/java/com/mckimquyen/reader/domain/sv/FeverRssSv.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/domain/sv/FeverRssSv.kt), [`app/src/main/java/com/mckimquyen/reader/domain/sv/LocalRssSv.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/domain/sv/LocalRssSv.kt), [`app/src/main/java/com/mckimquyen/reader/domain/sv/SyncWorker.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/domain/sv/SyncWorker.kt), `domain/model/article/`, `domain/repository/ArticleDao.kt` (bổ sung field/API mới)

## Vấn đề thực tế
Hiện tại app chỉ đồng bộ được **danh sách feed** (qua OPML — xem `OpmlSv.kt`) và trạng thái đã đọc/chưa đọc/gắn sao ở mức bài viết thông qua `AbstractRssRepository` (`markAsRead`, `markAsStarred`...) khi tài khoản dùng backend hỗ trợ (Fever qua `FeverRssSv`, Google Reader hiện fallback về `LocalRssSv` theo `RssSv.get()`). Tuy nhiên, **không có cơ chế nào đồng bộ vị trí đọc dở bên trong một bài viết dài** (scroll offset / phần trăm đã đọc) giữa nhiều thiết bị của cùng một user. Người dùng đọc dở một bài dài trên điện thoại, mở lại trên tablet/máy khác cùng tài khoản Fever/Google Reader thì phải cuộn tìm lại từ đầu — trải nghiệm kém hơn nhiều so với Kindle Whispersync. Đây là tính năng khác biệt hoàn toàn với OPML backup: OPML chỉ đồng bộ *cấu trúc feed*, không đồng bộ *tiến độ đọc*.

## User Story
> Là người dùng đọc RSS Cat Hub trên nhiều thiết bị (điện thoại + tablet) với cùng một tài khoản backend hỗ trợ sync (Fever/Google Reader),
> Tôi muốn vị trí đọc dở của một bài viết dài được tự động lưu lại và khôi phục trên bất kỳ thiết bị nào tôi mở lại bài đó,
> Để tôi không phải cuộn tìm lại đoạn mình đang đọc mỗi khi đổi thiết bị, giống trải nghiệm Whispersync của Kindle.

## Acceptance Criteria (Gherkin)
- **Given** người dùng đã đăng nhập tài khoản backend hỗ trợ sync (Fever hoặc Google Reader) trên từ 2 thiết bị trở lên
- **When** người dùng cuộn đọc dở một bài viết dài trên Thiết bị A rồi thoát khỏi màn hình đọc (đóng bài, quay lại danh sách, hoặc rời app)
- **Then** vị trí đọc (scroll offset dạng % nội dung đã đọc, hoặc phần tử/paragraph anchor gần nhất) được lưu cục bộ ngay lập tức và đẩy lên backend ở lần sync định kỳ tiếp theo (`SyncWorker`) mà không chặn luồng đọc (chạy nền qua `Dispatchers.IO`)
- **And** khi người dùng mở cùng bài viết đó trên Thiết bị B sau khi Thiết bị B đã sync xong, màn hình đọc tự động cuộn tới đúng vị trí đã lưu (sai số chấp nhận được ở mức đoạn văn, không cần chính xác tuyệt đối pixel)
- **And** nếu tài khoản đang dùng là Local RSS (không có backend, tức `LocalRssSv` không hỗ trợ) thì tính năng tự động vô hiệu hoá, không hiển thị lỗi, không crash — chỉ hoạt động như hiện tại (không đồng bộ)
- **And** khi có xung đột (2 thiết bị cùng đọc dở một bài trong lúc offline rồi sync lại), áp dụng quy tắc "vị trí đọc xa hơn (tiến độ % cao hơn) hoặc timestamp mới hơn thắng" — không được làm mất tiến độ đọc xa hơn đã có
- **And** người dùng có thể tắt tính năng này trong Settings (mặc định bật khi tài khoản hỗ trợ), lưu qua DataStore theo đúng pattern `*Pref.kt` hiện có trong `infrastructure/pref/`.

## 🔁 Loop Prompt (dùng cho `/loop` hoặc agent thực thi task này)

```
Bạn đang thực hiện task [INGEST-04] "Multi-device Read Position Sync" trong repo RSS Cat Hub (com.mckimquyen.reader, xem CLAUDE.md để hiểu kiến trúc). Đọc kỹ "Vấn đề thực tế" + "Acceptance Criteria" trong file doc/task/todo/INGEST-04_multi-device-read-position-sync.md trước khi bắt đầu.

Mỗi vòng lặp:
1. Đọc code liên quan tại phần Location, xác nhận vấn đề còn tồn tại (không giả định). Đặc biệt xác nhận: `AbstractRssRepository` hiện có contract gì cho sync trạng thái bài viết, `FeverRssSv`/`LocalRssSv` implement khác nhau ra sao, và cấu trúc Room hiện tại (`ArticleDao`, entity `Article`) có sẵn cột nào lưu tiến độ đọc chưa (nếu chưa thì cần Room migration mới, không được sửa trực tiếp schema cũ).
2. Implement fix/feature đúng theo Acceptance Criteria. Tuân thủ CLAUDE.md: phân tầng domain/infrastructure/ui, mọi I/O nặng chạy Dispatchers.IO/Default (không block Main), không lưu Context/Activity/LazyListState vào ViewModel hay singleton, localize đủ 6 ngôn ngữ (en, vi, zh-rCN, ja, fr, de) nếu có text UI mới (ví dụ toggle Settings mới). Không được gọi trực tiếp provider class từ UI — mọi thao tác backend đi qua `AbstractRssRepository`/`RssSv.get()`.
3. Với thay đổi kiến trúc/thiết kế quan trọng (không bắt buộc cho fix nhỏ, 1-2 dòng), tham khảo ý kiến độc lập từ 2 AI agent khác trước khi chốt:
   - `codex exec -s workspace-write "Review approach cho task [INGEST-04] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có."`
   - `claude -p "Review approach cho task [INGEST-04] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có." --allowedTools "Read Grep Glob"`
   Đối chiếu góp ý, chỉ áp dụng nếu hợp lý — không áp dụng máy móc, không để agent ngoài tự sửa code của bạn.
4. Build kiểm tra: `./gradlew assembleDevDebug` (hoặc `lintDevDebug` nếu chỉ đổi resource/string).
5. Lặp lại tới khi Acceptance Criteria thỏa mãn 100%.
```

## 🏁 Tín hiệu kết thúc loop (End-Loop Signal)
Chỉ dừng loop khi hoàn tất TẤT CẢ bước sau, đúng thứ tự, KHÔNG bỏ bước:
1. **Audit code changes**: tự review lại toàn bộ `git diff` so với Acceptance Criteria + Definition of Done trong doc/task/README.md. Chấm điểm khách quan trên thang **10** — không tự thổi điểm, nếu có test giả/mock rỗng/logic chưa đúng thì điểm phải phản ánh đúng thực tế.
2. Bổ sung **unit test** cho mọi nhánh logic mới (`app/src/test/...`), phủ cả edge case (rỗng, lỗi mạng, dữ liệu null, giới hạn biên, xung đột 2 thiết bị offline cùng lúc).
3. Bổ sung **widget/Compose UI test** cho mọi component UI mới hoặc thay đổi hành vi UI (`app/src/androidTest/...`), bao gồm toggle Settings và hành vi tự động cuộn khi mở lại bài viết.
4. Bổ sung **integration test** cho luồng end-to-end liên quan (DB + repository + worker nếu có liên quan) — đặc biệt luồng lưu vị trí đọc → sync qua `SyncWorker` → khôi phục trên phiên đọc khác.
5. Chạy **smoke test trên device/emulator thật**: `./gradlew installDevDebug`, thao tác thủ công đúng luồng vừa sửa, ghi lại bằng chứng cụ thể (log logcat hoặc mô tả kết quả quan sát được) chứng minh hoạt động đúng — không suy đoán, không báo cáo khống.
6. Nếu điểm audit **> 9/10 VÀ** mọi test bước 2-4 pass **VÀ** smoke test bước 5 xác nhận hoạt động đúng:
   → `git add` các file liên quan → `git commit` với message rõ ràng, đúng Conventional Commits → **`git push`** lên remote nhánh hiện tại. Kết thúc loop, cập nhật trạng thái task (di chuyển file từ `doc/task/todo/` hoặc `inprogress/` sang `doc/task/done/`, đổi tên thêm hậu tố `_DONE` và viết Completion Report ngắn: điểm số, commit hash, danh sách test đã thêm).
7. Nếu điểm **≤ 9/10** hoặc bất kỳ điều kiện bước 2-5 chưa đạt: quay lại bước 1 của vòng lặp Loop Prompt, KHÔNG commit/push.
