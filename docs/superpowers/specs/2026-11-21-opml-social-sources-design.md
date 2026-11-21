# OPML giữ nguồn Bluesky và Nostr qua xuất rồi nhập (đặc tả ngắn; owner chọn hướng "ghi thuộc tính" 2026-11-21)

**Nền:** đã đo bằng `OpmlSvTest` (commit `e84f4fea`): xuất rồi nhập OPML làm nguồn Bluesky (`bsky:…`) và Nostr (`nostr:npub…`) thành nguồn `RSS` với `sourceUrl = null`, tức nguồn hỏng.

## Thiết kế
**Xuất** (`OpmlSv.saveToString`): với nguồn có `sourceType` là `BLUESKY` hoặc `NOSTR`, thêm hai thuộc tính vào `<outline>`: `sourceType` và (nếu khác `null`) `sourceUrl`. Nguồn khác (RSS, WEB, READ_LATER) **không đổi**, tệp của chúng giống hệt bản cũ.

**Nhập** (`OPMLDataSource`, cả hai nhánh: nguồn nằm ngoài nhóm và nguồn nằm trong nhóm): tệp OPML là **đầu vào không tin cậy**, nên thuộc tính chỉ được nhận khi **khớp địa chỉ**:
- `sourceType="BLUESKY"` chỉ nhận khi `xmlUrl` bắt đầu `bsky:` và phần sau qua `BlueskyFeedMapper.isActor`.
- `sourceType="NOSTR"` chỉ nhận khi `xmlUrl` bắt đầu `nostr:` và phần sau là `npub` hợp lệ (`NostrKeys.npubToHex` khác `null`).
- `sourceUrl` chỉ nhận khi cùng quy tắc theo đúng kiểu đã nhận (Bluesky: `bsky:` + `isActor`; Nostr: `nostr:` + `npub` hợp lệ). Sai thì để `null`.
- Mọi giá trị `sourceType` khác (`WEB`, `READ_LATER`, chuỗi lạ, rỗng, chữ thường) hoặc địa chỉ không khớp: **bỏ thuộc tính**, nguồn nhập như RSS thường (hành vi cũ). Không bao giờ ném lỗi vì thuộc tính lạ.
- Không chọn kiểu nguồn từ tên tệp hay từ chuỗi do người ngoài đặt ngoài hai giá trị trên.

**Chặn trùng khi nhập** (`OpmlSv.saveToDatabase`): ngoài `isFeedExist(url)` như cũ, nếu nguồn nhập có `sourceUrl` khác `null` thì cũng gọi `isFeedSourceExist(sourceUrl)`; có rồi thì bỏ qua. (Nhờ vậy một tài khoản Bluesky đã lưu dưới DID không bị nhập đôi khi tệp ghi handle.)

## Không đổi
Định dạng của nguồn RSS/WEB/READ_LATER; Room, DataStore, UI, chuỗi; không dependency. Tệp OPML cũ vẫn nhập như trước (thiếu thuộc tính thì hành vi cũ).

## Rủi ro nói thẳng
- Trình đọc OPML khác bỏ thuộc tính lạ khi lưu lại tệp: nguồn quay về hỏng như hiện nay (không tệ hơn).
- Hai thuộc tính không chuẩn OPML nhưng OPML cho phép thuộc tính mở rộng.
- Không kiểm trên máy; chỉ JVM với DAO giả.

## Kiểm chứng
TDD JVM trong `OpmlSvTest`: xuất ghi thuộc tính cho Bluesky/Nostr và không cho RSS; nhập lại đúng kiểu và `sourceUrl`; tệp cũ (không thuộc tính) nhập như RSS; thuộc tính sai/lạ/kiểu không khớp địa chỉ bị bỏ; `sourceType="WEB"` hoặc `READ_LATER` trong tệp bị bỏ; trùng theo `sourceUrl` bị loại; nguồn nằm trong nhóm cũng đúng. Đột biến JVM trên cả xuất và nhập. Full JVM `--rerun-tasks`.
