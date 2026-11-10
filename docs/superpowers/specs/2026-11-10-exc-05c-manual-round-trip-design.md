# EXC-05c — Hai lượt đồng bộ thủ công và đổi vai

**Ngày:** 2026-11-10  
**Trạng thái:** Các phần thiết kế đã được owner duyệt qua AskUserQuestion; bản spec này chờ owner duyệt trước implementation plan.  
**Nhánh:** `dev`, không worktree, không push. Không sửa hoặc commit `.idea`.

## 1. Mục tiêu và ranh giới

Nối hai lượt đồng bộ đã có thành trải nghiệm đổi vai rõ ràng: A gửi/B nhận, sau đó B gửi/A nhận. Mỗi máy xem preview và tự xác nhận Apply cho DB của mình. Không có transaction phân tán hoặc lời hứa hai máy hoàn tất cùng lúc.

Chỉ tài khoản Local và các cặp đã ghép qua EXC-05b. Luồng ghép mới và manual URL/secret hiện có vẫn hoạt động, nhưng không tự tham gia hướng dẫn gửi ngược nếu không xác định được peer đã lưu.

EXC-05b đã chốt theo phạm vi đã kiểm. **EXC-05 gốc vẫn mở:** chưa tự sync khi mở app, chưa chạy nền, không cam kết ba giây cho luồng cần xác nhận của người dùng.

### Những gì không cần xây lại

- `LocalSyncPlanner` đã gộp trạng thái đơn điệu; không cần CRDT, last-write-wins hoặc metadata phiên bản mới.
- `LocalSyncRepository.snapshot/preview/apply` đã có allowlist, preview và transaction cục bộ.
- `servePaired` đã gửi bằng secret đã lưu; `receiveFromPaired` đã nhận qua nonce NSD.
- `MirrorServer` và `SyncSessionTransport` đã phục vụ/tải một ciphertext qua GET.

Lát này thêm ngữ cảnh peer của lượt nhận, hành động đổi vai, hướng dẫn và kiểm chứng hai chiều. Không thêm endpoint POST/upload, lệnh Apply từ xa, dependency hoặc Room migration. Room giữ v20.

## 2. Quyết định đã duyệt

1. **Merge đơn điệu:** đã đọc thắng chưa đọc; có sao thắng bỏ sao; `readAt` là mốc sớm nhất không null. Đánh dấu chưa đọc/bỏ sao cục bộ có thể bị khôi phục ở lần sync sau; UI phải nói rõ giới hạn này.
2. **Kích hoạt thủ công**, preview riêng trên mỗi máy. Không tự bật server/sync khi mở app hoặc khi ở nền.
3. **Mỗi máy Apply riêng.** Một phía Apply, phía kia huỷ/lỗi là kết quả hợp lệ nhưng chưa hội tụ; không rollback DB đã ghi bên kia.
4. **Dữ liệu như EXC-05a:** feed URL/tên/nhãn nhóm và trạng thái unread/star/readAt theo `(feedUrl, articleLink)`. Không mở rộng payload sang title/body/notes/Read Later hoặc dữ liệu tài khoản.
5. **Hai lượt GET tuần tự.** Người dùng chủ động dừng phiên gửi cũ trước khi đổi vai; không thêm ACK hoàn tất.

## 3. Quy tắc dữ liệu

Hai máy tạo snapshot riêng từ tài khoản Local đang dùng khi bấm gửi. B không tái sử dụng snapshot đã nhận từ A: nếu B vừa Apply, snapshot gửi ngược phản ánh DB sau Apply; nếu B huỷ, snapshot phản ánh DB không bị lần nhận sửa.

Feed chưa có được thêm theo quy tắc hiện tại vào default group; feed đã có không bị đổi tên hoặc chuyển nhóm bởi lượt này. Bài không có trên máy nhận vẫn bị bỏ qua, không giữ hàng đợi để áp dụng sau khi RSS tải bài. Phải hiển thị số bài không khớp trong preview.

Hai lượt có thể hội tụ trạng thái của bài chung, nhưng không bảo đảm hai thư viện giống hệt: thiếu bài, giới hạn kích thước/số lượng hiện có và thao tác mới xen giữa có thể tạo khác biệt. Không đồng bộ huỷ đăng ký feed hay xoá bài.

## 4. UX đổi vai

Giữ màn Local Sync hiện có, không tạo wizard hoặc trang mới.

### 4.1 Máy đang gửi

Với `Sending` có `pairedPeerId`, hiện hướng dẫn: sau khi máy kia nhận xong, dừng phiên và chuyển sang Nhận. Thêm hành động **Dừng và chờ nhận lại**:

1. Đóng server, dừng quảng bá/discovery cũ và huỷ job phiên cũ.
2. Đưa trạng thái về Idle và chuyển tab Nhận.
3. Bắt đầu discovery dưới quyền LAN hiện tại; chỉ báo đang tìm/chờ, không báo peer đã nhận hoặc đã Apply.

Không mở server gửi mới. Nếu quyền bị thu hồi, hiện lỗi quyền và hành động xin quyền/mở Settings sẵn có. Nút Stop thường và manual send vẫn giữ hành vi hiện tại. Luồng manual không có peer chỉ dùng Stop/đổi tab như trước.

### 4.2 Máy vừa nhận

Sau khi `receiveFromPaired` xác thực và tạo preview, nhớ peerId làm ngữ cảnh cho lượt gửi ngược; không giữ bản sao secret trong trạng thái UI.

- Sau Apply thành công: hiện số liệu `LocalSyncApplyResult`, thông báo **đã áp dụng trên máy này**, nút **Gửi lại cho ‹nhãn peer›**.
- Sau huỷ preview: không ghi DB; hiện lựa chọn **Gửi lại cho ‹nhãn peer›**, kèm nội dung rõ rằng dữ liệu nhận chưa được áp dụng.
- Hướng dẫn người dùng yêu cầu bên kia dừng gửi/chuyển sang Nhận trước khi bấm Gửi lại. Không dò ACK hoặc tự chuyển vai bằng timer.
- Bấm Gửi lại gọi đường `servePaired` hiện có với peerId đã lưu, tạo snapshot mới. Không hiện/copy/share secret và không gọi `apply` trên bất kỳ máy nào.
- Khi A nhận lượt về, A cũng có preview và Apply riêng như B. Không có nhãn “lượt cuối” tự suy ra, vì giao thức không biết đây là chiều nào trong một phiên tổng thể. Người dùng có thể tiếp tục đổi vai nếu cần.

Nhãn peer đọc từ danh sách hiện tại và render bằng Compose Text; không đưa vào URL/HTML. Không tự chọn peer khác khi entry cũ không còn.

### 4.3 Phạm vi sống của ngữ cảnh peer

Ngữ cảnh thuộc một ViewModel, gắn với accountId đã nhận và peerId của lần nhận thành công gần nhất.

- Chỉ gán sau xác thực/decode/preview thành công; lỗi fetch/decrypt/validate không tạo hành động gửi ngược.
- Giữ sau huỷ preview có chủ đích hoặc Apply thành công.
- Xoá khi bắt đầu tác vụ gửi/nhận khác, đổi account, quên peer, Stop/dừng màn/lifecycle cleanup hoặc ViewModel bị huỷ.
- Tách huỷ preview do người dùng khỏi cleanup khi màn bị dừng: cleanup không được để lại nút gửi ngược dựa trên một phiên cũ.
- Không lưu ngữ cảnh này vào DataStore/Room/SavedStateHandle. Sau process restart, người dùng chọn peer lại từ danh sách; pairing bền vững vẫn do EXC-05b cung cấp.
- Lỗi Apply không được trình bày như Apply thành công. Muốn tiếp tục thì thoát/retry theo hành vi hiện có; không giữ nút thành công gây hiểu nhầm.

## 5. Bảo mật, lifecycle và lỗi

- Chỉ dùng host IPv4 nội bộ, URL/token/nonce và kiểm tra GCM/package theo EXC-05b hiện có. Không quảng bá token/secret qua NSD; không log secret/ciphertext/payload.
- Discovery không chứng minh peer sở hữu secret. Chỉ xác thực ciphertext thành công mới cho preview; không ghi DB dựa trên tên dịch vụ hoặc host. Không thêm tuyên bố UI rằng một dịch vụ bất kỳ chắc chắn là máy đã ghép.
- Gửi ngược chỉ dùng secret hiện còn đọc được từ PairingStore. Peer đã quên/needsRepair/khoá lỗi: vô hiệu hoá hoặc từ chối, yêu cầu ghép lại; không sinh secret mới âm thầm.
- Đổi vai phải dừng session cũ trước khi bắt đầu discovery/phiên mới. Không còn socket, quảng bá, timeout/network collector của phiên cũ. Giữ TTL và giới hạn tài nguyên hiện có.
- Network mất/hết hạn/không thấy dịch vụ: báo trạng thái cục bộ, không tự retry vòng lặp hoặc tự Apply. Không hứa khôi phục phiên sau process death.
- `apply` tiếp tục tính lại kế hoạch trong transaction DB cục bộ. Preview là dự kiến, DB có thể đổi giữa preview và Apply; kết quả sau Apply là số liệu thực tế.
- Một phía thành công và phía khác lỗi/huỷ không rollback phía thành công. Thử lại an toàn nhờ merge đơn điệu, không tạo feed trùng theo URL.
- Forget khi đang phục vụ peer phải dừng session; khi chỉ còn ngữ cảnh gửi ngược thì xoá ngữ cảnh ngay. Mọi coroutine/job/NSD listener/socket phải được huỷ/đóng theo lifecycle.
- Không kéo code Second Screen, quảng cáo, IAP hoặc API key vào lát này.

## 6. Thay đổi dự kiến

- `ui/page/p2psync/LocalSyncViewModel.kt`: ngữ cảnh lượt nhận gắn account/peer, phân biệt Cancel chủ động với cleanup, hành động đổi vai an toàn và gửi lại bằng secret hiện có.
- `ui/page/p2psync/LocalSyncUiState.kt`: chỉ bổ sung dữ liệu UI tối thiểu nếu cần; không tạo bộ đếm lượt/phiên phân tán giả.
- `ui/page/p2psync/LocalSyncPage.kt` và `ui/page/common/HomeEntry.kt`: nút đổi vai, chọn tab Nhận, hiển thị peer/kết quả cục bộ, truyền callback.
- Chuỗi mới trong **cả 38 locale**; EN/VI viết riêng, các locale khác dùng English nếu chưa dịch; không `%d` trực tiếp trước danh từ số nhiều. Test parity bắt buộc.
- Tests theo các lớp dưới đây. Domain planner, schema và transport không đổi trừ khi test chứng minh yêu cầu đã duyệt không thể đáp ứng; nếu phát hiện cần đổi giao thức, dừng và duyệt lại spec.

## 7. Kiểm chứng bắt buộc

### Unit / ViewModel

- Dừng và chờ nhận lại đóng engine/advertising, huỷ job cũ, vào Idle/discovery; không mở phiên gửi mới.
- Nhận từ peer xác thực thành công mới có ngữ cảnh gửi ngược. Huỷ chủ động giữ peer nhưng không `apply`; lỗi/cleanup không giữ peer cũ.
- Apply thành công giữ đúng peer/account, kết quả cục bộ; lỗi Apply không giả thành công.
- Gửi lại snapshot mới sau Apply hoặc huỷ; dùng đúng secret/peerId, không thêm pairing, không hiện secret.
- Quên peer, khoá hỏng, account đổi, thiếu quyền LAN, start khi busy, lifecycle/TTL/network cleanup không rò session hoặc ghi DB ngoài ý muốn.

### Compose

- Nút đổi vai chỉ cho gửi đã ghép, chuyển tab đúng và gọi stop trước discovery.
- Sau Apply/Cancel hiện đúng nội dung và nhãn, gọi Gửi lại đúng peer; không tự Apply hoặc tuyên bố hai máy hoàn tất.
- Peer mất/needsRepair bị chặn. Manual path không thay đổi. Accessibility, target chạm và inset/scroll như màn hiện có.

### Integration hai DB

- Dữ liệu hai phía khác nhau; A gửi/B nhận preview riêng rồi Apply, B snapshot sau Apply và gửi ngược/A preview rồi Apply.
- Kiểm đã đọc/sao hợp nhất trên bài chung, `readAt` sớm nhất, feed không trùng, bài chưa có bị bỏ qua; nội dung và dữ liệu tài khoản khác không đổi.
- Huỷ/lỗi ở một phía không tự Apply hoặc rollback phía kia. Chạy lại không gây cập nhật sai/nhân bản.
- Endpoint GET/token sai/lifecycle close được test thật qua socket hiện có; không dùng fake bỏ qua ranh giới liên module để coi là integration đầy đủ.

### Smoke thiết bị

BG6 `118743744X002560` + Pixel `2B051FDH3006MU` là hai máy owner đã cho phép hiện tại; xác nhận lại hiện diện/gate trước thao tác. Chỉ thao tác RSS Cat Hub, không force-stop app khác. Không dùng `connected*` Gradle task.

Backup Room + sidecar + DataStore trước smoke, force-stop app trước backup/restore. Fixture không rỗng ở hai phía, kiểm DB sau từng Apply; kiểm socket/NSD cũ đóng khi đổi vai. Cuối lượt restore file gốc và so byte, integrity/foreign key trên bản sao, thu hồi quyền tạm. Không xoá DataStore. Ghi trung thực kết quả nếu thiếu máy hoặc quảng cáo/tiến trình khác chặn test; không giả pass.

Chạy unit, Compose, integration, StringResourceParityTest, full suite, lint và compile APK/app-test; mutation các guard quan trọng. Báo cáo riêng phạm vi đã kiểm, chưa kiểm và giới hạn. EXC-05 todo gốc không chuyển DONE.

## 8. Thứ tự feature đã chọn

Sau EXC-05c: FRONT-04 Android Auto trước (Wear OS tách scope), rồi RPG-02 câu hỏi AI thật, cuối cùng FRONT-01 AI Deep Dive. Mỗi feature có brainstorming/spec/plan riêng; không viết implementation đồng loạt.

## 9. Tự soát spec

- Không thêm thuật toán merge hoặc giao thức ghi từ xa khi hai lượt GET đã đáp ứng phạm vi được duyệt.
- Không nhầm Cancel chủ động với lifecycle cleanup; không giữ peer khi đổi tài khoản.
- Không nhận ACK giả hoặc báo cả hai máy đã xong; DB transaction chỉ cục bộ.
- Hai chiều là điều phối UI, không bảo đảm phản ánh thao tác bỏ sao/chưa đọc hoặc toàn bộ thư viện.
- Giới hạn bài chưa có, snapshot caps và việc chưa tự sync đã nêu rõ.
- Không placeholder triển khai hoặc hứa hoàn thành EXC-05 gốc trong lát này.
