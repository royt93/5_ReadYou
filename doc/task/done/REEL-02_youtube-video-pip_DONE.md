# [REEL-02] Trình Xem Video RSS & YouTube PiP Không Quảng Cáo (Picture-in-Picture)

- **Type:** Media / Video RSS
- **Priority:** `P2 (Medium)`
- **Estimation:** `5 Story Points`
- **Epic:** [09. REEL — Tin Tức Thị Giác & Cảnh Báo Khẩn (Visual Reels & Media)](09_VISUAL_REELS_AND_MEDIA.md)
- **Location:** `app/src/main/java/com/mckimquyen/reader/infrastructure/media/video/` (Gói mới)

## Vấn đề thực tế
Rất nhiều kênh tin tức đính kèm video hoặc kênh YouTube có cấp feed RSS. Khi bấm vào video, người dùng bị văng sang app YouTube hoặc xem trên webview nặng nề nhiều quảng cáo rác.

**Xác nhận qua audit code (2026-09-06):** không tồn tại package `infrastructure/media/video/` hay bất kỳ tích hợp ExoPlayer/PiP nào cho video nhúng trong repo (`find app/src/main -iname "*video*" -o -iname "*exoplayer*" -o -iname "*pip*"` không trả về implementation nào ngoài file task doc này; không có dependency ExoPlayer/Media3 nào trong `app/build.gradle`). Task **chưa được implement**.

## User Story
> Là người theo dõi các kênh tin tức video (TED Talks, VTV24, Kurzgesagt, Bloomberg),
> Tôi muốn xem video ngay trong ứng dụng với khung hình nhỏ nổi (Picture-in-Picture),
> Để tôi vừa có thể xem video vừa tiếp tục lướt danh sách bài đọc khác.

## Acceptance Criteria (Gherkin)
- **Given** bài viết có nhúng video YouTube hoặc thẻ video MP4
- **When** người dùng ấn phát video
- **Then** video phát mượt mà không quảng cáo rác thông qua Native Player / ExoPlayer
- **And** khi người dùng vuốt back hoặc chuyển trang, video tự động thu nhỏ thành cửa sổ nổi Picture-in-Picture (PiP) ở góc màn hình.

## 🔁 Loop Prompt (dùng cho `/loop` hoặc agent thực thi task này)

```
Bạn đang thực hiện task [REEL-02] "Trình Xem Video RSS & YouTube PiP Không Quảng Cáo" trong repo RSS Cat Hub (com.mckimquyen.reader, xem CLAUDE.md để hiểu kiến trúc). Đọc kỹ "Vấn đề thực tế" + "Acceptance Criteria" trong file doc/task/todo/REEL-02_youtube-video-pip.md trước khi bắt đầu.

Mỗi vòng lặp:
1. Đọc code liên quan tại phần Location, xác nhận vấn đề còn tồn tại (không giả định).
2. Implement fix/feature đúng theo Acceptance Criteria. Tuân thủ CLAUDE.md: phân tầng domain/infrastructure/ui, mọi I/O nặng chạy Dispatchers.IO/Default (không block Main), không lưu Context/Activity/LazyListState vào ViewModel hay singleton, localize đủ 6 ngôn ngữ (en, vi, zh-rCN, ja, fr, de) nếu có text UI mới.
3. Với thay đổi kiến trúc/thiết kế quan trọng (không bắt buộc cho fix nhỏ, 1-2 dòng), tham khảo ý kiến độc lập từ 2 AI agent khác trước khi chốt:
   - `codex exec -s workspace-write "Review approach cho task [REEL-02] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có."`
   - `claude -p "Review approach cho task [REEL-02] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có." --allowedTools "Read Grep Glob"`
   Đối chiếu góp ý, chỉ áp dụng nếu hợp lý — không áp dụng máy móc, không để agent ngoài tự sửa code của bạn.
4. Build kiểm tra: `./gradlew assembleDevDebug` (hoặc `lintDevDebug` nếu chỉ đổi resource/string).
5. Lặp lại tới khi Acceptance Criteria thỏa mãn 100%.

## 🏁 Tín hiệu kết thúc loop (End-Loop Signal)
Chỉ dừng loop khi hoàn tất TẤT CẢ bước sau, đúng thứ tự, KHÔNG bỏ bước:
1. **Audit code changes**: tự review lại toàn bộ `git diff` so với Acceptance Criteria + Definition of Done trong doc/task/README.md. Chấm điểm khách quan trên thang **10** — không tự thổi điểm, nếu có test giả/mock rỗng/logic chưa đúng thì điểm phải phản ánh đúng thực tế.
2. Bổ sung **unit test** cho mọi nhánh logic mới (`app/src/test/...`), phủ cả edge case (rỗng, lỗi mạng, dữ liệu null, giới hạn biên).
3. Bổ sung **widget/Compose UI test** cho mọi component UI mới hoặc thay đổi hành vi UI (`app/src/androidTest/...`).
4. Bổ sung **integration test** cho luồng end-to-end liên quan (DB + repository + worker nếu có liên quan).
5. Chạy **smoke test trên device/emulator thật**: `./gradlew installDevDebug`, thao tác thủ công đúng luồng vừa sửa, ghi lại bằng chứng cụ thể (log logcat hoặc mô tả kết quả quan sát được) chứng minh hoạt động đúng — không suy đoán, không báo cáo khống.
6. Nếu điểm audit **> 9/10 VÀ** mọi test bước 2-4 pass **VÀ** smoke test bước 5 xác nhận hoạt động đúng:
   → `git add` các file liên quan → `git commit` với message rõ ràng, đúng Conventional Commits → **`git push`** lên remote nhánh hiện tại. Kết thúc loop, cập nhật trạng thái task (di chuyển file từ `doc/task/todo/` hoặc `inprogress/` sang `doc/task/done/`, đổi tên thêm hậu tố `_DONE` và viết Completion Report ngắn: điểm số, commit hash, danh sách test đã thêm).
7. Nếu điểm **≤ 9/10** hoặc bất kỳ điều kiện bước 2-5 chưa đạt: quay lại bước 1 của vòng lặp Loop Prompt, KHÔNG commit/push.
```

## ✅ Completion Report (2026-09-27)

**Điểm audit: 9.2/10** — Acceptance Criteria thỏa mãn (có một sai lệch cố ý với đề bài, nêu rõ bên dưới):
- ✅ Video MP4/HLS nhúng trong bài phát bằng **ExoPlayer native, không quảng cáo**: nhánh `"video"` trong `HtmlToComposable.kt` trước đây là `// not implemented yet`, nay render `ArticleVideoPlayer` thật.
- ✅ Tự thu nhỏ thành Picture-in-Picture khi rời trang: `MainActivity.onUserLeaveHint()` đọc `VideoPipController.playing` → `VideoPipHelper.enterPip()`.
- ✅ **Autoplay tắt tiếng** (theo quyết định của người dùng): video tự chạy ở `volume = 0f`, kèm toggle `ReadingVideoAutoplayPref` trong Settings → Reading Style, localize đủ 6 ngôn ngữ. Tắt autoplay thì video đứng yên và PiP không kích hoạt.
- ⚠️ **Sai lệch cố ý với đề bài**: YouTube **không** phát qua ExoPlayer. Phát YouTube bằng player của bên thứ ba là bỏ qua quảng cáo của họ, vi phạm YouTube ToS. Iframe YouTube giữ nguyên hành vi cũ: thumbnail → mở app/trình duyệt ngoài. `ArticleVideoExtractor` chủ động loại iframe YouTube khỏi danh sách video phát được, có test khẳng định điều này.
- −0.8 cho sai lệch YouTube nói trên (có lý do chính đáng nhưng vẫn là không khớp 100% chữ trong đề bài) và cho việc chỉ đọc video từ HTML bài viết.

**Bug thật tìm được và đã sửa trong quá trình làm:**
1. `VideoPipHelper.aspectRatioFor()` clamp sai: dùng `.toInt()` trên float nhân hệ số, khiến tỉ lệ kẹp ra **ngoài** dải Android chấp nhận (`1/2.39` truncate thành `0.418 < 0.41841`). Với video cực cao/cực rộng sẽ ném `IllegalArgumentException` và **kéo sập activity** khi vào PiP. Sửa bằng hằng số nguyên chính xác `238:100`. Phát hiện qua unit test biên, không phải qua chạy tay.
2. `ReadingVideoAutoplayPref.values = listOf(ON, OFF)` trả về `[ON, null]` — `default = ON` kích hoạt khởi tạo companion trước khi object `OFF` sẵn sàng. Đã xoá `values` vì switch không dùng đến. **Lưu ý cho sau này: ~25 file `*Pref.kt` khác trong repo có cùng thứ tự khai báo rủi ro này** (xem mục Giới hạn).

**Tests thêm:**
- Unit (`ArticleVideoExtractorTest`, 12 case): `<video src>`, `<source>` lồng nhau, resolve URL root-relative, khử trùng lặp, HLS/DASH, **loại iframe YouTube**, HTML rỗng/rác, `isPlayable` với query string/fragment/mime type.
- Unit (`VideoPipHelperTest`): tỉ lệ landscape/portrait giữ nguyên, clamp ultra-wide/ultra-tall nằm trong dải hợp lệ, fallback 16:9 khi `width/height <= 0` (ExoPlayer báo 0x0 trước khung hình đầu), biên được bảo toàn, `isPipSupported`.
- Unit (`ReadingVideoAutoplayPrefTest`): default ON, đọc lại lựa chọn đã lưu, pref chưa đặt rơi về default, `not()` lật hai chiều.
- Integration (`ReelsAndVideoIntegrationTest`, Pixel 7 Pro): ExoPlayer thật init/release không để lại PiP marker; `ArticleVideoPlayer` attach/detach sạch; PiP params hợp lệ với 5 kích thước kể cả bệnh lý (0x0, 4000x500, 500x4000).
- Integration (`ArticleVideoPlaybackTest`, Pixel 7 Pro — **test quyết định**): lái player thật và assert bằng dữ liệu thay vì điểm ảnh —
  - `PlayerView` vào được view hierarchy, có player gắn vào, được layout với kích thước > 0;
  - **video thực sự phát** (`onIsPlayingChanged(true)` fire trong 30s);
  - **PiP được arm khi phát** (`VideoPipController.playing != null`), tỉ lệ nằm trong `1/2.39 .. 2.39` nên không thể crash `enterPictureInPictureMode`;
  - autoplay **bắt đầu ở trạng thái tắt tiếng** (`volume == 0f`);
  - tắt autoplay thì sau 5s video **vẫn đứng yên** và PiP **vẫn disarmed**;
  - rời trang → marker được clear, không rò rỉ.

**Smoke test (Pixel 7 Pro `2B051FDH3006MU`, 2026-09-27):**
- Logcat bài có `<video>`: `ExoPlayerImpl: Init [AndroidXMedia3/1.4.1]` → `DMCodecAdapterFactory: Creating an asynchronous MediaCodec adapter for track type video` → `CCodecConfig: read media type: video/avc` → `output.media-type.value = "video/raw"` → `[c2.exynos.h264.decoder] setting surface generation`. Giải mã phần cứng thật, không phải placeholder.
- `dumpsys SurfaceFlinger --list` xác nhận `SurfaceView[com.mckimquyen.reader/...MainActivity](BLAST)` — PlayerView gắn surface thành công.
- Bằng chứng gián tiếp player mở kết nối mạng thật: URL mẫu cũ của Google trả `HttpDataSource$InvalidResponseCodeException: Response code: 403`; đổi sang `https://download.samplelib.com/mp4/sample-5s.mp4` (curl xác nhận 200) thì hết lỗi.
- 15/15 test trên thiết bị pass (`ArticleVideoPlaybackTest`, `ReelsAndVideoIntegrationTest`, `ReelsCardWidgetTest`, `ReelsCloseButtonTest`).

**Ghi chú trung thực về phương pháp xác minh:** không bấm được nút Play bằng `adb` vì `uiautomator dump` liên tục báo `could not get idle state` (widget quiz trên trang đọc animate không ngừng), và controls của `PlayerView` là View truyền thống trong `AndroidView` nên không phơi node `exo_play_pause` ra dump. Thay vì bấm mò toạ độ, đã viết `ArticleVideoPlaybackTest` lái thẳng player và assert trên `VideoPipController` — chính là hợp đồng thật giữa player và `MainActivity.onUserLeaveHint()`.

**Giới hạn đã biết:**
- **YouTube không phát trong app** (ToS) — giữ hành vi mở ngoài.
- **Chỉ đọc video từ HTML bài viết** (`fullContent ?: rawDescription`). Enclosure `type="video/mp4"` cấp feed chưa hỗ trợ: cần thêm `Article.videoUrl` + migration Room v11→v12, không đáng rủi ro ở task này.
- **Nợ kỹ thuật phát hiện thêm**: khoảng 25 file `*Pref.kt` khai báo `val default` **trước** `val values = listOf(...)` trong cùng companion — cùng dạng với bug #2 nên `values` của chúng nhiều khả năng cũng chứa `null`. Chưa sửa vì ngoài phạm vi REEL-02 và chưa rõ chỗ nào đọc `values` thật. Nên mở task riêng để quét và sửa.
