# [FIX-15] Hardcode Tiếng Anh "Pillar"/"Detail" Trong `MindMapSheet` — Vi Phạm Quy Tắc Localize

- **Type:** Bug / Localization Defect
- **Priority:** `P2 (Medium)`
- **Estimation:** `1 Story Point`
- **Epic:** 01. FIX — Sửa Lỗi, Ổn Định & Ad Unit
- **Location:** [`ui/page/home/read/MindMapSheet.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/ui/page/home/read/MindMapSheet.kt#L582-L583)

## Vấn đề thực tế
Trong `MindMapSheet.kt`, hàm `NodeDetailCard` (khai báo dòng 553) chứa đoạn `when` sinh nhãn cấp độ node (dòng 578-584 khoảng đó) với 2 nhánh hardcode chuỗi tiếng Anh trực tiếp thay vì qua `stringResource(R.string....)`:
```kotlin
1 -> "Pillar"
else -> "Detail"
```
(dòng 582-583). CLAUDE.md quy định `resourceConfigurations` của app hỗ trợ 6 ngôn ngữ (`en, vi, zh-rCN, ja, fr, de`) và mọi text UI phải được localize qua `strings.xml` tương ứng — feature Mind Map (`ui/component/.../MindMapSheet.kt`) là một tính năng AI mới (Loop 10, theo `git log`), nhưng 2 nhãn cấp độ node ("Pillar" = trụ cột ý chính, "Detail" = chi tiết) lại bị bỏ sót, khiến người dùng dùng app ở ngôn ngữ vi/zh-rCN/ja/fr/de vẫn thấy 2 từ tiếng Anh này chen giữa giao diện đã dịch, phá vỡ trải nghiệm nhất quán ngôn ngữ.

## User Story
> Là người dùng sử dụng ứng dụng ở ngôn ngữ không phải tiếng Anh (ví dụ Tiếng Việt),
> Tôi muốn toàn bộ nhãn trong Mind Map (bao gồm "Pillar"/"Detail") hiển thị đúng ngôn ngữ tôi đã chọn,
> Để trải nghiệm giao diện nhất quán, không bị lẫn từ tiếng Anh không mong muốn.

## Acceptance Criteria (Gherkin)
- **Given** người dùng đặt ngôn ngữ ứng dụng là Tiếng Việt (hoặc zh-rCN, ja, fr, de)
- **When** người dùng mở Mind Map của một bài báo và chạm vào một node để xem `NodeDetailCard`
- **Then** nhãn cấp độ node hiển thị đúng bản dịch tương ứng ngôn ngữ đã chọn (ví dụ Tiếng Việt: "Trụ cột" cho `1 -> "Pillar"`, "Chi tiết" cho `else -> "Detail"`), không còn chuỗi tiếng Anh hardcode
- **And** 2 string resource mới (ví dụ `mind_map_level_pillar`, `mind_map_level_detail`) được thêm vào **cả 6 file** `values/strings.xml`, `values-vi/strings.xml`, `values-zh-rCN/strings.xml`, `values-ja/strings.xml` (nếu tồn tại trong `resourceConfigurations`), `values-fr/strings.xml`, `values-de-rDE/strings.xml` — khớp đúng tên file/qualifier đang dùng trong `app/build.gradle` `resourceConfigurations = ['en','vi','zh-rCN','ja','fr','de']`
- **And** code trong `MindMapSheet.kt` gọi `stringResource(R.string.mind_map_level_pillar)`/`stringResource(R.string.mind_map_level_detail)` thay vì literal string
- **And** rà soát thêm trong cùng file `MindMapSheet.kt` xem còn chuỗi tiếng Anh hardcode nào khác sót lại tương tự (nếu có, xử lý luôn trong cùng task).

## 🔁 Loop Prompt (dùng cho `/loop` hoặc agent thực thi task này)

```
Bạn đang thực hiện task [FIX-15] "Hardcode Tiếng Anh Pillar/Detail Trong MindMapSheet — Vi Phạm Quy Tắc Localize" trong repo RSS Cat Hub (com.mckimquyen.reader, xem CLAUDE.md để hiểu kiến trúc). Đọc kỹ "Vấn đề thực tế" + "Acceptance Criteria" trong file doc/task/todo/FIX-15_mindmap-hardcoded-strings.md trước khi bắt đầu.

Mỗi vòng lặp:
1. Đọc code liên quan tại phần Location, xác nhận vấn đề còn tồn tại (không giả định) — mở MindMapSheet.kt, xác nhận dòng "Pillar"/"Detail" (số dòng có thể lệch nếu file đã đổi), grep toàn file tìm thêm chuỗi tiếng Anh hardcode khác (`grep -n '"[A-Z][a-z]' MindMapSheet.kt` làm gợi ý, không phải tuyệt đối chính xác — đọc kỹ bằng mắt).
2. Implement fix đúng theo Acceptance Criteria: thêm 2 (hoặc nhiều hơn nếu phát hiện thêm) string resource vào app/src/main/res/values/strings.xml và mọi values-*/strings.xml nằm trong resourceConfigurations của app/build.gradle (['en','vi','zh-rCN','ja','fr','de'] — xác nhận lại tên thư mục chính xác: values-vi, values-zh-rCN, values-ja, values-fr-rFR (kiểm tra tên thư mục thực tế có hậu tố -rXX hay không, xem các thư mục values-* hiện có trong app/src/main/res/), values-de-rDE). Dịch nghĩa "Pillar" (trụ cột/ý chính) và "Detail" (chi tiết) sang đúng ngôn ngữ tương ứng — có thể dùng translate_strings.py ở repo root để hỗ trợ nếu phù hợp. Thay literal string trong MindMapSheet.kt bằng stringResource(). Tuân thủ CLAUDE.md.
3. Bước tham khảo AI khác là TUỲ CHỌN cho task nhỏ này (thêm string resource, rủi ro thấp) — có thể bỏ qua nếu thay đổi rõ ràng an toàn.
4. Build kiểm tra: `./gradlew lintDevDebug` (đủ vì chỉ đổi resource/string + 1 lời gọi Composable), có thể chạy thêm `./gradlew assembleDevDebug` để chắc chắn không lỗi biên dịch.
5. Lặp lại tới khi Acceptance Criteria thỏa mãn 100% — kiểm tra đủ cả 6 file strings.xml đã có string mới với key giống nhau.
```

## 🏁 Tín hiệu kết thúc loop (End-Loop Signal)
Chỉ dừng loop khi hoàn tất TẤT CẢ bước sau, đúng thứ tự, KHÔNG bỏ bước:
1. **Audit code changes**: tự review lại toàn bộ `git diff` so với Acceptance Criteria + Definition of Done trong doc/task/README.md. Chấm điểm khách quan trên thang **10** — không tự thổi điểm. Xác nhận cả 6 file `strings.xml` đều có đúng key mới, không thiếu ngôn ngữ nào, và bản dịch hợp lý về ngữ nghĩa (không phải dịch máy vô nghĩa).
2. Bổ sung **unit test**: không bắt buộc (đây là thay đổi resource string thuần tuý), nhưng nếu có logic Kotlin nào chọn string theo level, có thể viết test xác nhận đúng resource ID được chọn theo từng level.
3. Bổ sung **widget/Compose UI test** cho mọi component UI mới hoặc thay đổi hành vi UI (`app/src/androidTest/...`) — test `NodeDetailCard` hiển thị đúng `stringResource` (không hardcode) khi đổi locale, ví dụ set locale vi và assert text hiển thị khớp `R.string.mind_map_level_pillar` đã dịch.
4. Bổ sung **integration test**: không áp dụng.
5. Chạy **smoke test trên device/emulator thật**: `./gradlew installDevDebug`, đổi ngôn ngữ app sang Tiếng Việt trong Settings, mở 1 bài báo, tạo Mind Map, chạm vào 1 node cấp Pillar và 1 node cấp Detail, xác nhận nhãn hiển thị tiếng Việt, ghi lại bằng chứng cụ thể (screenshot hoặc mô tả kết quả quan sát được) — không suy đoán, không báo cáo khống.
6. Nếu điểm audit **> 9/10 VÀ** mọi test bước 2-4 pass **VÀ** smoke test bước 5 xác nhận hoạt động đúng:
   → `git add` các file liên quan → `git commit` với message rõ ràng, đúng Conventional Commits → **`git push`** lên remote nhánh hiện tại. Kết thúc loop, cập nhật trạng thái task (di chuyển file từ `doc/task/todo/` hoặc `inprogress/` sang `doc/task/done/`, đổi tên thêm hậu tố `_DONE` và viết Completion Report ngắn: điểm số, commit hash, danh sách test đã thêm).
7. Nếu điểm **≤ 9/10** hoặc bất kỳ điều kiện bước 2-5 chưa đạt: quay lại bước 1 của vòng lặp Loop Prompt, KHÔNG commit/push.
