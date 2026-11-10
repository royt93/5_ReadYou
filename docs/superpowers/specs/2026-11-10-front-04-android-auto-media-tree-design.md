# FRONT-04 (lát Android Auto) — Cây nội dung hai nhánh và điều khiển chung

**Ngày:** 2026-11-10  
**Trạng thái:** Các phần thiết kế đã được owner duyệt qua AskUserQuestion; owner đã duyệt spec (2026-11-10); đang lập implementation plan.  
**Nhánh:** `dev`, không worktree, không push. Không sửa hoặc commit `.idea`.  
**Phạm vi:** chỉ **Android Auto**. **Wear OS tách khỏi lát này** và giữ nguyên trong `doc/task/todo/FRONT-04_android-auto-and-wear-os.md` (task gốc vẫn mở).

## 1. Mục tiêu và hiện trạng

Màn hình Android Auto liệt kê hai danh sách có thể phát: **Bản tin sáng** (CommuteCast) và **Podcast mới nhất**, và điều khiển phát/tạm dừng/chuyển/tua của bên đang phát hoạt động đúng.

### Hiện trạng đã đọc trong mã (không suy đoán)

- `AndroidManifest.xml` đã khai báo `com.google.android.gms.car.application` trỏ `res/xml/automotive_app_desc.xml` (`<uses name="media" />`) và `CommuteMediaSessionService` (`exported`, `MediaBrowserService`, `MEDIA_PLAY_FROM_SEARCH`).
- `CommuteMediaSessionService` (`MediaBrowserServiceCompat`) hiện trả **một cấp**, một mục `commutecast_latest_episode` (`FLAG_PLAYABLE`). `onPlayFromMediaId` chỉ nhận id đó. Session của nó điều khiển `CommuteAudioPlayer` (TTS).
- `PodcastPlaybackService` là `Service` thường, có `MediaSessionCompat` riêng cho thông báo, **không nằm trong cây**; engine là ExoPlayer qua `PodcastPlaybackController`.
- Không có truy vấn liệt kê tập có audio. Cột `audioUrl`, `audioMime`, `audioDurationSec` có trong `Article`.
- Hai service dùng chung `NOTIFICATION_ID = 9183` (`CommuteMediaSessionService` dòng 379 và `PodcastPlaybackService` dòng 203): thông báo của chúng ghi đè nhau.
- `TtsGate` chỉ bọc `TtsManager` (đọc bài thành tiếng), **không** bọc `CommuteAudioPlayer` (có `TextToSpeech` riêng). Vì vậy `PodcastPlaybackController.play()` và `setOnPlayRequested { pause() }` chỉ nhường với đọc bài; **chưa chiều nào giữa podcast và CommuteCast được nối** (đã sửa 2026-11-10 khi lập plan, sau khi đọc lại `PodcastModule`). `CommuteAudioPlayer` tự yêu cầu audio focus và xử lý mất focus. `ExoPodcastEngine` gọi `setAudioAttributes(..., handleAudioFocus = true)` nên ExoPlayer cũng tự quản lý audio focus (`USAGE_MEDIA`, `CONTENT_TYPE_SPEECH`). Vì vậy hai đường **đều** đã có audio focus riêng; thứ chưa có là sự phối hợp giữa chúng với nhau. Spec không thêm cơ chế audio focus mới.
- `CommuteAutoPackageValidator` chỉ so tên package với danh sách tin cậy; không kiểm chữ ký (đã ghi chú `ponytail` trong mã).
- Môi trường kiểm chứng: Desktop Head Unit 2.0 (arm64) có trong Android SDK trên Mac, Pixel 7 Pro có Android Auto 18.0.664264. Chưa có Android Auto trên Tecno KJ7. Xe thật hoặc head unit thật: không có.

## 2. Quyết định đã duyệt

1. **Kiểm chứng bằng DHU + Pixel**, không hứa xe thật.
2. **Cây hai mục:** Bản tin sáng + **5 tập podcast mới nhất chưa đọc**, từ mọi feed của tài khoản hiện tại.
3. **Giữ `CommuteMediaSessionService` làm cổng duy nhất**; ủy quyền cho hai controller theo tiền tố id.
4. **Phát bên này thì tạm dừng bên kia**; không thêm cơ chế audio focus mới.
5. **Session Auto là đường phát cho cả hai**, phản ánh trạng thái bên đang phát; `PodcastPlaybackService` giữ session riêng cho thông báo; sửa `NOTIFICATION_ID` trùng.

## 3. Cây nội dung

Gốc `commutecast_root` (giữ nguyên hằng số) có hai con duyệt được (`FLAG_BROWSABLE`):

| Id | Tiêu đề | Con |
|---|---|---|
| `commute_root` | Bản tin sáng | tối đa 1 mục phát được: tập CommuteCast mới nhất (id giữ `commutecast_latest_episode`) |
| `podcast_root` | Podcast mới nhất | tối đa **5** mục phát được, id `podcast:<articleId>` |

- Id nhánh và id lá có tiền tố cố định. Id lạ → `onLoadChildren` trả `null` (như hiện nay với `parentId` lạ), `onPlayFromMediaId` bỏ qua.
- **Tập podcast:** bài của tài khoản hiện tại, có `audioUrl` không rỗng, `isUnread = 1`, sắp theo `date DESC, id DESC`, `LIMIT 5`. Chỉ đọc cột nhỏ (`id`, `title`, tên feed, `audioDurationSec`); **không đọc thân bài, mô tả hay URL audio trong cây** (FIX-17). `audioUrl` chỉ được đọc lúc phát, qua đường lấy bài hiện có.
- Mỗi mục có tiêu đề, phụ đề (tên feed), thời lượng nếu có. Ảnh bìa: dùng artwork đã cache của app như CommuteCast hiện nay; không tải ảnh từ mạng trong lát này.
- Danh sách rỗng là kết quả hợp lệ (`emptyList`), không lỗi. Không tự tải thêm hay đồng bộ để lấp.
- "Chưa nghe" dùng cờ chưa đọc của bài, **không** phải vị trí phát riêng. Đây là giới hạn đã nói rõ với owner.

## 4. Điều khiển và đồng bộ trạng thái

- `onPlayFromMediaId(id)`:
  - `commutecast_latest_episode` → hành vi hiện có (`resumeOrPlayLatestEpisode`).
  - `podcast:<articleId>` → kiểm tra id **vẫn nằm trong kết quả truy vấn hiện tại**, lấy `title`, tên feed và `AudioInfo` của đúng bài đó rồi gọi `PodcastPlaybackController.playEpisode(...)`. Không có hoặc không phát được → bỏ qua, ghi log, không ném lỗi.
- Trước khi một bên phát: bên kia tạm dừng. `TtsGate` chỉ bọc `TtsManager`, không bọc `CommuteAudioPlayer`, nên **cả hai chiều cần nối** (cùng một `CommuteGate`): podcast bắt đầu phát phải tạm dừng CommuteCast đang phát, và **CommuteCast bắt đầu phát phải tạm dừng podcast đang phát**, cho mọi đường vào (Auto, màn hình điện thoại, lệnh giọng nói) chứ không chỉ Auto. Không đổi logic phát bên trong hai engine.
- Session Auto phản ánh bên **đang hoạt động gần nhất**: metadata (tiêu đề, nguồn, thời lượng) và `PlaybackStateCompat` (đang phát/tạm dừng/đệm/lỗi, vị trí, tốc độ) lấy từ `PodcastPlaybackState` khi podcast hoạt động, từ `CommutePlayerState` khi CommuteCast hoạt động. Hành động hợp lệ phụ thuộc bên đó: tua theo giây cho podcast (`onFastForward`/`onRewind`/`onSeekTo` → `controller.skip/seekTo`); với CommuteCast giữ hành vi hiện có (tua = chuyển câu, `onSeekTo` không làm gì).
- `onPlay`/`onPause`/`onStop`/`onSkipToNext`/`onSkipToPrevious` được chuyển cho bên đang hoạt động. Khi **không** bên nào hoạt động, `onPlay` và `onPlayFromSearch` giữ hành vi hiện có (phát tập CommuteCast mới nhất); không tự quyết định phát podcast khi nghe lệnh giọng nói mơ hồ.
- `PodcastPlaybackService` giữ session riêng cho thông báo hiện tại. `PodcastPlaybackService.NOTIFICATION_ID` đổi sang giá trị khác `CommuteMediaSessionService.NOTIFICATION_ID`; kiểm bằng test hằng số khác nhau.
- Service đọc trạng thái podcast bằng `PodcastPlaybackController.state` (`StateFlow`); thu thập trong `scope` của service và huỷ ở `onDestroy`.

## 5. Bảo mật, lỗi, vòng đời

- Ranh giới tin cậy giữ nguyên: `CommuteAutoPackageValidator.isTrusted` chỉ so tên package. **Không nâng cấp** trong lát này; ghi vào giới hạn.
- Cây chỉ trả metadata. Không trả URL audio, thân bài, ghi chú, khoá hay dữ liệu tài khoản.
- Id là dữ liệu do client không tin cậy gửi: phải khớp tiền tố và nằm trong kết quả truy vấn vừa tính; không dùng id để ghép đường dẫn, truy vấn thô hay URL.
- Lỗi phát podcast: controller đã có trạng thái `ERROR`; session Auto báo `STATE_ERROR` thay vì giữ trạng thái cũ.
- Truy vấn cây chạy ngoài luồng chính (`result.detach()` rồi `scope.launch`), không giữ `Context`/bitmap mới ngoài `cachedArtwork`, mọi coroutine thuộc `scope` huỷ ở `onDestroy`.
- Không thêm cơ chế audio focus mới. `CommuteAudioPlayer` giữ cách xin và nhả focus hiện có; ExoPlayer giữ `handleAudioFocus = true` hiện có. Hành vi khi Maps, cuộc gọi hoặc nhạc khác chiếm focus do hai cơ chế đó quyết định và **chưa được kiểm** trên DHU (không có Maps thật). Có thể xảy ra việc hai cơ chế focus của hai bên xin/nhả chồng nhau khi đổi bên; test JVM không phát hiện được, chỉ kiểm bằng DHU và Pixel. Ghi vào giới hạn.

## 6. Thay đổi dự kiến

- `domain/model/podcast/` hoặc gần `ArticleDao`: lớp nhỏ `PodcastBrowseItem(id, title, feedName, durationSec)` (không chứa URL).
- `ArticleDao`: một truy vấn `queryLatestUnreadPodcastEpisodes(accountId: Int, limit: Int): List<PodcastBrowseItem>` chỉ chọn cột nhỏ.
- `infrastructure/audio/`: phần thuần (không Android) tách ra để test JVM: tạo cây, phân tách/kiểm id, chọn bên hoạt động, ánh xạ trạng thái sang `PlaybackStateCompat`. `CommuteMediaSessionService` mỏng lại, chỉ nối.
- `PodcastPlaybackService`: đổi `NOTIFICATION_ID`.
- `CommuteAudioPlayer`/service: tạm dừng podcast khi CommuteCast bắt đầu phát, qua **một** interface nhỏ `CommuteGate` đặt cạnh `TtsGate` (`isPlaying`, `pause()`, `setOnPlayRequested`): `PodcastPlaybackController` nhận `CommuteGate` giống cách nhận `TtsGate`, đăng ký callback để tạm dừng khi CommuteCast xin phát và tự gọi `pause()` khi podcast bắt đầu phát; `CommuteAudioPlayer` không phụ thuộc controller (tránh vòng phụ thuộc Hilt).
- Chuỗi mới (tiêu đề hai nhánh) trong **cả 38 locale**, EN và VI viết riêng, parity test.
- Không đổi Room (v20), không migration, không dependency mới. `automotive_app_desc.xml` và manifest không đổi trừ khi test chứng minh cần.

## 7. Kiểm chứng

- **Unit (JVM):** tạo cây; tối đa 5 tập; id đúng tiền tố; id lạ và id không còn trong kết quả bị bỏ qua; ánh xạ `PodcastPlaybackState` → `PlaybackStateCompat` và metadata (kể cả `ERROR`, `PREPARING`); chọn bên hoạt động; quy tắc nhường nhau hai chiều; hằng `NOTIFICATION_ID` khác nhau.
- **Room (Robolectric):** truy vấn chỉ lấy bài có audio và chưa đọc của đúng tài khoản, đúng thứ tự, giới hạn 5, bài đã đọc hoặc không audio bị loại, không chạm cột thân bài.
- **androidTest:** mở rộng `CommuteMediaBrowserIntegrationTest` để duyệt hai nhánh qua `MediaBrowserCompat` thật và gửi `playFromMediaId`. Không chạy bằng `connected*`; dùng `adb -s <serial> shell am instrument` có `timeout`.
- **DHU + Pixel (thủ công):** owner cần bật Developer Mode trong Android Auto và Start head unit server trên Pixel (tôi không đọc được bằng adb). Thấy hai mục trên màn hình xe giả lập, mở từng nhánh, phát một tập podcast, tạm dừng, chuyển sang Bản tin sáng thì bên kia dừng. Ghi nhận trung thực nếu bước nào chưa làm được.
- **Mutation** cho: tiền tố id, giới hạn 5, nhường nhau hai chiều, kiểm id còn trong kết quả, `NOTIFICATION_ID`.
- Full suite `--rerun-tasks`, lint, `assembleDevDebug`, `assembleDevDebugAndroidTest` trước khi chốt. Báo cáo DONE riêng nêu rõ phạm vi, giới hạn và việc chưa kiểm.

## 8. Ngoài phạm vi (nói rõ)

Wear OS (tile, điều khiển từ đồng hồ); ReadNews trong cây hoặc lệnh "phát tin tức tiếp theo" qua Assistant; cơ chế audio focus mới (và bất kỳ thay đổi nào ở cách ExoPlayer xin focus); xác minh chữ ký của client; cây hai cấp theo feed; ảnh bìa từng tập tải từ mạng; điều khiển bằng vô-lăng và giọng nói ngoài hành vi `onPlayFromSearch` hiện có; xe thật hoặc head unit thật. Task gốc `FRONT-04_android-auto-and-wear-os.md` **giữ ở todo**.

## 9. Thứ tự feature đã chọn

Sau FRONT-04 (Android Auto): RPG-02 (câu hỏi AI thật), rồi FRONT-01 (AI Deep Dive). Mỗi feature có brainstorming, spec và plan riêng.

## 10. Tự soát spec

- Không thêm audio focus mới, không đổi engine; nhường nhau dùng một interface nhỏ `CommuteGate` cho cả hai chiều (sửa 2026-11-10: `TtsGate` chỉ bọc `TtsManager`, không phải CommuteCast). Mô tả hiện trạng audio focus đã được đối chiếu lại với mã sau khi viết bản đầu.
- Id từ client được coi là không tin cậy; cây không chứa URL audio.
- Danh sách "chưa nghe" dựa trên cờ chưa đọc, đã nêu là gần đúng.
- Hai MediaSession cùng tồn tại vẫn như hiện nay; chỉ sửa xung đột `NOTIFICATION_ID` và làm session Auto nhất quán. Không hứa hợp nhất.
- DHU phụ thuộc thao tác thủ công của owner; không hứa kết quả trước khi chạy.
- Không placeholder triển khai; Wear OS và xe thật được loại trừ tường minh.
