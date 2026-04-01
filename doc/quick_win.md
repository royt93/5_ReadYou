# Đề Xuất Tính Năng "Thắng Nhanh" (Quick Wins) Cho ReadYou

Dựa trên xu hướng người dùng hiện tại (Trending) đối với các ứng dụng đọc tin tức (RSS Reader) và cấu trúc ứng dụng hiện có của ReadYou, dưới đây là 2 tính năng dễ triển khai (Easy to implement) mang lại giá trị lớn nhất cho Project:

## 1. Tóm Tắt Bài Viết Bằng AI (AI TL;DR / Summary Card)
**Xu hướng (Trending):** 
AI đang định hình lại cách chúng ta tiêu thụ nội dung. Thay vì cuộn một bài RSS dài hơn nghìn chữ, người dùng thường muốn đọc đoạn "TL;DR" (Quá dài, ngại đọc) để nắm bắt ý chính trước khi quyết định có dành thời gian đọc toàn bộ sự kiện hay không.

**Lý do là "Quick Win":**
* Không đòi hỏi thay đổi Database hay Architecture phức tạp nội bộ của ứng dụng.
* Chỉ cần tích hợp một HTTP Request đơn giản lên Google Gemini API (có gói miễn phí) hoặc OpenAI.
* Giao diện chỉ cần một nút bấm "✨ Tóm tắt bằng AI" trong màn hình Đọc (`ReadingViewModel`), ấn vào sẽ bật lên một `BottomSheet` chứa 3-5 ý chính dạng Bullet Points.

**Hướng triển khai:**
1. Trích xuất nội dung văn bản thuần (plain-text) từ thẻ Article/Feed.
2. Viết một `UseCase` ném Text đó cho Gemini API với Prompt: *"Hãm tóm tắt bài báo này thành 3 gạch đầu dòng ngắn gọn bằng tiếng Việt/tiếng Anh"*.
3. Đổ kết quả vào Jetpack Compose UI.

---

## 2. Nghe Báo (Text-to-Speech / Đọc Thành Tiếng)
**Xu hướng (Trending):** 
Ngày càng có nhiều người dùng thích "nghe" tin tức giống như nghe Podcast khi họ di chuyển, lái xe hay làm việc nhà. Tích hợp âm thanh vào bài viết giúp mở rộng mạnh mẽ thời gian tương tác của người dùng với ứng dụng (Retention Time).

**Lý do là "Quick Win":**
* Android có API **native** `TextToSpeech` có sẵn trên mọi thiết bị, hoàn toàn không tốn tiền API, không cần server hay library bên thứ 3.
* Triển khai siêu nhanh chỉ trong vài chục dòng code.

**Hướng triển khai:**
1. Thêm một nút 🎧 (Play/Nghe) dưới hoặc trên thanh công cụ Floating (ToolBar) ở giao diện Đọc.
2. Khi người dùng bấm, truyền nội dung bài báo vào `TextToSpeech(context)`:
   ```kotlin
   val tts = TextToSpeech(context) { status ->
       if (status == TextToSpeech.SUCCESS) {
           tts.language = Locale("vi", "VN") // Hoặc lấy Locale hiện hành của bài viết
           tts.speak(articleText, TextToSpeech.QUEUE_FLUSH, null, null)
       }
   }
   ```
3. Cập nhật icon `Pause` / `Stop` khi đang phát âm thanh.

---

> **Kết Luận:** Hai tính năng này không tốn quá nhiều Story Points để phát triển nhưng lại là những tính năng **"Marketing Materials"** tuyệt vời (có thể mang lên Screenshot của PlayStore để thu hút User mới, đặc biệt là tính năng Tích hợp AI).
