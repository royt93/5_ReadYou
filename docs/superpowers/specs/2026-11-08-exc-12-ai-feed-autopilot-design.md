# EXC-12 — AI Feed Autopilot

- **Ngày:** 2026-11-08.
- **Nhánh:** `dev`.
- **Trạng thái:** đã chốt các quyết định brainstorming bên dưới; bản spec chờ owner duyệt trước khi lập implementation plan.
- **Thiết bị duy nhất:** Tecno KJ7 `115333744A005844`.
- **Task gốc:** `doc/task/todo/EXC-12_ai-feed-autopilot.md`.

## 1. Mục tiêu và quyết định đã chốt

Tự động nhận diện các feed đăng nhiều nhưng người đọc hầu như không bao giờ mở, hiển thị số liệu thực tế làm lý do và đưa ra 3 lựa chọn ("Hủy đăng ký ngay", "Bỏ qua 30 ngày", "Giữ lại, không gợi ý nữa"). Xử lý hoàn toàn trên thiết bị, không mạng, không model nặng, không bao giờ tự ý hủy đăng ký mà không có xác nhận của người dùng.

Quyết định của owner ngày 2026-11-08:

1. Dùng dữ liệu hành vi đọc có sẵn trong app: tỷ lệ bài có `readAt` (đã mở đọc trong Reader). Bỏ hai chỉ số trong todo gốc mà app không lưu: thời gian đọc từng bài và tỷ lệ vuốt bỏ qua riêng.
2. Lưu các quyết định của người dùng ("bỏ qua 30 ngày", "giữ lại mãi") dưới dạng chuỗi JSON trong DataStore, không sửa schema Room (giữ nguyên v20).
3. Chỉ tài khoản Local hỗ trợ hủy đăng ký một chạm, vì các tài khoản từ xa (Fever, Google Reader) bị ẩn menu hủy trong code gốc và không hỗ trợ xóa cục bộ một chiều. Tài khoản từ xa chỉ hiển thị giải thích.
4. Chạy định kỳ bằng WorkManager mỗi tuần một lần, thông báo im lặng bằng một chấm báo số lượng trên hàng Cài đặt, không dùng thông báo đẩy hệ thống. Màn hình tự tính lại mỗi khi mở để số liệu luôn mới.
5. Ngưỡng nhiễu: feed có từ 20 bài trở lên trong cửa sổ đánh giá và tỷ lệ mở đọc dưới 5%, đồng thời feed phải có bài được nhập về máy từ ít nhất 14 ngày trước (tuổi feed, mục 4.2).
6. Viết spec, tự rà soát, commit rồi dừng chờ owner duyệt trước khi lập implementation plan hoặc viết code.

Các nội dung kỹ thuật dưới đây là đề xuất trong spec, chưa phải kết quả triển khai hoặc kiểm chứng.

## 2. Sự thật từ code

- `Article.readAt` (kiểu `Long?`) được thêm từ ngày 2026-10-18 (commit `b46a6bc9`, Room v17). Cột này chỉ được ghi tại một vị trí duy nhất khi người dùng mở bài trong `ReadingPage` (`ReadingViewModel.recordOpened`). Không có giá trị `readAt` cho bài đọc trước ngày 2026-10-18 (tất cả là null).
- Các thao tác đánh dấu đã đọc hàng loạt (nút đánh dấu tất cả, vuốt bài trong danh sách, chế độ Triage) chỉ sửa `isUnread = 0` và không bao giờ ghi `readAt`. Do đó, điều kiện `isUnread = 0 AND readAt IS NULL` đại diện cho các bài được đánh dấu đọc mà chưa từng mở xem nội dung.
- App hoàn toàn không lưu thời gian đọc của từng bài. Không có cột, không có bảng và không có nhật ký thời gian đọc.
- App không lưu riêng hành vi "vuốt bỏ qua". Vuốt trái, vuốt phải hay vuốt lên trong Triage đều chỉ gọi cùng một hàm đánh dấu đọc và cùng một loại điểm thưởng; bộ đếm vuốt bỏ qua chỉ nằm trong bộ nhớ và bị hủy khi đóng màn hình.
- `AbstractRssRepository.clearKeepArchivedArticles()` chạy vào mỗi lần sync và gọi `ArticleDao.deleteAllArchivedBeforeThan`, xóa vĩnh viễn mọi bài có `isUnread = 0 AND isStarred = 0` cũ hơn thời hạn `keepArchived` tính theo cột `updateAt` (ngày bài được sync vào DB). Mặc định là 30 ngày (`KeepArchivedPreference.For1Month`), nhưng người dùng có thể đặt ngắn hơn (1 ngày, 2 ngày, 3 ngày, 1 tuần, 2 tuần).
- Do việc dọn bài này, bài đã đọc bị xóa trước trong khi bài chưa đọc vẫn còn lại. Nếu cửa sổ tính toán dài hơn `keepArchived`, tỷ lệ mở đọc sẽ bị lệch nặng và dẫn đến kết luận sai rằng người dùng không bao giờ đọc feed đó.
- `FeedDao` không có cột ngày tạo feed, không có cột điểm nhiễu và không có truy vấn thống kê theo feed. `Feed` entity chỉ có `id, name, icon, url, groupId, accountId, isNotification, isFullContent, sourceType, sourceUrl`.
- Hàm xóa feed `AbstractRssRepository.deleteFeed` chỉ chạy đầy đủ trên tài khoản Local; `FeverRssSv` và `GoogleReaderRssSv` khóa cờ `delete = false` và không ghi đè hàm xóa, làm ẩn menu hủy đăng ký trong giao diện.
- App chưa có nơi lưu trữ các quyết định tắt gợi ý theo feed (không có bảng Room, không có khóa `stringSetPreferencesKey` trong DataStore).
- Đã có mẫu WorkManager định kỳ một ngày một lần (`BrainRpgDecayWorker`), đăng ký một dòng trong `RApp.workerInit()`. Chưa có worker chạy định kỳ mỗi tuần.

## 3. Phương án và lý do chọn

**Chọn: tính điểm nhiễu từ tỷ lệ tương tác của bài viết trong cửa sổ an toàn, lưu quyết định trong DataStore.**

- Tái sử dụng dữ liệu có sẵn (`readAt`, `isStarred`, `isReadLater`, `isUnread`), không cần thêm bảng hay cột Room mới (giữ v20), không cần chờ người dùng tích lũy dữ liệu thời gian đọc trong tương lai.
- Cửa sổ đánh giá tự co lại theo cấu hình `keepArchived` của tài khoản, chặn triệt để bẫy xóa bài làm lệch tỷ lệ.
- Dùng từ "tương tác" (gồm mở đọc, gắn sao, lưu đọc sau) thay vì chỉ đếm "mở", tránh báo sai cho những feed người dùng có đọc nhưng chọn lưu để đọc sau.
- Giao diện giải thích lý do bằng câu khách quan ("Bạn đã mở 0/25 bài trong 30 ngày qua"), không dùng từ "bỏ qua" vì app không phân biệt được vuốt bỏ qua với đánh dấu đã đọc hàng loạt.

Hai phương án không chọn:

- Thêm tracking thời gian đọc và bảng lưu riêng: phải tăng schema lên v21, phải sửa Reader để ghi nhận thời gian đọc, và tính năng sẽ phải chờ nhiều tuần sau người dùng mới có đủ dữ liệu để chạy lần đầu.
- Gợi ý trên tài khoản từ xa: tài khoản Fever/Google Reader đồng bộ bài đọc từ nơi khác về mà không mang theo `readAt`, làm mọi bài đọc ở nơi khác đều bị coi là chưa đọc; đồng thời app không thể hủy đăng ký feed trên máy chủ của họ.

Không thêm Room table, migration, dependency, không dùng Gemini/cloud, không quảng cáo/IAP.

## 4. Công thức điểm nhiễu và các biện pháp chống báo sai

### 4.1 Cửa sổ đánh giá

Gọi `T` là thời điểm hiện tại. Cửa sổ đánh giá được xác định theo tài khoản đang chọn:

```
CỬA_SỔ_TỐI_ĐA   = 30 ngày
thời_hạn_lưu    = account.keepArchived        (Always được coi là 30 ngày)
độ_dài_cửa_sổ   = min(CỬA_SỔ_TỐI_ĐA, thời_hạn_lưu)
bắt_đầu         = max(T - độ_dài_cửa_sổ, READ_AT_INTRODUCED)
```

- Nếu `thời_hạn_lưu` nhỏ hơn 14 ngày (người dùng chọn giữ bài 1, 2, 3 ngày hoặc 1 tuần), app từ chối đánh giá tài khoản đó và hiển thị giải thích. Lý do: bài đã đọc và chưa gắn sao bị xóa khi sync, nên tỷ lệ mở đọc bị lệch nặng.
- `READ_AT_INTRODUCED` là hằng số có tên, bằng 2026-10-18T00:00Z (ngày commit `b46a6bc9` thêm cột `readAt`). Bài đọc trước mốc này có `readAt` rỗng và không được tính là "chưa mở".
- Giới hạn trung thực của mốc này: máy cập nhật app muộn hơn ngày 2026-10-18 vẫn có thể có bài đọc trước khi cập nhật bị tính là chưa mở. Rủi ro này được giảm bằng việc người dùng luôn phải xác nhận, và thẻ gợi ý hiển thị số đếm thật để người dùng tự phán đoán.
- Cột lọc thời gian là `updateAt` (thời điểm bài được sync vào máy), không phải `date` (ngày xuất bản). Một feed mới thêm có thể kéo về bài cũ nhiều tháng. Bài có `updateAt` rỗng bị bỏ qua.

### 4.2 Tuổi của feed

Spec không dùng "bài sớm nhất trong cửa sổ" để đo tuổi feed, vì cửa sổ co lại theo `keepArchived` nên bài sớm nhất trong cửa sổ luôn trẻ hơn độ dài cửa sổ. Thay vào đó dùng `MIN(updateAt)` của **mọi** bài còn lưu của feed (không lọc cửa sổ).

Một feed nhiễu thường tích lũy bài chưa đọc, và loại bài này không bị dọn khi sync, nên `MIN(updateAt)` phản ánh đúng thời điểm feed bắt đầu đổ bài về máy. Nếu người dùng đã đánh dấu đọc hết và việc dọn bài đã xóa bài cũ, feed sẽ trông trẻ hơn thực tế và không bị gợi ý. Đây là hướng sai an toàn, không gây hủy nhầm.

### 4.3 Điều kiện một feed bị coi là gây nhiễu

Một feed thuộc tài khoản Local chỉ được đưa vào danh sách gợi ý khi thỏa mãn **toàn bộ** các điều kiện sau:

1. Không có quyết định "giữ lại mãi", và không có quyết định "bỏ qua 30 ngày" còn hiệu lực.
2. `tổng_bài_trong_cửa_sổ >= MIN_ARTICLES = 20`.
3. Tuổi feed (mục 4.2) tối thiểu `MIN_FEED_AGE_DAYS = 14`.
4. `tỷ_lệ_tương_tác < MAX_INTERACTION_RATIO = 0.05`.

Trong đó:
- `bài_tương_tác` = số bài trong cửa sổ có `readAt IS NOT NULL` hoặc `isStarred = 1` hoặc `isReadLater = 1`.
- `tỷ_lệ_tương_tác = bài_tương_tác / tổng_bài_trong_cửa_sổ`.
- Ví dụ biên: 20 bài có 1 bài tương tác là đúng 5%, không bị gắn cờ; 21 bài có 1 bài tương tác là 4,76%, bị gắn cờ.
- Thứ tự hiển thị: tỷ lệ tương tác tăng dần, bằng nhau thì nhiều bài hơn đứng trước.

### 4.4 Câu giải thích bằng số liệu thật

Mỗi thẻ gợi ý hiển thị một câu mô tả ngắn bằng số liệu đếm thật, ví dụ:
- Tiếng Việt: "Bạn chỉ mở 1 trong tổng số 34 bài nhận được trong 30 ngày qua (tương tác 3%)."
- Tiếng Anh: "You opened 1 of 34 articles received in the last 30 days (3% engagement)."
- Nếu `bài_tương_tác = 0`: "Bạn chưa mở bài nào trong tổng số 28 bài nhận được trong 30 ngày qua."

Không dùng từ "bỏ qua" hay "swipe-skip" trong giao diện.

## 5. Lưu trữ quyết định của người dùng

Lưu trong DataStore qua khóa chuỗi `DataStoreKeys.FeedAutopilotDecisions`, nội dung là JSON một map:

```json
{
  "feed_id_abc": {
    "type": "SNOOZE_30_DAYS",
    "decidedAtMillis": 1794052800000
  },
  "feed_id_xyz": {
    "type": "KEEP_FOREVER",
    "decidedAtMillis": 1794052800000
  }
}
```

Các loại quyết định:
- `SNOOZE_30_DAYS`: không đưa vào gợi ý trong 30 ngày (2.592.000.000 ms) kể từ `decidedAtMillis`. Sau thời hạn này, feed sẽ được đánh giá lại bình thường.
- `KEEP_FOREVER`: không bao giờ đưa feed này vào danh sách gợi ý nữa, trừ khi người dùng xóa quyết định trong phần quản lý ngoại lệ.

Dọn dữ liệu mồ côi theo cách lười: mỗi lần tính điểm, app loại khỏi map JSON mọi khóa `feedId` không còn tồn tại trong tài khoản, rồi ghi lại nếu có thay đổi. Cách này không cần chạm vào đường xóa feed dùng chung (`AbstractRssRepository.deleteFeed`) và vẫn bao được trường hợp người dùng xóa feed từ Feed Drawer.

Công tắc tính năng: `DataStoreKeys.FeedAutopilotEnabled` (kiểu Boolean, mặc định `true`). Khi tắt, worker định kỳ không tính điểm, chấm báo trên Settings bị ẩn, và màn hình gợi ý hiển thị thông báo tính năng đang tắt kèm nút bật lại.

## 6. Truy vấn dữ liệu (FIX-17)

Không thêm bảng hay cột Room mới. Thêm hai phương thức vào `ArticleDao.kt`:

```kotlin
data class FeedEngagementStat(
    val feedId: String,
    val windowTotal: Int,
    val windowEngaged: Int,
    val oldestUpdateAtMillis: Long?,
)

@Query(
    """
    SELECT
        feedId,
        SUM(CASE WHEN updateAt >= :sinceMillis THEN 1 ELSE 0 END) AS windowTotal,
        SUM(CASE WHEN updateAt >= :sinceMillis
                  AND (readAt IS NOT NULL OR isStarred = 1 OR isReadLater = 1)
                 THEN 1 ELSE 0 END) AS windowEngaged,
        MIN(updateAt) AS oldestUpdateAtMillis
    FROM article
    WHERE accountId = :accountId
    GROUP BY feedId
    """
)
suspend fun queryFeedEngagementStats(accountId: Int, sinceMillis: Long): List<FeedEngagementStat>

@Query("SELECT COUNT(*) FROM article WHERE accountId = :accountId AND feedId = :feedId AND isStarred = 1")
suspend fun queryStarredCountByFeedId(accountId: Int, feedId: String): Int
```

Đặc điểm:
- Chỉ quét metadata, không chọn các cột nội dung `rawDescription` và `fullContent`, tuân thủ nghiêm ngặt FIX-17.
- Tổng và bài tương tác chỉ đếm trong cửa sổ; `MIN(updateAt)` lấy trên mọi bài còn lưu để đo tuổi feed (mục 4.2). `updateAt` là `Date?` trong entity và được `AndroidDatabase.DateConverters` lưu thành số `Long` (mili giây) trong SQLite, nên so sánh trực tiếp với mốc `Long`; test Room phải chứng minh điều này trên SQLite thật, kể cả với bài có `updateAt` rỗng.
- Hàm thứ hai được dùng khi người dùng chọn "Hủy đăng ký ngay", để cảnh báo rõ số bài đã gắn sao sẽ bị xóa vĩnh viễn cùng với feed.

## 7. Chạy nền và thông báo im lặng

### 7.1 Worker định kỳ

Thêm `domain/sv/FeedAutopilotWorker.kt` theo mẫu `BrainRpgDecayWorker.kt`:
- Kế thừa `CoroutineWorker`, inject `FeedAutopilotProcessor`.
- Chu kỳ chạy: 7 ngày một lần (`PeriodicWorkRequestBuilder<FeedAutopilotWorker>(7, TimeUnit.DAYS)`).
- Đặt tên duy nhất: `FeedAutopilotWeeklyWorker`, chính sách `ExistingPeriodicWorkPolicy.KEEP`.
- Đăng ký trong `RApp.workerInit()`.
- Kiểm tra công tắc `Context.feedAutopilotEnabled`: nếu tắt thì bỏ qua và trả về `Result.success()`.
- Nếu tài khoản hiện tại không phải Local: bỏ qua.
- Bộ xử lý tính số lượng feed gây nhiễu và lưu số đếm vào DataStore `DataStoreKeys.FeedAutopilotPendingCount` để hàng Settings hiển thị chấm báo mà không cần chạy truy vấn mỗi lần vẽ.

### 7.2 Thông báo im lặng

- Không sử dụng `NotificationManager`, không tạo kênh thông báo hệ thống và không đòi quyền `POST_NOTIFICATIONS`.
- Chỉ hiển thị một huy hiệu nhỏ (chấm kèm số lượng) tại hàng "Feed Autopilot" trong màn hình Cài đặt khi `pendingCount > 0`.
- Khi người dùng mở màn hình gợi ý, bộ đếm này được đặt lại bằng số gợi ý thực tế còn lại.

## 8. Giao diện và các luồng tương tác

### 8.1 Vị trí mở tính năng

Một hàng trong `SettingsPage`, đặt dưới mục "Hỏi kho lưu trữ" (EXC-11) và trên mục "Trí tuệ nhân tạo (AI)":
- Tiêu đề: "Feed Autopilot".
- Mô tả: "Tự nhận diện và gợi ý hủy đăng ký các nguồn tin bạn hầu như không đọc."
- Biểu tượng: một biểu tượng dọn dẹp có trong thư viện icons đã ghim; tên cụ thể được kiểm tra khi triển khai, chưa xác minh trong spec này.
- Huy hiệu: chấm đỏ/cam kèm số feed gây nhiễu nếu có.
- Route: `RouteName.FEED_AUTOPILOT = "feed_autopilot"`.

### 8.2 Màn hình gợi ý (`FeedAutopilotPage`)

Dựng bằng Jetpack Compose, dùng `BaseScaffold` có nút Back đưa về Cài đặt, bố cục một cột cuộn được và hỗ trợ safe drawing insets:

1. **Phần đầu trang:**
   - Tiêu đề "Feed Autopilot" và mô tả ngắn gọn.
   - Thẻ tóm tắt: ví dụ "Đang phát hiện 3 nguồn tin ít đọc trong 30 ngày qua" hoặc "Kho tin gọn gàng, không có nguồn tin gây nhiễu".
   - Nút công tắc bật/tắt tính năng ngay tại đầu màn hình.
2. **Khi tài khoản không phải Local:**
   - Hiển thị thông báo: "Tính năng này chỉ hỗ trợ tài khoản cục bộ (Local). Tài khoản Fever và Google Reader quản lý nguồn tin trên máy chủ riêng."
3. **Khi thời hạn lưu bài quá ngắn (`keepArchived < 14 ngày`):**
   - Hiển thị thông báo: "Tài khoản của bạn đang đặt thời hạn xóa bài đã đọc dưới 14 ngày. Hãy đặt lại thời hạn lưu dài hơn trong phần Cài đặt tài khoản để tính năng có đủ dữ liệu đánh giá."
4. **Danh sách thẻ gợi ý (nếu có):**
   - Mỗi feed gây nhiễu là một thẻ độc lập gồm: tên feed, biểu tượng (nếu có), số bài nhận được và tỷ lệ tương tác.
   - Dòng lý do bằng số liệu cụ thể (như mô tả ở mục 4.4).
   - Ba nút bấm có nhãn rõ ràng:
     - **"Hủy đăng ký"** (nút nổi bật hoặc màu cảnh báo): mở hộp thoại xác nhận.
     - **"Bỏ qua 30 ngày"** (nút phụ): lưu trạng thái snooze, thẻ biến mất ngay khỏi danh sách.
     - **"Giữ lại"** (nút chữ): lưu trạng thái giữ vĩnh viễn, thẻ biến mất ngay.
5. **Hộp thoại xác nhận hủy đăng ký:**
   - Tiêu đề: "Hủy theo dõi [Tên feed]?"
   - Nội dung: "Hành động này sẽ xóa toàn bộ bài viết của feed khỏi máy và không thể hoàn tác."
   - Nếu feed có bài gắn sao (`starredCount > 0`): thêm dòng cảnh báo nổi bật: "Feed này có [X] bài viết bạn đã gắn sao. Các bài này cũng sẽ bị xóa vĩnh viễn."
   - Hai nút: "Hủy bỏ" và "Xác nhận xóa".
   - Khi xác nhận: gọi `AbstractRssRepository.deleteFeed`, xóa quyết định cũ của feed đó khỏi DataStore (nếu có), và cập nhật lại danh sách trên màn hình.

App tuyệt đối không bao giờ tự động xóa feed mà không qua hộp thoại này.

## 9. Đa ngôn ngữ (Localization)

Mọi chuỗi văn bản mới được thêm đầy đủ vào toàn bộ **38 file locale** duy trì trong thư mục `app/src/main/res/values*/strings.xml`. Tiếng Anh và tiếng Việt được viết tay cẩn thận; 36 ngôn ngữ còn lại sử dụng bản tiếng Anh dự phòng.

Các khóa chuỗi dự kiến:
- `feed_autopilot_title`: Feed Autopilot / Feed Autopilot
- `feed_autopilot_desc`: Identify and clean up noisy feeds you rarely read / Nhận diện và gợi ý hủy theo dõi các nguồn tin ít đọc
- `feed_autopilot_enabled`: Enable Feed Autopilot / Bật Feed Autopilot
- `feed_autopilot_summary_noisy`: Rarely read feeds found: %1$d (last %2$d days) / Nguồn tin ít đọc: %1$d (%2$d ngày qua)
- `feed_autopilot_summary_clean`: Your feeds look tidy. No noisy feeds detected. / Nguồn tin gọn gàng. Không có nguồn gây nhiễu.
- `feed_autopilot_reason_opened`: Opened %1$d of %2$d received (last %3$d days, %4$d%% engagement) / Đã mở %1$d trên %2$d bài nhận được (%3$d ngày qua, tương tác %4$d%%)
- `feed_autopilot_reason_zero`: Opened none of %1$d received (last %2$d days) / Chưa mở bài nào trong %1$d bài nhận được (%2$d ngày qua)
- `feed_autopilot_action_unsubscribe`: Unsubscribe / Hủy theo dõi
- `feed_autopilot_action_snooze`: Snooze for 30 days / Bỏ qua 30 ngày
- `feed_autopilot_action_keep`: Keep and stop suggesting / Giữ lại và không hỏi nữa
- `feed_autopilot_confirm_title`: Unsubscribe from %1$s? / Hủy theo dõi %1$s?
- `feed_autopilot_confirm_message`: This will permanently delete all articles from this feed from your device. This cannot be undone. / Thao tác này sẽ xóa vĩnh viễn toàn bộ bài viết của nguồn này khỏi máy và không thể hoàn tác.
- `feed_autopilot_confirm_starred_warning`: Starred articles that will also be deleted: %1$d / Bài đã gắn sao cũng sẽ bị xóa vĩnh viễn: %1$d
- `feed_autopilot_not_local_account`: Feed Autopilot only supports Local accounts. Remote accounts (Fever, Google Reader) manage feeds on their own server. / Feed Autopilot chỉ hỗ trợ tài khoản cục bộ (Local). Tài khoản từ xa (Fever, Google Reader) quản lý nguồn tin trên máy chủ riêng.
- `feed_autopilot_retention_too_short`: Your account article retention period is under 14 days. Increase it in Account Settings so Autopilot has enough history to analyze safely. / Thời hạn lưu bài của tài khoản đang dưới 14 ngày. Hãy tăng thời hạn trong Cài đặt tài khoản để Autopilot có đủ dữ liệu đánh giá an toàn.
- `feed_autopilot_disabled_message`: Feed Autopilot is turned off. Turn it on to receive feed clean-up suggestions. / Feed Autopilot đang tắt. Hãy bật để nhận gợi ý dọn dẹp nguồn tin.

Câu chữ tránh danh từ đi kèm số đếm để giảm nguy cơ cảnh báo `PluralsCandidate`; lỗi nếu có sẽ được chỉnh sau khi chạy lint thật. Toàn bộ khóa phải được kiểm tra qua `StringResourceParityTest` và không được tạo cảnh báo `UnusedResources` hay `PluralsCandidate` trong Android Lint.

## 10. Kế hoạch kiểm thử

### 10.1 Unit tests (JVM)

- `FeedAutopilotScorerTest`:
  - Feed có < 20 bài: không bị coi là nhiễu dù tỷ lệ mở = 0%.
  - Feed có đủ bài nhưng `MIN(updateAt)` trẻ hơn 14 ngày: không bị coi là nhiễu.
  - Cửa sổ 14 ngày (`keepArchived = For2Weeks`) vẫn có thể gắn cờ một feed đủ tuổi, tức điều kiện tuổi không tự mâu thuẫn với cửa sổ.
  - Bài có `updateAt` trước `READ_AT_INTRODUCED` bị loại khỏi cửa sổ.
  - Feed có bài gắn sao hoặc lưu đọc sau: tính đúng vào `bài_tương_tác`.
  - Feed có tỷ lệ tương tác = 4.9%: bị gắn cờ; tỷ lệ = 5.0%: không bị gắn cờ.
  - Cửa sổ tự co theo `keepArchived`; tài khoản có `keepArchived < 14 ngày` bị từ chối đánh giá.
  - Feed có quyết định `KEEP_FOREVER`: không bao giờ bị gợi ý.
  - Feed có quyết định `SNOOZE_30_DAYS`: bị ẩn trong 29 ngày, xuất hiện lại ở ngày 31.
  - Format câu lý do đúng với `bài_tương_tác = 0` và `bài_tương_tác > 0`.
- `FeedAutopilotDecisionsCodecTest`:
  - Serialize map quyết định sang JSON và parse ngược lại chính xác.
  - Chuỗi JSON hỏng hoặc rỗng: trả về map rỗng an toàn, không crash.
  - Tự động lọc bỏ các quyết định `SNOOZE_30_DAYS` đã hết hạn khi đọc.
  - Khóa của feed không còn tồn tại bị loại khỏi map khi tính điểm.
- `FeedAutopilotViewModelTest`:
  - Chuyển đúng các trạng thái `Loading`, `Clean`, `HasSuggestions`, `NotLocalAccount`, `RetentionTooShort`, `Disabled`.
  - Chọn "Bỏ qua 30 ngày": thẻ biến mất, quyết định được ghi xuống DataStore.
  - Chọn "Giữ lại": thẻ biến mất, quyết định ghi xuống DataStore.
  - Chọn "Hủy đăng ký": mở hộp thoại có số bài gắn sao; khi xác nhận thì gọi hàm xóa và thẻ biến mất.
  - Bật/tắt công tắc phản ánh tức thì vào UI và DataStore.

### 10.2 Room integration tests (Robolectric SQLite Mode Native)

- `ArticleDaoFeedEngagementTest`:
  - `queryFeedEngagementStats` tính đúng `totalArticles`, `engagedArticles` (gồm cả readAt, starred, readLater) và `MIN(updateAt)` theo từng `feedId`.
  - Cô lập theo `accountId`, không lẫn dữ liệu giữa các tài khoản.
  - Bỏ qua các bài có `updateAt < sinceMillis`.
  - `queryStarredCountByFeedId` đếm chính xác số bài gắn sao của một feed cụ thể.

### 10.3 Compose UI tests (Robolectric)

- `FeedAutopilotPageTest`:
  - Hiển thị đúng thẻ gợi ý, câu lý do, và 3 nút hành động.
  - Nút Back gọi đúng callback đưa về Cài đặt.
  - Bấm "Hủy theo dõi" mở hộp thoại xác nhận có cảnh báo bài gắn sao.
  - Bấm "Bỏ qua 30 ngày" làm thẻ biến mất.
  - Trạng thái tài khoản không phải Local và trạng thái thời hạn lưu bài ngắn hiển thị đúng thông báo.

### 10.4 On-Device Instrumentation và Smoke

- Chạy kiểm thử trên thiết bị duy nhất: **Tecno KJ7 `115333744A005844`**.
- Tuyệt đối không chạy lệnh nhắm vào thiết bị khác, không dùng `connectedDevDebugAndroidTest`.
- Trước khi smoke: sao lưu mới `Reader` và `settings.preferences_pb`.
- Chạy một instrumentation test kiểm tra luồng tính điểm trên SQLite của Android thật.
- Khởi động app: nếu gặp quảng cáo toàn màn hình, thực hiện uiautomator dump, kiểm tra foreground `AdActivity`, chạm đúng nút có nhãn để đóng. Bỏ qua banner.
- Thao tác giao diện: vào Cài đặt → mở "Feed Autopilot", kiểm tra các thẻ gợi ý với dữ liệu thật của máy hoặc dữ liệu mẫu, thử các nút bấm và xác nhận không có crash.
- Khôi phục Room và DataStore từ bản sao lưu, kiểm tra `cmp` trùng khớp 100%.

## 11. Các điểm nằm ngoài phạm vi (Out of Scope)

- Không tự động hủy đăng ký mà không có thao tác bấm xác nhận của người dùng.
- Không đo hoặc lưu thời gian đọc của bài viết.
- Không phát hiện hoặc lưu hành vi vuốt bỏ qua riêng lẻ.
- Không hỗ trợ hủy đăng ký một chạm trên tài khoản Fever hoặc Google Reader.
- Không có nút hoàn tác (undo) sau khi đã xác nhận xóa feed (do kiến trúc Room xóa cascade toàn bộ bài viết).
- Không cho người dùng tùy chỉnh con số ngưỡng 5% hay 20 bài trong phiên bản này.
- Không đẩy thông báo hệ thống ra thanh thông báo của máy.
- Không gửi bất kỳ dữ liệu nào ra mạng Internet; không dùng Gemini API hay dịch vụ đám mây.
