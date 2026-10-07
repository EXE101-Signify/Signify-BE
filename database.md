# Database Schema Explanation - Signify

Database này được thiết kế cho một ứng dụng hỗ trợ giao tiếp cho người gặp khó khăn trong ngôn ngữ thông thường hoặc sử dụng ngôn ngữ ký hiệu. Dưới đây là giải thích chi tiết về cấu trúc database.

---

### Phần 1 — Tổng quan

Database được chia thành các nhóm chức năng chính sau:

1.  **User & Authentication**: Quản lý thông tin người dùng, tài khoản đăng nhập (Username/Password, OAuth2) và phiên làm việc (sessions).
2.  **Chat**: Quản lý các cuộc hội thoại 1-1, tin nhắn, tệp đính kèm và cảm xúc (reactions).
3.  **Video Call**: Quản lý các cuộc gọi video và bản ghi phụ đề/transcript thời gian thực.
4.  **Notification**: Hệ thống thông báo cho người dùng.
5.  **Admin**: Nhật ký hoạt động quản trị (Audit Logs).

#### Sơ đồ quan hệ (Text-based)
```text
users
 ├── account (1-1)
 ├── oauth_account (1-N)
 ├── user_sessions (1-N)
 ├── conversation_participant (1-N)
 ├── message (1-N)
 ├── message_reaction (1-N)
 ├── user_blocks (blocker/blocked)
 ├── video_calls (caller/receiver)
 ├── call_transcripts (speaker)
 ├── notifications (1-N)
 └── admin_audit_logs (admin)
```

---

### Phần 2 — Giải thích từng table

#### TABLE: users
**Mục đích**: Lưu trữ thông tin cá nhân cơ bản của người dùng.
**Tại sao cần?**: Tách biệt thông tin định danh (tên, avatar) khỏi thông tin đăng nhập để dễ dàng quản lý và mở rộng.
**Các column quan trọng**:
- `user_id`: ID duy nhất do backend generate.
- `full_name`: Tên hiển thị của người dùng.
- `is_updated_profile`: Đánh dấu người dùng đã cập nhật thông tin sau khi đăng ký chưa.
- `deleted_at`: Dùng để hỗ trợ Soft Delete (không xóa hẳn data).

**Quan hệ**:
- `users` → `account`: 1-1 (Một người dùng có một tài khoản hệ thống).
- `users` → `message`: 1-N (Một người dùng có thể gửi nhiều tin nhắn).

**Ví dụ**: `1 | Nguyễn Văn A | a@gmail.com | male | avatar.png | null (chưa xóa)`

---

#### TABLE: account
**Mục đích**: Lưu trữ thông tin đăng nhập bằng username/password.
**Tại sao cần?**: Để bảo mật thông tin nhạy cảm (password_hash) riêng biệt với thông tin cá nhân.
**Các column quan trọng**:
- `username`: Tên đăng nhập duy nhất.
- `password_hash`: Mật khẩu đã được mã hóa.
- `role`: Phân quyền (USER, ADMIN).
- `status`: Trạng thái tài khoản (ACTIVE, BANNED).

**Quan hệ**:
- `account` → `users`: N-1 (Tham chiếu đến `user_id`).

**Ví dụ**: `1 | nguyenvana | $2a$10... | USER | ACTIVE`

---

#### TABLE: oauth_account
**Mục đích**: Lưu trữ thông tin liên kết với các nhà cung cấp bên thứ 3 (Google).
**Tại sao cần?**: Cho phép một `users` có thể đăng nhập bằng nhiều phương thức khác nhau.
**Các column quan trọng**:
- `provider`: Tên nhà cung cấp (ví dụ: 'GOOGLE').
- `provider_user_id`: ID định danh duy nhất từ Google.

**Quan hệ**:
- `oauth_account` → `users`: N-1.

**Ví dụ**: `101 | 1 | GOOGLE | 123456789...`

---

#### TABLE: user_sessions
**Mục đích**: Quản lý các phiên đăng nhập và Refresh Token.
**Tại sao cần?**: Để hỗ trợ tính năng "Remember Me", đăng xuất từ xa và theo dõi thiết bị.
**Các column quan trọng**:
- `refresh_token_hash`: Hash của refresh token để cấp lại access token.
- `expires_at`: Thời điểm hết hạn của session.
- `revoked_at`: Thời điểm session bị hủy (khi logout).

**Quan hệ**:
- `user_sessions` → `users`: N-1.

---

#### TABLE: conversation
**Mục đích**: Đại diện cho một cuộc hội thoại.
**Tại sao cần?**: Nhóm các tin nhắn lại với nhau. Dù hiện tại chỉ chat 1-1, cấu trúc này giúp dễ nâng cấp lên chat group sau này.
**Các column quan trọng**:
- `type`: Loại hội thoại (PRIVATE).

**Quan hệ**:
- `conversation` → `conversation_participant`: 1-N.
- `conversation` → `message`: 1-N.

---

#### TABLE: conversation_participant
**Mục đích**: Lưu danh sách người tham gia vào một cuộc hội thoại.
**Tại sao cần?**: Để biết ai có quyền đọc tin nhắn trong cuộc hội thoại đó.
**Các column quan trọng**:
- `last_read_message_id`: ID của tin nhắn cuối cùng mà người này đã đọc (dùng cho tính năng "Seen").

**Quan hệ**:
- `conversation_participant` → `users`: N-1.
- `conversation_participant` → `conversation`: N-1.

---

#### TABLE: message
**Mục đích**: Lưu nội dung tin nhắn.
**Các column quan trọng**:
- `content`: Nội dung văn bản.
- `message_type`: Loại tin nhắn (TEXT, IMAGE, FILE).
- `is_deleted`: Đánh dấu tin nhắn bị thu hồi.

**Quan hệ**:
- `message` → `conversation`: N-1.
- `message` → `users` (sender_id): N-1.

---

#### TABLE: message_attachment
**Mục đích**: Lưu thông tin tệp đính kèm.
**Tại sao cần?**: Một tin nhắn có thể có nhiều file hoặc thông tin file quá lớn để nhét chung vào bảng `message`.
**Các column quan trọng**:
- `file_url`: Link storage (S3/R2).
- `mime_type`: Định dạng file (image/png, application/pdf).

**Quan hệ**:
- `message_attachment` → `message`: N-1.

---

#### TABLE: message_reaction
**Mục đích**: Lưu cảm xúc của người dùng đối với tin nhắn.
**Các column quan trọng**:
- `reaction`: Giá trị cảm xúc (LIKE, HEART, v.v.).

**Quan hệ**:
- `message_reaction` → `message`: N-1.
- `message_reaction` → `users`: N-1.

---

#### TABLE: user_blocks
**Mục đích**: Lưu thông tin chặn người dùng.
**Các column quan trọng**:
- `blocker_id`: Người thực hiện chặn.
- `blocked_id`: Người bị chặn.

---

#### TABLE: video_calls
**Mục đích**: Lưu lịch sử cuộc gọi video.
**Các column quan trọng**:
- `status`: Trạng thái cuộc gọi: `CALLING`, `ACCEPTED`, `REJECTED`, `MISSED`, `COMPLETED`, `BUSY`. `ACCEPTED` là cuộc gọi đang kết nối; chỉ chuyển từ `CALLING` sang `ACCEPTED`, `REJECTED`, `MISSED` hoặc `BUSY`, và từ `ACCEPTED` sang `COMPLETED`.
- `started_at`, `ended_at`: Thời gian bắt đầu và kết thúc để tính thời lượng.

**Quan hệ**:
- `video_calls` → `users`: N-1 (caller & receiver).

---

#### TABLE: call_transcripts
**Mục đích**: Lưu phụ đề hoặc nội dung chuyển đổi từ giọng nói/ký hiệu trong cuộc gọi.
**Tại sao cần?**: Tách khỏi bảng cuộc gọi vì một cuộc gọi có hàng trăm dòng transcript.
**Các column quan trọng**:
- `start_time`, `end_time`: Offset (miligiây) tính từ lúc bắt đầu cuộc gọi.

**Quan hệ**:
- `call_transcripts` → `video_calls`: N-1.

---

#### TABLE: notifications
**Mục đích**: Lưu các thông báo đẩy cho người dùng.
**Các column quan trọng**:
- `reference_id`: ID của đối tượng liên quan (ví dụ message_id).
- `is_read`: Trạng thái đã đọc.

---

#### TABLE: admin_audit_logs
**Mục đích**: Ghi lại lịch sử thao tác của Admin.
**Tại sao cần?**: Để kiểm soát và truy vết các hành động nhạy cảm như Ban/Unban user.
**Các column quan trọng**:
- `action`: Hành động (BAN_USER, DELETE_MESSAGE).
- `reason`: Lý do thực hiện.

---

### Phần 3 — Tại sao không gom tất cả vào `users`?

Việc tách `users`, `account`, và `oauth_account` là kỹ thuật **Database Normalization**:
1.  **Bảo mật**: `account` chứa password hash, không nên truy xuất nó khi chỉ cần lấy tên/avatar hiển thị.
2.  **Đa dạng hóa**: Một người dùng có thể không có mật khẩu hệ thống (chỉ dùng Google) hoặc có cả hai.
3.  **Mở rộng**: Dễ dàng thêm các phương thức đăng nhập mới (Facebook, Apple) bằng cách thêm dòng vào `oauth_account` mà không phải thêm cột vào bảng `users`.
4.  **Hiệu năng**: Bảng `users` sẽ nhẹ hơn, giúp các câu query lấy thông tin cơ bản nhanh hơn.

---

### Phần 4 — Giải thích flow thực tế

#### Flow 1: Register
1.  Backend generate ID.
2.  Insert vào `users`: Thông tin cá nhân cơ bản.
3.  Insert vào `account`: Username và password đã hash.

#### Flow 2: Login username/password
1.  Kiểm tra `account` xem username có tồn tại và password có khớp không.
2.  Nếu khớp, tạo 1 bản ghi vào `user_sessions` với `refresh_token`.
3.  Trả về JWT (Access Token lưu ở bộ nhớ, Refresh Token lưu ở Cookie/Database).

#### Flow 3: Login Google
1.  Lấy thông tin từ Google.
2.  Kiểm tra `oauth_account` với `provider_user_id`.
3.  **Nếu chưa có**: Tạo `users` mới -> Tạo `oauth_account` liên kết.
4.  **Nếu đã có**: Lấy `user_id` hiện tại.
5.  Tạo session mới trong `user_sessions`.

#### Flow 4: Chat (User A -> User B)
1.  Tìm hoặc tạo 1 `conversation` loại PRIVATE.
2.  Đảm bảo `conversation_participant` có mặt cả A và B.
3.  Insert vào `message`: `sender_id = A`, `content = "Hello"`.
4.  Nếu có file, insert vào `message_attachment` liên kết với `message_id` vừa tạo.

#### Flow 5: Seen message
Khi User B mở tin nhắn:
- Cập nhật `last_read_message_id` trong bảng `conversation_participant` của User B bằng ID của tin nhắn mới nhất.

#### Flow 6: Block user
1.  Insert bản ghi vào `user_blocks` với `blocker_id = A` và `blocked_id = B`.
2.  Khi muốn bỏ chặn (Unblock), cập nhật `deleted_at` của bản ghi đó.

#### Flow 7: Video call
1.  Tạo bản ghi trong `video_calls` khi bắt đầu gọi.
2.  Trong quá trình gọi, mỗi khi AI dịch được 1 đoạn, insert vào `call_transcripts` với `call_id` tương ứng.

#### Flow 8: Admin ban user
1.  Admin thực hiện Ban.
2.  Cập nhật `account.status = 'BANNED'`.
3.  Insert 1 dòng vào `admin_audit_logs` để ghi lại lý do.

---

### Phần 5 — Những thứ KHÔNG lưu trong database

Các dữ liệu sau nên xử lý qua **WebSocket** và **Redis**:
- **Typing indicator**: Tần suất gửi quá cao (mỗi vài giây), lưu database sẽ gây nghẽn (IO Overhead).
- **Online/Offline status**: Thay đổi liên tục khi user đóng/mở app, Redis (Key-Value) xử lý tốt hơn nhiều.
- **WebRTC Signaling (Offer/Answer/ICE)**: Đây là dữ liệu trung chuyển để thiết lập kết nối ngang hàng (P2P), không có giá trị lưu trữ lâu dài.

---

### Phần 6 — Tóm tắt cực ngắn

| Table | Công dụng ngắn gọn |
| :--- | :--- |
| **users** | Thông tin cá nhân (Tên, Avatar, Email). |
| **account** | Tài khoản hệ thống (Username, Password, Role, Status). |
| **oauth_account** | Liên kết đăng nhập Google. |
| **user_sessions** | Quản lý phiên đăng nhập và Refresh Token. |
| **conversation** | Nhóm các tin nhắn lại thành cuộc hội thoại. |
| **conversation_participant** | Ai tham gia hội thoại và đã đọc đến đâu. |
| **message** | Nội dung tin nhắn văn bản. |
| **message_attachment** | File đính kèm (ảnh, tài liệu) của tin nhắn. |
| **message_reaction** | Các cảm xúc (thả tim, like) trên tin nhắn. |
| **user_blocks** | Danh sách chặn người dùng. |
| **video_calls** | Lịch sử và trạng thái cuộc gọi video. |
| **call_transcripts** | Phụ đề/Nội dung cuộc trò chuyện trong video call. |
| **notifications** | Thông báo đẩy cho người dùng. |
| **admin_audit_logs** | Nhật ký hành động của quản trị viên. |
| **event_publication** | Lưu vết các sự kiện hệ thống (Outbox pattern). |
