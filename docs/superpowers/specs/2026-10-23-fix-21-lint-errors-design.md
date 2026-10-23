# FIX-21 — Sửa 2 lỗi `lintDevDebug`

- Ngày: 2026-10-23
- Trạng thái: thiết kế đã duyệt, thực thi Inline Execution và kiểm chứng (2026-10-23); đã commit và push.
- Không tự commit/push.

## Bằng chứng đầu vào

`./gradlew lintDevDebug` (chạy thật 2026-10-23, exit fail): **2 error, 2402 warning, 8 information**. Báo cáo: `app/build/reports/lint-results-devDebug.xml`. Lint cũng ghi `Can't read API file .../platforms/android-37/data/api-versions.xml` (20 lần), nên kết quả có thể thiếu một số kiểm tra API; chưa điều tra, ngoài phạm vi.

Hai error:

1. `MissingOnPlayFromSearch` — `PodcastPlaybackService.kt:61`. Callback `MediaSessionCompat.Callback` ẩn danh chưa override `onPlayFromSearch`. App khai báo Android Auto (`AndroidManifest.xml:58-60`, `automotive_app_desc`).
2. `ProduceStateDoesNotAssignValue` — `SmartCollectionDetailPage.kt:45`. `value = it` nằm trong lambda con `collect { }` của `produceState`. Code chạy đúng lúc runtime; lint không nhận ra phép gán lồng. Đây là suy luận từ kết quả lint và đọc code, chưa kiểm tra mã nguồn detector.

Tiền lệ trong repo: `PlayFromSearchCallback` (`infrastructure/audio/PlayFromSearchCallback.kt`, `internal open class`) đã dùng cho `TtsForegroundService` và `CommuteMediaSessionService`; test `PlayFromSearchCallbackTest` cover hành vi (mọi query, kể cả rỗng/null, đều gọi `playSingleItem`). `SmartCollectionDetailPage` đã dùng `collectAsState()` ở dòng 43.

## Mục tiêu

`lintDevDebug` không còn 2 error trên, giữ nguyên hành vi hiện có.

## Không thuộc phạm vi

2402 warning (1872 là `Untranslatable`), 8 information, lỗi đọc `api-versions.xml`, intent-filter `MEDIA_PLAY_FROM_SEARCH` hay `MediaBrowserService` cho `PodcastPlaybackService`, hỗ trợ tìm kiếm theo nội dung (query bị bỏ qua), `@SuppressLint`, baseline lint, đổi cấu hình lint trong `build.gradle`.

## Thiết kế

### 1. `PodcastPlaybackService`

**Sửa so với bản đầu:** bản đầu định kế thừa `PlayFromSearchCallback`. Khi lập plan phát hiện `TtsForegroundService:47-50` và `CommuteMediaSessionService:72-74` đều kế thừa lớp đó **và còn override `onPlayFromSearch` trực tiếp**; hai service này không bị lint báo lỗi. Điều này gợi ý (chưa chứng minh) rằng detector cần override ngay trong lớp ẩn danh. Vì vậy chọn cách chắc chắn hơn, theo đúng pattern của hai service kia: thêm override trực tiếp vào callback hiện có, không đổi lớp cha.

Thêm vào `object : MediaSessionCompat.Callback()` trong `PodcastPlaybackService.onCreate`:

```kotlin
override fun onPlayFromSearch(query: String?, extras: Bundle?) = podcast.play()
```

(thêm `import android.os.Bundle`). Các override còn lại giữ nguyên. Ngữ nghĩa: voice search với bất kỳ query nào phát tập đang được chọn, cùng triết lý với `TtsForegroundService`. Không thêm intent-filter, service vẫn `exported="false"`; thay đổi chỉ làm callback đúng chuẩn lint, không mở tính năng mới. Nếu `lintDevDebug` vẫn báo lỗi sau thay đổi này, dừng và điều tra thay vì chuyển sang `@SuppressLint`.

### 2. `SmartCollectionDetailPage`

Thay `produceState` bằng:

```kotlin
val articlesFlow = remember(collectionId) { viewModel.observeArticles(collectionId) }
val articles by articlesFlow.collectAsState(initial = emptyList())
```

`remember(collectionId)` bắt buộc: `observeArticles` trả `Flow` mới ở mỗi lần gọi, nếu không bọc thì `collectAsState` thu thập lại sau mỗi recompose. Bỏ import `produceState` nếu không còn dùng, thêm `remember`/`collectAsState` nếu chưa có.

## Kiểm chứng (TDD)

Hai thay đổi giữ nguyên hành vi, nên test hành vi sẽ **pass trước và sau**; "RED" hợp lệ duy nhất là chính `lintDevDebug` (đang fail vì 2 error). Báo cáo phải nói rõ điều này, không gọi test hành vi là RED.

1. Thêm test đặc tả trước khi sửa (pass trên mã cũ):
   - `SmartCollectionDetailPageTest` (Robolectric + Compose, theo mẫu `SmartCollectionsPageTest`): DAO giả phát danh sách bài, assert tiêu đề bài hiển thị; phát danh sách mới thì giao diện cập nhật; đổi `collectionId` thì chuyển sang flow mới. Kiểm số lần gọi `observeArticles` không tăng theo recompose để bắt lỗi thiếu `remember`.
   - Callback voice search dùng `MediaSessionCompat.Callback` của thư viện Android và override trực tiếp trong anonymous class của service. Regression behavior hiện có là `PlayFromSearchCallbackTest`; khi plan, xác định xem có thể extract callback nhỏ không làm đổi thêm cấu trúc, hoặc ghi rõ lint mới là verification cho method wiring, còn test hiện có bao phủ semantics. Không thêm test reflection/source-text yếu chỉ để khẳng định method được override.
2. Chạy `./gradlew lintDevDebug`: xác nhận **2 error → 0 error**, đếm lại warning (không yêu cầu giảm).
3. `./gradlew testDevDebugUnitTest --rerun` (đếm XML mới), `assembleDevDebug`, `compileDevDebugAndroidTestKotlin`, `git diff --check`.
4. Không chạy thiết bị. Không claim Android Auto voice search hoạt động; chỉ claim lint sạch 2 error và hành vi cũ không đổi.

## Tiêu chí chấp nhận

1. `lintDevDebug` không còn `MissingOnPlayFromSearch` và `ProduceStateDoesNotAssignValue`; báo cáo ghi số error/warning mới và vẫn nêu lỗi `api-versions.xml`.
2. `SmartCollectionDetailPage` không khởi động lại thu thập khi recompose (có test).
3. Full unit suite, build, androidTest compile pass bằng bằng chứng mới.
4. Không `@SuppressLint`, không đổi manifest, không sửa warning khác.
5. Không commit/push khi chưa được yêu cầu.
