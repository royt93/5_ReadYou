# [FIX-21] Sửa 2 lỗi `lintDevDebug` — Báo cáo hoàn thành

- **Trạng thái:** Đã thực thi Inline Execution, kiểm chứng, commit và push lên `origin/dev` (xem lịch sử git cho commit FIX-21).
- **Ngày hoàn thành:** 2026-10-23
- **Audit:** 9/10. `lintDevDebug` chạy thật: **2 error → 0 error**; 2402 warning và 8 information giữ nguyên (không thuộc phạm vi). Hành vi runtime không đổi. Không dùng `@SuppressLint`.

## Bằng chứng trước khi sửa

`./gradlew lintDevDebug` (2026-10-23): BUILD FAILED, 2 error, 2402 warning, 8 information.

1. `MissingOnPlayFromSearch` — `PodcastPlaybackService.kt:61`: `MediaSessionCompat.Callback` ẩn danh chưa override `onPlayFromSearch`; app khai báo Android Auto trong manifest.
2. `ProduceStateDoesNotAssignValue` — `SmartCollectionDetailPage.kt:45`: `value = it` nằm trong lambda con `collect { }` của `produceState`. Code chạy đúng lúc runtime; lint không nhận ra phép gán lồng (suy luận từ kết quả lint và đọc code, chưa kiểm tra mã nguồn detector).

## Thay đổi

- `PodcastPlaybackService.kt`: thêm `import android.os.Bundle` và `override fun onPlayFromSearch(query: String?, extras: Bundle?) = podcast.play()` trong callback hiện có. Theo cùng pattern của `TtsForegroundService` và `CommuteMediaSessionService` (cả hai override trực tiếp trong lớp ẩn danh). Voice search với mọi query phát tập đang chọn. Không thêm intent-filter, service vẫn `exported="false"`: không mở tính năng mới.
- `SmartCollectionDetailPage.kt`: thay `produceState` bằng `remember(collectionId) { viewModel.observeArticles(collectionId) }` + `collectAsState(initial = emptyList())`; đổi import `produceState` → `remember`. `remember(collectionId)` bắt buộc vì `observeArticles` trả `Flow` mới mỗi lần gọi.
- `SmartCollectionDetailPageTest.kt` (mới, Robolectric + Compose): empty state, hiển thị bài, và emission làm UI cập nhật mà không tạo lại Flow (`verify(exactly = 1)` trên `observeArticlesInCollection`).

## Kiểm chứng

- **Không có RED hành vi hợp lệ.** Hai thay đổi giữ nguyên hành vi nên 3 test đặc tả **pass trên cả mã gốc (3/3) và mã đã sửa (3/3)**. RED hợp lệ duy nhất là chính `lintDevDebug` (fail với 2 error trước sửa).
- **Mutation kiểm tra guard `remember`:** tạm bỏ `remember(collectionId)` → test `detailPage_emissionRecomposesWithoutRecreatingObservedFlow` **FAIL** ("3 matching calls found, but needs at least/exactly 1"); khôi phục `remember` → pass. Mutation không được giữ lại.
- **Lint sau sửa:** BUILD SUCCESSFUL, 0 error, 2402 warning, 8 information (tổng 2410 issue).
- **Full unit suite** `./gradlew testDevDebugUnitTest --rerun`: BUILD SUCCESSFUL, **1254 test**, 212 class, 0 failures/errors/skipped (đếm từ XML mới, không `JAVA_TOOL_OPTIONS`).
- `assembleDevDebug` và `compileDevDebugAndroidTestKotlin`: BUILD SUCCESSFUL. `git diff --check` sạch.

## Giới hạn

- **Chưa kiểm chứng `onPlayFromSearch` bằng test hành vi hay thiết bị.** Callback là lớp ẩn danh trong `Service`, không có cách test đáng tin mà không dựng service; lint chứng minh method được override, `PlayFromSearchCallbackTest` bao phủ ngữ nghĩa tương tự của callback dùng chung ở service khác. Android Auto voice search thật chưa được thử (không có xe/DHU).
- Lint vẫn ghi `Can't read API file .../platforms/android-37/data/api-versions.xml` (SDK 37 cục bộ), nên một số kiểm tra API có thể bị thiếu. Chưa điều tra.
- 2402 warning (1872 `Untranslatable`, 342 `TypographyEllipsis`, 64 `UnusedResources`, …) chưa xử lý và không phân loại tác động.
- Không chạy thiết bị.
