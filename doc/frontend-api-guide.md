# Hướng dẫn API cho frontend

Tài liệu này mô tả các endpoint đang được khai báo trong controller của `features`. Base URL khi chạy local: `http://localhost:8080`. Các route bên dưới đã bao gồm tiền tố `/api`; không tự thêm `/api/v1` cho Auth, User, Email hoặc Storage. Chi tiết nghiệp vụ có trong [authentication.md](authentication.md), [user-management.md](user-management.md), [email-api.md](email-api.md) và [chat-api.md](chat-api.md).

## Quy ước chung

- Mọi endpoint trả HTTP `200` khi thành công và dùng `ApiResponse`. JSON có các trường `success`, `status`, `message`, `timestamp`; `data` có thể vắng mặt khi kết quả là `null`. Khi lỗi, đọc HTTP status cùng `message`, và `errors` nếu có lỗi theo từng field. Xem [standard-api-response.md](standard-api-response.md).
- Trừ các endpoint được đánh dấu **Public**, gửi `Authorization: Bearer <accessToken>`. `USER` và `ADMIN` được gọi API bảo vệ thông thường; `/api/admin/**` chỉ dành cho `ADMIN`. Không gửi token cũ/hỏng trên request public vì JWT filter vẫn kiểm tra Bearer header nếu có.
- Body mặc định là `Content-Type: application/json`. Riêng đăng ký tài khoản dùng `multipart/form-data`; trình duyệt tự đặt boundary khi dùng `FormData`.
- Các ID và trường thời gian dạng số dùng `BIGINT` / Unix epoch **milliseconds**. Nếu ID có thể vượt `Number.MAX_SAFE_INTEGER`, frontend JavaScript cần chiến lược đọc JSON giữ nguyên độ chính xác; API hiện vẫn serialize ID dưới dạng number.
- CORS mặc định cho phép `http://localhost:3000` (có thể cấu hình bằng `CORS_ALLOWED_ORIGINS`).

Ví dụ response thành công (các trường null của envelope có thể bị lược bỏ):

```json
{
  "success": true,
  "status": 200,
  "message": "Success",
  "data": {},
  "timestamp": "2026-10-01T10:00:00+07:00"
}
```

Ví dụ lỗi validation:

```json
{
  "success": false,
  "status": 400,
  "message": "Validation failed",
  "errors": { "content": "Message content must not be blank" },
  "timestamp": "2026-10-01T10:00:00+07:00",
  "path": "/api/v1/conversations/1/messages"
}
```

## Auth và đăng ký

### `POST /api/users/register` — Public

Tạo tài khoản và đăng nhập ngay. Gửi `multipart/form-data` với part **`request` là JSON** (`Content-Type: application/json`) và part `avatar` là file tùy chọn. Không gửi từng field tài khoản thành các text part riêng. Ví dụ:

```ts
const baseUrl = "http://localhost:8080";
const avatarFile = document.querySelector<HTMLInputElement>("#avatar")?.files?.[0];
const form = new FormData();
form.append("request", new Blob([JSON.stringify({
  username: "alice",
  password: "StrongPass1!",
  email: "alice@example.com",
  firstName: "Alice",
  lastName: "Nguyen"
})], { type: "application/json" }));
if (avatarFile) form.append("avatar", avatarFile);

const response = await fetch(`${baseUrl}/api/users/register`, {
  method: "POST",
  body: form
});
const result = await response.json();
```

`username` bắt buộc, tối đa 100 ký tự. `password` bắt buộc, tối thiểu 8 ký tự, tối đa 72 byte UTF-8, cần chữ thường, chữ hoa, số và ký tự đặc biệt. `email` tùy chọn, nếu có phải đúng định dạng và tối đa 150 ký tự. `firstName`, `lastName` tùy chọn, mỗi trường tối đa 100 ký tự. Avatar chấp nhận JPEG/PNG/WebP/GIF, tối đa 5 MiB theo cấu hình mặc định. `data` là `{ "user": UserResponse, "tokens": TokenResponse }`; URL avatar nằm trong `data.user.avatar`. Username/email đã tồn tại hoặc mật khẩu trùng theo kiểm tra hiện tại trả `409`. DTO validation trả `400`; kiểm tra mật khẩu trong service hiện trả `409` nếu vi phạm quy tắc; file quá lớn `413`, loại ảnh không hỗ trợ `415`.

### `POST /api/auth/login` — Public

Body: `{ "username": "alice", "password": "StrongPass1!", "deviceName": "Chrome on Windows" }`. `deviceName` tùy chọn, tối đa 255 ký tự. `data` giống đăng ký: `{ user, tokens }`. Sai thông tin đăng nhập trả `401`; tài khoản không hoạt động trả `403`.

### `POST /api/auth/refresh` — Public

Body: `{ "refreshToken": "<refreshToken>" }` (bắt buộc, tối đa 4096 ký tự). `data` là **TokenResponse trực tiếp**, không có lớp `tokens`: `{ "accessToken": "...", "refreshToken": "...", "tokenType": "Bearer", "accessExpiresAt": 1790000900000, "refreshExpiresAt": 1790604800000 }`. Thay **cả hai token** đã lưu sau khi refresh; token cũ bị thu hồi. Không gửi Bearer access token đã hết hạn kèm request này. Token không hợp lệ hoặc đã thu hồi trả `401`.

### `POST /api/auth/logout` — Bearer USER/ADMIN

Body: `{ "refreshToken": "<refreshToken>" }`. Refresh token phải thuộc người dùng trong access token. Thành công trả `200`, không có `data`; phiên tương ứng bị thu hồi và access token gắn với phiên đó bị từ chối ở request tiếp theo. Frontend xóa cặp token đã lưu. Token sai hoặc không thuộc phiên/người dùng trả `401`.

### `POST /api/auth/logout-all` — Bearer USER/ADMIN

Không có body. Thành công trả `200`, không có `data`; tất cả phiên của người dùng bị thu hồi. Frontend xóa cặp token đã lưu trên thiết bị hiện tại.

`UserResponse` trong đăng ký, đăng nhập, `/me` và cập nhật profile:

```json
{
  "userId": 1,
  "username": "alice",
  "role": "USER",
  "email": "alice@example.com",
  "firstName": "Alice",
  "lastName": "Nguyen",
  "fullName": "Alice Nguyen",
  "avatar": null,
  "emailVerified": false,
  "phone": null,
  "gender": null,
  "address": null
}
```

Trong `UserResponse`, `gender` được DTO khai báo là string (`"true"`, `"false"` hoặc `null`); request cập nhật dùng boolean. Trường này là boolean/null trong `ManagedUser.profile` của Admin.

## Hồ sơ người dùng

### `GET /api/users/me` — Bearer USER/ADMIN

Không có body. `data` là `UserResponse` của chính người dùng trong access token. Dùng để khôi phục trạng thái đăng nhập và hiển thị hồ sơ. Không truyền `userId`.

### `PATCH /api/users/profile` — Bearer USER/ADMIN

Cập nhật một phần hồ sơ của người dùng trong access token. Ví dụ:

```json
{
  "firstName": "Alice",
  "lastName": "Tran",
  "phone": "0901234567",
  "gender": true,
  "address": "Ho Chi Minh City"
}
```

| Field | Kiểu | Quy tắc |
| --- | --- | --- |
| `email` | string | Email hợp lệ, không blank, tối đa 150 ký tự; đổi email đặt `emailVerified=false` |
| `firstName`, `lastName` | string | Tối đa 100 ký tự mỗi trường; server trim và tính lại `fullName` |
| `avatar` | string | URL hoặc chuỗi do ứng dụng chọn, tối đa 2048 ký tự; endpoint này không upload file và hiện không kiểm tra định dạng URL |
| `phone` | string | Tối đa 20 ký tự; hiện không áp regex số điện thoại |
| `gender` | boolean | `true`/`false`; `null` không thay đổi giá trị cũ |
| `address` | string | Tối đa 500 ký tự |

Field bỏ qua hoặc gửi `null` giữ nguyên. Chuỗi rỗng `""` xóa `avatar`, `phone`, `address`; tên rỗng được trim thành `""`. Không gửi `userId`, `role`, `status` để chọn danh tính/quyền. `data` là `UserResponse` mới; email trùng người khác trả `409`. Route cập nhật là `/api/users/profile`, còn `/api/users/me` chỉ hỗ trợ `GET`.

## Quản trị người dùng

Tất cả route trong mục này cần Bearer với role `ADMIN`. `data` của endpoint chi tiết/cập nhật/ban là `ManagedUser`:

```json
{
  "profile": {
    "userId": 2,
    "username": "bob",
    "role": "USER",
    "email": "bob@example.com",
    "firstName": "Bob",
    "lastName": null,
    "fullName": "Bob",
    "avatar": null,
    "emailVerified": false,
    "phone": null,
    "gender": null,
    "address": null
  },
  "status": "ACTIVE",
  "createdAt": 1790000000000,
  "updatedAt": 1790000000000,
  "deletedAt": null
}
```

### `GET /api/admin/users` — danh sách

Query tùy chọn: `search` (mặc định `""`, tối đa 150 ký tự; tìm username/email/full name, không phân biệt hoa thường), `page` (mặc định `0`, bắt đầu từ 0), `size` (mặc định `20`, từ 1 đến 100). Ví dụ: `/api/admin/users?search=alice&page=0&size=20`. `data` là `{ "content": [ManagedUser], "page": 0, "size": 20, "totalElements": 1, "totalPages": 1 }`. Danh sách gồm cả người dùng bị ban, sắp xếp theo user ID. Query không hợp lệ trả `400`.

### `GET /api/admin/users/{userId}` — chi tiết

`userId` là số nguyên dương. Không có body. `data` là `ManagedUser`, kể cả tài khoản đã bị ban. Không tìm thấy trả `404`.

### `PATCH /api/admin/users/{userId}` — sửa hồ sơ

Body và giới hạn giống `PATCH /api/users/profile`. `data` là `ManagedUser`. Chỉ sửa hồ sơ của user đang active; user không tồn tại trả `404`, đã bị ban trả `403`, email trùng trả `409`.

### `DELETE /api/admin/users/{userId}` — ban tài khoản

Không có body. Đây là soft delete: `data` trả `ManagedUser` với `status="BANNED"`, `deletedAt` được đặt và các phiên của user bị thu hồi. Gọi lặp lại giữ nguyên thời điểm xóa ban đầu. Admin tự ban mình trả `403`; ID không tồn tại trả `404`.

## Email và OTP — Public

Mỗi endpoint sau dùng JSON và trả `200` với thông báo trong `message`, không có `data` khi thành công. `email` phải có và đúng định dạng. `otp` phải có, dài đúng 6 ký tự; nên gửi dưới dạng **string** để giữ số 0 đầu nếu có. OTP tồn tại 5 phút và bị xóa sau khi verify thành công.

| Endpoint | Body mẫu | Cách dùng và lỗi chính |
| --- | --- | --- |
| `POST /api/email/otp/send` | `{ "email": "alice@example.com" }` | Gửi OTP đăng ký; email đã tồn tại trả `409` |
| `POST /api/email/otp/verify` | `{ "email": "alice@example.com", "otp": "123456" }` | Kiểm tra OTP; sai/hết hạn trả `400` |
| `POST /api/email/otp/resend` | `{ "email": "alice@example.com" }` | Tạo OTP mới và gửi lại; OTP cũ hết hiệu lực |
| `POST /api/email/forgot-password/send` | `{ "email": "alice@example.com" }` | Gửi OTP đặt lại mật khẩu; email chưa đăng ký trả `404` |
| `POST /api/email/forgot-password/verify` | `{ "email": "alice@example.com", "otp": "123456", "newPassword": "NewPass1!" }` | Kiểm tra OTP rồi đổi mật khẩu; OTP sai/hết hạn trả `400`; mật khẩu theo quy tắc đăng ký |

`/otp/verify` chỉ xác nhận và tiêu thụ OTP. Controller đăng ký hiện **không** nhận mã OTP và không kiểm tra trạng thái verify; không hiển thị `emailVerified=true` chỉ vì gọi `/otp/verify`. Luồng quên mật khẩu dùng cùng kho OTP theo email, nên tránh chạy đồng thời hai luồng OTP trên cùng một email. Với quên mật khẩu, kiểm tra quy tắc mật khẩu mới ở frontend trước khi gửi: service tiêu thụ OTP trước khi kiểm tra độ phức tạp của mật khẩu. Mật khẩu không hợp lệ có thể trả `400` ở DTO hoặc `409` ở service.

## Chat

Các endpoint sau dùng Bearer USER/ADMIN. Controller chấp nhận cả `/api/v1/conversations` và `/api/conversations`; frontend nên dùng `/api/v1/conversations` nhất quán. Server lấy danh tính người gửi/người tạo từ access token.

### `POST /api/v1/conversations` — tạo conversation

Body chat 1-1: `{ "type": "PRIVATE", "participantIds": [2] }`. `participantIds` gồm ID người còn lại, không cần đưa ID của chính mình. Nếu conversation PRIVATE giữa hai người đã tồn tại, `data` trả conversation cũ. Body nhóm: `{ "type": "GROUP", "name": "Team", "participantIds": [2, 3] }`; `name` bắt buộc, không blank, tối đa 100 ký tự. `type` nhận `PRIVATE`/`GROUP` (server parse không phân biệt hoa thường); `participantIds` phải không rỗng. Conversation PRIVATE cần đúng hai người tính cả người tạo; ID không tồn tại hoặc chỉ tự chat trả `400`.

`data` là `ConversationResponse`:

```json
{
  "conversationId": 10,
  "type": "PRIVATE",
  "name": null,
  "creatorId": 1,
  "participants": [
    { "userId": 1, "fullName": "Alice", "avatar": null, "joinedAt": 1790000000000 },
    { "userId": 2, "fullName": "Bob", "avatar": null, "joinedAt": 1790000000000 }
  ],
  "createdAt": 1790000000000,
  "updatedAt": 1790000000000
}
```

### `GET /api/v1/conversations` — danh sách

Không có query/body. `data` là mảng `ConversationListResponse`, sắp xếp theo `updatedAt` giảm dần. Mỗi phần tử gồm `conversationId`, `type`, `name`, `participants`, `lastMessage` (có thể `null`) và `updatedAt`. `lastMessage` gồm `messageId`, `senderId`, `content`, `messageType`, `createdAt`. Dùng `[]` cho trạng thái chưa có hội thoại; endpoint hiện không có phân trang.

### `GET /api/v1/conversations/{conversationId}` — chi tiết

Không có body. `data` là `ConversationResponse` như ví dụ trên. Conversation không tồn tại trả `404`; người gọi không còn là active participant trả `403`.

### `GET /api/v1/conversations/{conversationId}/participants` — thành viên

Không có body. `data` là mảng `{ "userId": 1, "fullName": "Alice", "avatar": null, "joinedAt": 1790000000000 }` của **các thành viên active**. PRIVATE có tối đa hai người; GROUP có thể nhiều hơn. Conversation không tồn tại trả `404`; người gọi không còn là active participant trả `403`.

### `POST /api/v1/conversations/{conversationId}/messages` — gửi tin nhắn 1-1

Body: `{ "content": "Xin chào!", "messageType": "TEXT" }`. `content` bắt buộc, không blank, tối đa 5000 ký tự. Chỉ hỗ trợ `TEXT`; conversation phải là `PRIVATE` với đúng hai active participants. `senderId` do server lấy từ token, mọi `senderId` client tự gửi bị bỏ qua. `data` là `{ "messageId": 25, "conversationId": 10, "senderId": 1, "content": "Xin chào!", "messageType": "TEXT", "createdAt": 1790000100000 }`; gửi thành công cập nhật `conversation.updatedAt`. Body sai trả `400`, không phải active participant `403`, conversation không tồn tại `404`, không đúng nghiệp vụ 1-1 `409`.

Controller hiện chưa có API đọc lịch sử message hoặc WebSocket. Danh sách conversation chỉ có `lastMessage`; frontend chưa thể tải toàn bộ lịch sử qua các route này.

## Storage image

`StorageController` hiện khai báo hai route dưới đây, đều cần Bearer USER/ADMIN. **Chưa có `POST /api/storage/images`**. Avatar được upload trong lúc đăng ký qua `POST /api/users/register`; `PATCH /api/users/profile` chỉ nhận chuỗi avatar. `StorageService.uploadChatImage` là phương thức nội bộ, chưa có route upload chat image.

Key hợp lệ gồm `images/{uuid-v4}.{jpg|png|webp|gif}`, `avatars/{userId}/{uuid-v4}.{ext}`, `chat-attachments/{conversationId}/{uuid-v4}.{ext}`; key avatar/chat cũ có thêm `/images/` trước UUID vẫn được chấp nhận cho thao tác URL/xóa. UUID phải viết thường và ID phải dương. Key khác sẽ trả `400`.

### `GET /api/storage/images/url?key=...` — lấy URL

Truyền `key` bằng query parameter có URL encoding, ví dụ `new URLSearchParams({ key })`. `data` là `{ "url": "https://..." }`. Key không hợp lệ trả `400`; object không tồn tại trả `404`. URL có thể là presigned URL có thời hạn; dùng giá trị trả về mới khi cần.

### `DELETE /api/storage/images` — xóa object

Gửi `Content-Type: application/json` với body `{ "key": "avatars/1/550e8400-e29b-41d4-a716-446655440000.png" }`. Thành công trả `200`, không có `data`; key rỗng/không hợp lệ trả `400`. Đây là thao tác xóa object, frontend chỉ gọi khi thật sự cần xóa file.

## Ví dụ gọi API bằng `fetch`

```ts
const baseUrl = "http://localhost:8080";

async function api<T>(path: string, options: RequestInit = {}, accessToken?: string): Promise<T> {
  const headers = new Headers(options.headers);
  if (options.body && !(options.body instanceof FormData)) {
    headers.set("Content-Type", "application/json");
  }
  if (accessToken) headers.set("Authorization", `Bearer ${accessToken}`);

  const response = await fetch(`${baseUrl}${path}`, { ...options, headers });
  const payload = await response.json();
  if (!response.ok) throw { status: response.status, message: payload.message, errors: payload.errors };
  return payload.data as T;
}

const auth = await api<{ user: unknown; tokens: { accessToken: string; refreshToken: string } }>(
  "/api/auth/login",
  { method: "POST", body: JSON.stringify({ username: "alice", password: "StrongPass1!" }) }
);
const token = auth.tokens.accessToken;
const me = await api("/api/users/me", {}, token);
const profile = await api("/api/users/profile", {
  method: "PATCH",
  body: JSON.stringify({ firstName: "Alice", phone: "0901234567" })
}, token);
const message = await api("/api/v1/conversations/10/messages", {
  method: "POST",
  body: JSON.stringify({ content: "Xin chào!", messageType: "TEXT" })
}, token);
```

Khi API trả `401`, chỉ thử refresh nếu đang có refresh token hợp lệ; lưu lại cả access/refresh token mới rồi gọi lại request phù hợp. Nếu refresh cũng trả `401`, xóa token và chuyển về màn hình đăng nhập. `403` là thiếu quyền hoặc tài nguyên không cho phép truy cập, không giải quyết bằng refresh token.
