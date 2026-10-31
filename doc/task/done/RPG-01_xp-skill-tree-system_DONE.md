# RPG-01 — Skill Tree Constellation UI — DONE

**Hoàn tất:** 2026-10-31 · **Nhánh:** `dev` · **Trạng thái push:** chưa push

## Tính năng
Thay thế danh sách 6 thanh tiến độ danh mục phẳng cũ (`KnowledgeSkillTreeCard`) trên màn hình Brain RPG bằng **Cây Kỹ Năng dạng Chòm Sao (`SkillTreeConstellationCard`)**:
- **Sơ đồ 6 node cố định:** 6 danh mục hiện có (`Tech & AI`, `Science & Nature`, `Business & Finance`, `Health & Lifestyle`, `Philosophy & Ideas`, `General Knowledge`) được bố trí theo dạng chòm sao bất đối xứng 3 cột / 2 hàng, toạ độ chuẩn hoá cố định, không di chuyển theo điểm XP.
- **Đường nối chòm sao (Canvas):** 6 đường nối trang trí mỏng vẽ trên Canvas nằm dưới các node, kết nối các danh mục gần kề.
- **Kích thước & độ sáng theo XP:** đường kính node tỉ lệ thuận với XP hiệu lực (tối thiểu 48 dp, tối đa 72 dp; node 0 XP vẫn hiển thị rõ ràng và có thể chạm).
- **Trạng thái suy thoái (Cognitive Decay):** khi `baseXp > effectiveXp`, node tự động mờ đi (alpha 0.65), hiện chấm đỏ cảnh báo ở góc trên và hiển thị phần trăm XP còn giữ lại (ví dụ: `90%`).
- **Hộp thoại chi tiết:** chạm vào bất kỳ node nào sẽ mở hộp thoại chi tiết (`AlertDialog`) hiển thị biểu tượng, tên danh mục, XP hiệu lực, XP gốc, thanh tiến trình và cảnh báo suy thoái.
- **Hồi phục kỹ năng:** nút "Ôn tập để hồi phục" chỉ xuất hiện trong hộp thoại của danh mục đang bị suy thoái; các danh mục bình thường không có nút này; cơ chế khoá một lượt quảng cáo (`recoveringCategoryId`) được giữ nguyên hoàn toàn.
- **Không thay đổi:** logic tính XP, cách tính level, cơ chế streak, thuật toán suy thoái (`CategoryDecay`), Room schema, cơ chế thưởng quảng cáo đều giữ nguyên 100%.

## Quyết định của owner (AskUserQuestion)
1. **Nâng cấp trực quan thành sơ đồ nhánh**, không thêm luật mở khoá (unlock) hay node con mới để tránh làm vỡ dữ liệu progression của người dùng.
2. **Thay thế trực tiếp thẻ cũ** trên trang `BrainRpgPage`, không tạo màn hình mới hay nút chuyển đổi chế độ xem.
3. Chọn **Bố cục C (Chòm sao / Constellation)** qua visual companion.
4. Xếp 6 node theo **3 cột lệch hàng** cố định, connector nằm sau node, chạm mở hộp thoại chi tiết.
5. Danh mục bị suy thoái hiển thị **sao mờ + chấm cảnh báo đỏ + phần trăm XP bảo lưu**.

## Cấu trúc mã
- `domain/model/rpg/SkillTreeLayout.kt`:
  - `SkillTreeNodeSpec`: toạ độ x, y chuẩn hoá cho 6 node.
  - `SkillTreeEdge`: 6 cặp cạnh cố định.
  - `SkillTreeNodeVisual`: tính toán đường kính, độ mờ/sáng, trạng thái decay và phần trăm bảo lưu (0..100%).
- `ui/page/rpg/SkillTreeConstellationCard.kt`:
  - `SkillTreeConstellationCard`: composable chính vẽ Canvas đường nối + bố trí 6 `ConstellationNode` qua `BoxWithConstraints` + mở `CategoryDetailDialog`.
  - `ConstellationNode`: nút tròn ngữ nghĩa (Role.Button) với icon, nhãn, điểm XP và chấm cảnh báo đỏ khi decay.
  - `CategoryDetailDialog`: hộp thoại Material hiển thị thanh tiến trình và nút hồi phục (nếu decay).
- Điểm thay thế: `BrainRpgPage.kt` đổi gọi `KnowledgeSkillTreeCard` sang `SkillTreeConstellationCard` (giữ nguyên các tham số và callback).
- Đa ngôn ngữ: bổ sung chuỗi chi tiết và trợ năng vào cả 38 thư mục `values*/strings.xml`.

## Kiểm chứng
- **Unit test suite:** `./gradlew testDevDebugUnitTest --rerun` đạt **1.481 test, 0 lỗi, 0 skip**:
  - `SkillTreeLayoutTest` (6 test): 6 danh mục cố định, toạ độ 0..1, cạnh hợp lệ không tự nối, kích thước tăng đơn điệu theo XP, trần min/max, tính đúng phần trăm bảo lưu, xử lý an toàn khi XP=0.
  - 100% các bộ test Brain RPG cũ (`BrainRpgRepositoryTest`, `BrainRpgLegacyImportTest`, `BrainRpgRoomDaoTest`, v.v.) tiếp tục pass.
- **String Resource Parity:** `./gradlew testDevDebugUnitTest --tests "*StringResourceParityTest"` đạt **6/6 test pass**; `processDevDebugResources` pass.
- **Lint:** `./gradlew lintDevDebug --rerun-tasks` đạt **0 lỗi, 2.451 cảnh báo** (không có lỗi hay cảnh báo mới).
- **Build:** `assembleDevDebug` và `assembleDevDebugAndroidTest` pass.
- **Android instrumentation test (trên phần cứng Samsung Galaxy A50 `R58MA6WYRPE`):**
  - `KnowledgeSkillTreeCardWidgetTest` (6 test): pass 6/6 (hiển thị đủ 6 nhãn danh mục, hiển thị XP hiệu lực và phần trăm bảo lưu khi decay, không hiện nút hồi phục bừa bãi, không có nút hồi phục ngoài đồ thị).
  - `SkillTreeConstellationCardTest` (4 test): pass 4/4 (chạm node decay mở dialog có nút hồi phục và gọi đúng callback, chạm node bình thường không có nút hồi phục, nút hồi phục bị vô hiệu hoá khi đang chờ xem quảng cáo, hiển thị đầy đủ thẻ và Canvas đồ thị).
- **Smoke test thực tế (trên Samsung Galaxy A50 `R58MA6WYRPE`):**
  - Mở trang Brain RPG, xác nhận đồ thị chòm sao 6 node hiển thị đúng vị trí bất đối xứng 3 cột / 2 hàng, đường nối Canvas hiển thị phía sau các node.
  - Chạm node `Tech & AI` mở đúng hộp thoại chi tiết với biểu tượng, tên, 0 XP, thanh tiến trình; không có nút hồi phục (do chưa decay).
  - Cuộn trang dọc xác nhận toàn bộ 6 node hiển thị đầy đủ và không bị đè lên các thẻ bên dưới (AI Challenge Stats, Weekly Brain Wrapped).
  - Không gặp quảng cáo toàn màn hình trong suốt quá trình thử nghiệm.
  - Dữ liệu thử nghiệm đã được dọn sạch hoàn toàn; thiết bị được đưa về trạng thái ban đầu.
- **Chuyển đổi thiết bị:** Sau smoke test, owner đã chỉ thị chuyển khoá thiết bị sang **TECNO BG6 (`118743744X002560`)** cho mọi phiên tiếp theo; quy tắc đã được cập nhật vào bộ nhớ dài hạn.

## Giới hạn đã biết (nói thẳng)
1. **Đây là đồ thị trực quan (visualization), không phải cây kỹ năng mở khoá gameplay:** 6 danh mục đều có sẵn từ đầu, không có điều kiện tiên quyết (prerequisite) giữa các node, các đường nối Canvas chỉ mang tính trang trí chòm sao.
2. **Hộp thoại dùng `AlertDialog` thay vì `ModalBottomSheet`:** do phiên bản Material 3 hiện tại của dự án là 1.0.1 (chưa có `ModalBottomSheet` của Material 3); sử dụng `AlertDialog` để tránh thêm thư viện ngoài.
3. **Phần hồi phục qua quảng cáo thưởng chưa được bấm thử trên máy thật:** do nhóm tác vụ Ad/IAP/Money đang bị hoãn theo chỉ thị của owner; logic hồi phục được kiểm chứng qua unit/widget test với callback giả lập.

## Commits trên nhánh `dev`
- `75b3c642`: `feat(rpg-01): model fixed constellation skill layout`
- `0f36446c`: `feat(rpg-01): draw constellation skill tree and detail dialog`
- `8be27af5`: `feat(rpg-01): replace flat category bars with constellation tree`
