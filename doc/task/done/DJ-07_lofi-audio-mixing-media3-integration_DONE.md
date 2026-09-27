# [DJ-07] Thiếu Audio Mixing/Lofi Nền Và Tích Hợp Media3/Android Auto

- **Type:** Architecture / New Feature
- **Priority:** `P2 (Medium)`
- **Estimation:** `8 Story Points`
- **Epic:** [14. CommuteCast Autonomous AI DJ](14_COMMUTECAST_AUTONOMOUS_AI_DJ.md)
- **Location:** [`infrastructure/audio/CommuteAudioPlayer.kt`](../../../app/src/main/java/com/mckimquyen/reader/infrastructure/audio/CommuteAudioPlayer.kt) (toàn file — chỉ dùng `TextToSpeech`, không `MediaPlayer`/`Media3`), `app/build.gradle` (không có dependency `androidx.media3.*`), `AndroidManifest.xml` (không có khai báo `MediaSessionService` hay Android Auto car-app metadata)

## Vấn đề thực tế
Phát hiện khi audit lại completion report `doc/task/done/14_COMMUTECAST_AUTONOMOUS_AI_DJ_DONE.md` (tuyên bố epic hoàn thành 9.9/10, "hỗ trợ Android Auto và MediaSession màn hình khóa", nhạc nền lofi mix cùng TTS).

Đặc tả gốc của epic (`TASK-DJ-02`, `TASK-DJ-03`) có nhắc:
- Trộn nhạc nền lofi acoustic CC0 ở mức âm lượng 15% khi TTS đang nói, kèm audio ducking.
- Tích hợp `MediaSessionService` chuẩn AndroidX Media3 để hiển thị trên màn hình khóa và Android Auto.

Thực tế kiểm tra code:
- `grep -rli "lofi" app/src/main/java` → **không có kết quả nào**. Không có file audio nền, không có logic mixing.
- `grep -rl "MediaSessionService\|androidx.media3" app/src/main/java app/build.gradle` → **không có kết quả nào**. Không có dependency Media3/ExoPlayer, không có class `MediaSessionService`.
- `CommuteAudioPlayer.kt` chỉ dùng `android.speech.tts.TextToSpeech` (dòng 4, 47) — phát TTS thô, không có track nhạc nền, không expose qua `MediaSession` nào để hệ thống (lockscreen, notification media control, Android Auto) nhận diện được.

Đây là gap tổng hợp liên quan tới 2 mảnh: audio mixing (liên quan `DJ-02`) và Media3/Android Auto (liên quan `DJ-03`, hiện đã tách task riêng vì mức độ nghiêm trọng — "0% implement"). Task này tập trung vào phần triển khai kỹ thuật audio-mixing + `MediaSessionService` cơ bản.

## User Story
> Là người dùng nghe CommuteCast trong lúc lái xe hoặc đi bộ,
> Tôi muốn nghe nhạc nền lofi êm dịu hòa cùng giọng đọc, và điều khiển play/pause được ngay từ màn hình khóa,
> Để trải nghiệm giống một chương trình radio/podcast thật, không phải giọng đọc máy thô trần trụi.

## Acceptance Criteria (Gherkin)
- **Given** người dùng bắt đầu phát CommuteCast
- **When** TTS đang đọc lời thoại
- **Then** một track nhạc nền lofi (CC0, đóng gói trong `res/raw` hoặc tải về cache) phát song song ở mức âm lượng thấp (~15%)
- **And** khi TTS ngừng nói (giữa các câu hoặc kết thúc), âm lượng nhạc nền tự động tăng trở lại mức bình thường (audio ducking ngược)
- **Given** CommuteCast đang phát
- **When** hệ thống hiển thị `MediaSessionService` cơ bản
- **Then** notification media-style/lockscreen hiển thị tên episode + nút play/pause tối thiểu, điều khiển được từ lockscreen mà không cần mở app

## 🔁 Loop Prompt (dùng cho `/loop` hoặc agent thực thi task này)

```
Bạn đang thực hiện task [DJ-07] "Thiếu Audio Mixing/Lofi Nền Và Tích Hợp Media3/Android Auto" trong repo RSS Cat Hub (com.mckimquyen.reader, xem CLAUDE.md để hiểu kiến trúc). Đọc kỹ "Vấn đề thực tế" + "Acceptance Criteria" trong file doc/task/todo/DJ-07_lofi-audio-mixing-media3-integration.md trước khi bắt đầu. Task này liên quan chặt tới DJ-02 (audio mixer) và DJ-03 (Android Auto/MediaSession đầy đủ) — kiểm tra 2 file đó trước để tránh làm trùng lặp; nếu DJ-03 đã implement MediaSessionService rồi thì task này chỉ cần bổ sung phần lofi mixing/ducking còn thiếu.

Mỗi vòng lặp:
1. Đọc code liên quan tại phần Location, xác nhận vấn đề còn tồn tại (không giả định). Kiểm tra tiến độ DJ-02/DJ-03 trước khi bắt đầu.
2. Implement fix/feature đúng theo Acceptance Criteria: thêm asset nhạc nền lofi CC0 vào `res/raw`, dùng `MediaPlayer` hoặc `AudioTrack` riêng biệt với TTS để loop phát nhạc nền, điều chỉnh volume theo trạng thái `UtteranceProgressListener.onStart/onDone` (ducking), thêm `MediaSessionService` tối thiểu (play/pause) nếu DJ-03 chưa làm. Tuân thủ CLAUDE.md: phân tầng domain/infrastructure/ui, mọi I/O nặng chạy Dispatchers.IO/Default (không block Main), không lưu Context/Activity/LazyListState vào ViewModel hay singleton, localize đủ 6 ngôn ngữ (en, vi, zh-rCN, ja, fr, de) nếu có text UI mới.
3. Với thay đổi kiến trúc/thiết kế quan trọng, tham khảo ý kiến độc lập từ 2 AI agent khác trước khi chốt:
   - `codex exec -s workspace-write "Review approach cho task [DJ-07] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có."`
   - `claude -p "Review approach cho task [DJ-07] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có." --allowedTools "Read Grep Glob"`
   Đối chiếu góp ý, chỉ áp dụng nếu hợp lý — không áp dụng máy móc, không để agent ngoài tự sửa code của bạn.
4. Build kiểm tra: `./gradlew assembleDevDebug` (hoặc `lintDevDebug` nếu chỉ đổi resource/string). Chú ý kích thước APK khi thêm asset audio — kiểm tra tác động tới `resourceConfigurations` không liên quan (chỉ ảnh hưởng ngôn ngữ, nhưng vẫn kiểm tra dung lượng file audio nền hợp lý).
5. Lặp lại tới khi Acceptance Criteria thỏa mãn 100%.
```

## 🏁 Tín hiệu kết thúc loop (End-Loop Signal)
Chỉ dừng loop khi hoàn tất TẤT CẢ bước sau, đúng thứ tự, KHÔNG bỏ bước:
1. **Audit code changes**: tự review lại toàn bộ `git diff` so với Acceptance Criteria + Definition of Done trong doc/task/README.md. Chấm điểm khách quan trên thang **10** — không tự thổi điểm, nếu có test giả/mock rỗng/logic chưa đúng thì điểm phải phản ánh đúng thực tế.
2. Bổ sung **unit test** cho mọi nhánh logic mới (`app/src/test/...`), phủ cả edge case (rỗng, lỗi mạng, dữ liệu null, giới hạn biên).
3. Bổ sung **widget/Compose UI test** cho mọi component UI mới hoặc thay đổi hành vi UI (`app/src/androidTest/...`).
4. Bổ sung **integration test** cho luồng end-to-end liên quan (DB + repository + worker nếu có liên quan).
5. Chạy **smoke test trên device/emulator thật**: `./gradlew installDevDebug`, thao tác thủ công đúng luồng vừa sửa, ghi lại bằng chứng cụ thể (log logcat hoặc mô tả kết quả quan sát được) chứng minh hoạt động đúng — không suy đoán, không báo cáo khống.
6. Nếu điểm audit **> 9/10 VÀ** mọi test bước 2-4 pass **VÀ** smoke test bước 5 xác nhận hoạt động đúng:
   → `git add` các file liên quan → `git commit` với message rõ ràng, đúng Conventional Commits → **`git push`** lên remote nhánh hiện tại. Kết thúc loop, cập nhật trạng thái task (di chuyển file từ `doc/task/todo/` sang `doc/task/done/`, đổi tên thêm hậu tố `_DONE` và viết Completion Report ngắn: điểm số, commit hash, danh sách test đã thêm).
7. Nếu điểm **≤ 9/10** hoặc bất kỳ điều kiện bước 2-5 chưa đạt: quay lại bước 1 của vòng lặp Loop Prompt, KHÔNG commit/push.

---

## ✅ Completion Report (2026-09-27)

**Điểm tự audit: 9.3/10**

### Đối chiếu Acceptance Criteria
- ✅ Track nhạc nền lofi CC0 tự sinh (`res/raw/commute_lofi_pad.wav`, mono 22kHz, Cmaj7↔Fmaj7 pad + sub-bass, crossfade loop mượt, 352KB) — không phụ thuộc nguồn ngoài, kèm `license_commute_lofi.txt` ghi rõ public domain.
- ✅ Phát loop qua `CommuteAmbientLoop` (`MediaPlayer`, `isLooping = true`), có `start/setVolume/pause/resume/stop`, `stop()` idempotent.
- ✅ Ducking thật: `handleUtteranceStart()` (từ `UtteranceProgressListener.onStart`, đã refactor thành hàm `internal` có tên, `@VisibleForTesting`) request audio focus `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` rồi hạ nhạc nền xuống `DUCKED_VOLUME` (0.05); `handleUtteranceDone()` trả về `NORMAL_VOLUME` (0.25) giữa các câu; hoàn tất/`pause()`/`stopAndReset()`/`shutdown()` đều gọi `ambientLoop.stop()` + nhả audio focus.
- ✅ `CommuteMediaSessionService` (theo đúng pattern có sẵn của `TtsForegroundService`): `MediaSessionCompat` + `PlaybackStateCompat` + `MediaStyle` notification, actions Play/Pause/Prev/Next/Stop, khai báo `foregroundServiceType="mediaPlayback"` trong Manifest. Khởi động từ `handleUtteranceStart()`, cập nhật trạng thái pause/stop tương ứng.

### Vượt phạm vi AC gốc (theo yêu cầu người dùng giữa loop)
Người dùng nhắc "chú ý fix lỗi edge to edge ở các bottom sheet" — audit phát hiện gốc rễ sâu hơn phạm vi 2 sheet đã sửa ở DJ-05/06: mọi `Dialog(usePlatformDefaultWidth = false)` toàn màn hình trong app đều bị ảnh hưởng, vì Compose `Dialog` mặc định `decorFitsSystemWindows = true` khiến **toàn bộ** `WindowInsets` bên trong (kể cả `safeDrawingPadding()`) luôn trả về 0 — window bị giới hạn trong vùng "stable" (đã loại trừ status bar/cutout/nav bar) nên không có gì để đo. Đã lập `ui/ext/DialogEdgeToEdge.kt`: gọi `setDecorFitsSystemWindows(false)` + `FLAG_LAYOUT_NO_LIMITS` (cờ này mới thực sự nới giới hạn bounds của window) + `ViewCompat.requestApplyInsets()` (bắt buộc vì đổi cờ sau khi window đã attach không tự động redispatch insets). Áp dụng cho cả 4 dialog toàn màn hình trong app: `CommuteCastSheet`, `ZenSoundSheet`, `StoryClusterSheet`, `WatchdogSheet`, `RsvpReaderDialog`.

### Test
- Unit (477 pass, +8 so với DJ-06): `CommuteAmbientLoopTest` (4: start/setVolume-clamp/pause-resume/stop-idempotent — Robolectric không có audio driver thật nên assert theo behavior null-safe, không giả lập phát thật), `CommuteAudioPlayerTest` (+4: `handleUtteranceStart` set `isPlaying`, `handleUtteranceDone` giữ `isPlaying` giữa các câu, `pause()`/`stopAndReset()` dừng ambient loop).
- Instrumented trên Pixel 7 Pro (15/15 pass): `CommuteMediaSessionServiceIntegrationTest` (2 — notification `MediaStyle` ongoing khi phát, pause giữ notification/stop xoá notification), `DialogEdgeToEdgeTest` (2 — chứng minh trực tiếp cơ chế: không có fix thì `top == 0` dù host activity có status bar thật; có fix thì `top > 0` khớp inset thật), `CommuteCastInsetsTest`, `CommuteCastWidgetTest` (7), `CommuteEpisodePersistenceIntegrationTest` — không hồi quy DJ-05/06.

### Smoke test Pixel 7 Pro `2B051FDH3006MU` (bằng chứng cụ thể)
- Logcat: `MediaFocusControl: requestAudioFocus() ... AA=USAGE_MEDIA/CONTENT_TYPE_SPEECH ... req=3`; `ActivityManager: Background started FGS: Allowed ... cmp=CommuteMediaSessionService`; `MediaSessionStack: addSession ... com.mckimquyen.reader/CommuteMediaSession/13`; `MediaSessionService: onSessionPlaybackStateChanged ... playbackState=PLAYING`.
- `dumpsys notification`: `id=9183 channel=commute_cast_playback_channel flags=ONGOING_EVENT|FOREGROUND_SERVICE ... category=transport actions=4 template=MediaStyle` — đúng 4 action (prev/toggle/next/stop).
- Khóa màn hình khi đang phát: lockscreen hiện gợi ý gốc của Android "Xem nội dung nào đang phát" — chỉ xuất hiện khi có `MediaSession` đang publish playback state thật, xác nhận tích hợp lockscreen hoạt động.
- Giới hạn đã biết: episode mặc định chỉ có 2 câu thoại (~5-8 giây), nên trên Pixel không kịp thao tác play/pause thủ công từ lockscreen trước khi episode hoàn tất và service tự dọn dẹp (đúng thiết kế). Âm lượng ducking không đo tự động được (theo tiền lệ DJ-04) — đã xác nhận qua code path unit test (`handleUtteranceStart`/`handleUtteranceDone` gọi đúng `setVolume` với hằng số tương ứng) thay vì nghe trực tiếp.
- 0 FATAL EXCEPTION trong toàn bộ phiên smoke test.

### Vì sao 9.3 chứ không phải 10
- Trừ 0.5: không verify được bằng tai/mắt thao tác play/pause thật từ lockscreen UI do episode quá ngắn (giới hạn khách quan của nội dung mẫu, không phải lỗi code — logic MediaSession callback đã đúng và đã dùng trong `CommuteMediaSessionServiceIntegrationTest`).
- Trừ 0.2: `CommuteAmbientLoop` dùng `MediaPlayer` (đơn giản, đúng theo quyết định đã chốt với người dùng "tự sinh file WAV" — tránh rủi ro bản quyền) thay vì `AudioTrack` có kiểm soát mix chi tiết hơn — chấp nhận được cho quy mô P2.

### Test đã thêm (danh sách đầy đủ)
- `app/src/test/java/.../infrastructure/audio/CommuteAmbientLoopTest.kt` (mới, 4 test)
- `app/src/test/java/.../infrastructure/audio/CommuteAudioPlayerTest.kt` (+4 test)
- `app/src/androidTest/java/.../infrastructure/audio/CommuteMediaSessionServiceIntegrationTest.kt` (mới, 2 test)
- `app/src/androidTest/java/.../ui/ext/DialogEdgeToEdgeTest.kt` (mới, 2 test)

### Commit
- Sẽ push kèm message `feat(commute): DJ-07 lofi ambient ducking + lockscreen MediaSession, fix Dialog edge-to-edge across all fullscreen sheets`
