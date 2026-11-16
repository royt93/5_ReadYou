# FRONT-02m (Điều tra gốc lỗi gõ chữ chập chờn của test 02d), CHƯA KẾT LUẬN GỐC

**Date:** 2026-11-16  
**Branch:** `dev` (không push)  
**Commit:** `9aea8512` và `b2191805` (chỉ bộ phân loại và test thuần JVM của nó)  
**Scope:** điều tra, **không đổi mã production**, manifest, chuỗi, Room (v22), DataStore, `app/build.gradle`; không dependency mới. **Điều tra chưa xong**: vòng đo dừng ở 4 lô hợp lệ trên 10 vì Pixel bị dùng cho việc khác.  
**Spec / plan:** `docs/superpowers/specs/2026-11-16-front-02m-typing-flake-investigation-design.md`, `docs/superpowers/plans/2026-11-16-front-02m-typing-flake-investigation.md`.

## Câu hỏi
Ở FRONT-02d, bước gõ chữ vào ô "Thêm nguồn" thỉnh thoảng không có hiệu lực (ô còn đó, chữ rỗng). Test 02d che bằng cách gõ lại khi chữ chưa từng vào ô. Chữ mất ở đâu: test, thư viện hay app?

## Điều đã đo được (trên Pixel 7 Pro, chế độ đồng hồ `manual`, giống 02d)
Probe tạm: mỗi lượt dựng một `ComponentActivity` mới, gắn `SubscribeDialog` thật (như 02e), gõ **một lần, không gõ lại** bằng `performTextInput`, rồi mỗi khung hình ghi độ dài chữ trong ViewModel và trên màn hình. Không bấm Tìm, không gọi mạng, không ghi Room.

| Lô | Kết quả |
|---|---|
| 1 | 100 lượt: 98 xanh, 2 `UNFOCUSED_AT_TYPING` |
| 2 | 100 lượt: 98 xanh, 1 `ERASED`, 1 `UNFOCUSED_AT_TYPING` |
| 3 | 100 lượt: 100 xanh |
| 5 | 100 lượt: 100 xanh |
| **Cộng 4 lô hợp lệ** | **400 lượt: 396 xanh, 3 `UNFOCUSED_AT_TYPING`, 1 `ERASED`; tỉ lệ đỏ 1,0%** |
| 4 | **mất số liệu** (lỗi probe: `NullPointerException` trong `ActivityScenario.close()`, lô chạy 402 giây thay vì 250) |
| 6 | **mất số liệu** (lỗi probe: "Never saw: the link field") |
| 7 đến 10 | **chưa chạy**: cổng dừng vì Pixel đang mở app `loyalty.skyjoy.vn.dev` của owner |

Các dòng đỏ (nguyên văn, không có chữ người dùng):
```
RED{outcome=UNFOCUSED_AT_TYPING iter=37 winFocus=true fieldFocus=false enabled=true vm=0x60 screen=0x60}
RED{outcome=UNFOCUSED_AT_TYPING iter=68 winFocus=true fieldFocus=false enabled=true vm=0x60 screen=0x60}
RED{outcome=UNFOCUSED_AT_TYPING iter=48 winFocus=true fieldFocus=false enabled=true vm=0x60 screen=0x60}
RED{outcome=ERASED iter=96 winFocus=true fieldFocus=false enabled=true vm=18x1,0x59 screen=0x60}
```

## Đọc số liệu (có cân nhắc, chưa phải kết luận)
1. **Lỗi tái hiện được** ở tỉ lệ khoảng 1% (4/400), khớp 2 đến 9% đo ở 02d, **ngoài `MainActivity`**, không có `FeedsPage`. Vậy giả thuyết H5 ("chỉ xảy ra khi có `FeedsPage`") **không đứng vững ở dạng đầy đủ**, nhưng vì đang dùng đồng hồ `manual`, "do đồng hồ ảo `manual`" vẫn chưa loại trừ (chưa chạy chế độ `auto`).
2. **Trong cả 400 lượt, kể cả lượt xanh, ô chưa có focus tại thời điểm gõ** (`field already focused before typing: 0/400`) và chữ vẫn vào ở 396 lượt: `performTextInput` tự xin focus rồi chèn chữ. Vì vậy cờ "ô chưa focus" **không tự giải thích** 3 lượt đỏ; nhãn `UNFOCUSED_AT_TYPING` ở bản probe này **không phân biệt được** "focus đến sau lúc gõ" với "focus không bao giờ đến" (bản mới của bộ phân loại tách hai trường hợp, **chưa chạy trên máy**).
3. **Lượt `ERASED` (1/400) là dấu vết đáng chú ý nhất:** ViewModel giữ đủ 18 ký tự đúng 1 khung hình rồi về 0 trong khi màn hình luôn trống. Nghĩa là chữ **đã tới ViewModel rồi bị ghi đè**. Từ một lượt không kết luận được đó là app (ví dụ `reset()` muộn của `LaunchedEffect(visible)`), thư viện (`CoreTextField` ghi lại giá trị cũ khi phiên nhập khởi động) hay test; **không đủ bằng chứng**.
4. **Phép thử âm đạt:** khi probe không gõ gì, bộ phân loại **không bao giờ** báo xanh (5/5 mỗi lô), nên probe nhìn thấy một ô trống. Điều này **không** chứng minh probe *tái tạo* đúng lỗi, chỉ chứng minh nó *nhìn thấy* một lượt hỏng.

## Các giả thuyết, sau số liệu
| | Giả thuyết | Tình trạng |
|---|---|---|
| H1 | Hành động nhập chữ trả `false` (ô tắt) | Không thấy: `enabled=true` ở mọi lượt đỏ. Giá trị trả về **không đo trực tiếp** (gọi trực tiếp sẽ đổi đường chạy). |
| H2 | Chữ tới ViewModel nhưng không tới màn hình | Không thấy riêng (không có nhãn `NOT_RENDERED`); lượt `ERASED` có màn hình trống nhưng ViewModel cũng về 0. |
| H3 | Cuộc đua phiên nhập và focus | **Còn mở, đáng nghi nhất** (3/4 lượt đỏ là ô chưa focus và chữ không vào ViewModel). Cần bản probe mới để phân biệt "focus đến muộn" với "không bao giờ". |
| H4 | `reset()` muộn xóa chữ | **Còn mở**, một lượt `ERASED` phù hợp nhưng chưa phân biệt được với H3. |
| H5 | Chỉ xảy ra với `MainActivity` | Không đứng vững ở dạng đầy đủ (xem trên); phần "đồng hồ `manual`" chưa kiểm. |

## Điều chưa làm, và vì sao
- **Chưa chạy chế độ `auto`** (giống 02e): Pixel không còn rảnh.
- **Chưa chạy bản probe đã sửa** (bắt lỗi từng lượt, ghi focus từng khung, cờ cửa sổ mất focus, nhãn `NEVER_DELIVERED_FOCUS_LATE`): biên dịch được, bộ phân loại có 11 test và mutation 10/10 (JVM), nhưng **chưa chạy trên máy**; probe chưa commit.
- **Chưa chạy Pha B** (`MainActivity` thật); plan hoãn có chủ ý và cần owner đồng ý (quảng cáo).
- Tỉ lệ ~1% từ 4 lượt đỏ: **khoảng tin cậy rộng** (khoảng 0,3% đến 2,5% ở độ tin cậy 95% theo Wilson); chưa đủ để so tỉ lệ hai chế độ.

## Điều chỉnh khi thực thi
- Lượt thử đầu: **toàn bộ INVALID** vì cửa sổ hộp thoại nhận focus cửa sổ chậm hơn lúc ô hiện (đo: chưa có lúc ô vừa hiện, có sau 30 khung). Probe đổi sang chờ có hạn cho focus cửa sổ rồi mới đo (3 giây).
- Plan hoãn Pha B và chọn giữ `performTextInput` nguyên bản (không đọc giá trị trả về).
- Ban đầu probe để lô hỏng làm mất cả lô; bản mới đếm riêng lượt hỏng (`broken`) (chưa chạy trên máy).
- Công cụ `px_m_*` nằm ngoài kho.

## Dữ liệu máy và an toàn
Mọi lô đã chạy: DataStore trước và sau **giống hệt** (`ed9f6887…`), khôi phục byte-exact 6 tệp. Pixel `user_rotation` vẫn 0. Test không ghi Room. **Pixel đang để app `loyalty.skyjoy.vn.dev` của owner ở foreground**, nên tôi dừng, không chạm tiếp.

## Kết luận
**Chưa kết luận gốc.** Đã chứng minh lỗi tái hiện ngoài `MainActivity` (4/400), thu hẹp được còn H3 và H4 (một lượt `ERASED` cho thấy chữ **có lúc đã tới ViewModel rồi mất**), và có công cụ để phân biệt tiếp, nhưng vòng đo mới chạy được 4 trên 10 lô và bản probe tốt hơn chưa chạy trên máy. Test 02d vẫn dùng cơ chế gõ lại có điều kiện.

## Để mở lại
Khi Pixel rảnh khoảng 40 phút: chạy cổng, rồi `px_m_loop.sh 4 100 manual` và `px_m_loop.sh 4 100 auto` với probe đã sửa (commit probe trước khi chạy để có bản lưu), so tỉ lệ hai chế độ và đọc nhãn `NEVER_DELIVERED_FOCUS_LATE` so với `UNFOCUSED_AT_TYPING` / `ERASED`.
