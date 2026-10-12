# Thiết kế campaign bốn feature RSS Cat Hub

## Mục tiêu

Triển khai bốn feature backlog thành các vertical slice nối tiếp: EXC-01 AI Daily Smart Digest, EXC-08 Second Brain Graph, EXC-03 Bionic Reading, EXC-07 Bilingual Reader. Mỗi feature phải có unit, Compose widget/UI và integration test thực chất trước khi campaign chuyển sang feature kế tiếp.

## Hiện trạng đã xác minh trên nhánh `dev`

- EXC-01 chưa có digest UI hoặc lưu trữ riêng; `GeminiSummaryService` và `AiRequestGateway` đã có thể tái sử dụng.
- EXC-08 chưa có màn hình graph hợp nhất; `StoryClusteringEngine`, `SemanticSearchEngine`, `ArticleMindMapExtractor`, model và các test cho engine đã tồn tại.
- EXC-03 có string và một setting placeholder đang disable; chưa có logic Bionic Reading hoặc preference đang hoạt động.
- EXC-07 chưa có trải nghiệm dịch inline/toàn bài hoặc ML Kit Translation.

## Thiết kế theo feature

### EXC-01 — AI Daily Smart Digest

Thêm luồng vertical slice trên hạ tầng Gemini hiện có. Chọn bài unread gần nhất theo tiêu chí xác định và giới hạn; gửi tiêu đề/tóm tắt/nội dung phù hợp tới service tạo digest có cấu trúc, mỗi claim tham chiếu article IDs đầu vào. Parse và validate output trước khi hiển thị; không cho model tự tạo URL. Lưu digest cùng danh sách article IDs nguồn qua Room để có lịch sử đọc lại. UI vào từ FlowPage, hiển thị loading/error/empty/success và link mở bài nguồn. Test parser/selector/persistence, Compose states và luồng Room → service giả lập → lưu → load.

### EXC-08 — Second Brain Graph

Không tạo engine embedding/cluster mới. Tạo model graph tổng hợp từ cluster, concept và mind-map data hiện có, giới hạn 200 node, xử lý dữ liệu rỗng/thiếu và dedupe. Compose Canvas vẽ graph; slider thời gian lọc timeline; chọn node mở danh sách cluster/bài liên quan theo thời gian. Đo render/graph build cho 200 node; tránh thêm thư viện graph. Test thuật toán hợp nhất/giới hạn/thời gian, widget empty/populated/selection/slider, và integration từ DAO/index hiện có tới model UI.

### EXC-03 — Bionic Reading

Bật preference hiện đang disable. Chuyển đổi text thành AnnotatedString, nhấn mạnh 40% đầu từ theo quy tắc code-point/grapheme an toàn, không sửa nội dung accessibility/copy. Bật/tắt từ ReadingStylePage; truyền trạng thái tới renderer bài đọc. Không làm biến dạng markup, khoảng trắng, punctuation hoặc URL. Test logic từ đơn vị tới Unicode/punctuation, widget toggle và rendering, integration preference → reader.

### EXC-07 — Bilingual Reader

Dịch tới locale app hiện hành. Dùng Google ML Kit on-device Translation; model được tải khi cần, trạng thái tải model/đang dịch/lỗi/không hỗ trợ phải thể hiện rõ. Chạm đoạn để hiển thị translation inline; có toggle toàn bài dạng interleaved. Cache theo nội dung đoạn và target locale trong phạm vi phù hợp; không cache dữ liệu nhạy cảm lâu dài. Hủy tác vụ khi rời bài và đóng Translator. Không hứa latency <500 ms trước khi model có sẵn; ghi nhận rõ giới hạn này trong UI/spec. Test adapter với fake translator (success/error/model download/cancel/unsupported), widget interactions và integration parser/paragraph selection → adapter → rendered state. Nếu ML Kit API thực tế không thể đáp ứng locale app hoặc dependency/size constraint, dừng tại design gate và xin quyết định thay vì âm thầm đổi engine.

## Thứ tự và cổng hoàn thành

Thứ tự: EXC-01 → EXC-08 → EXC-03 → EXC-07. Mỗi vertical slice độc lập; chạy unit + instrumented UI + integration tests tương ứng, `./gradlew assembleDevDebug`, và device smoke test trước khi bắt đầu feature sau. Theo global project rules, trước build/install phải kiểm tra `adb devices`, khóa target duy nhất hoặc hỏi user nếu nhiều device. Không commit/push tự động; chỉ làm khi được yêu cầu rõ ràng.

## Rủi ro/giới hạn

- Digest cần nguồn dữ liệu persisted của bài viết có thể đọc; selector và DB schema phải xác nhận qua DAO hiện hữu trước khi code.
- ML Kit Translation thêm dependency và model tải runtime; kiểm tra phiên bản, API, kích thước, ngôn ngữ hỗ trợ trước khi chốt task code.
- Integration test phải chạy qua boundary thật có thể kiểm soát (Room/Robolectric hoặc instrumentation với fake external service), không gọi dịch vụ Gemini/ML Kit thật trong CI.
- Unit/widget/integration tests có thể không chứng minh được smoke test trên thiết bị; cả hai là cổng riêng.
