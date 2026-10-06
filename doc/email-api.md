# 📧 Email API Documentation — Signify Backend

> **Base URL**: `/api/email`  
> **Content-Type**: `application/json`  
> **Authentication**: Tất cả endpoints đều **public** (không cần token)

---

## 📋 Mục lục

1. [Send OTP (Registration)](#1-send-otp-registration)
2. [Verify OTP (Registration)](#2-verify-otp-registration)
3. [Resend OTP](#3-resend-otp)
4. [Send Password Reset OTP](#4-send-password-reset-otp)
5. [Verify OTP & Reset Password](#5-verify-otp--reset-password)
6. [Error Codes](#error-codes)
7. [Flow Diagrams](#flow-diagrams)

---

## 1. Send OTP (Registration)

Gửi mã OTP 6 số đến email. Sau khi nhận mã, gửi `email` và `otp` trong part JSON `request` của `POST /api/users/register`. Mã hợp lệ được tiêu thụ khi đăng ký và tài khoản mới có `emailVerified=true`.

**Endpoint**: `POST /api/email/otp/send`

### Request Body

```json
{
  "email": "user@example.com"
}
```

| Field   | Type   | Required | Validation           |
|---------|--------|----------|----------------------|
| `email` | string | ✅       | Must be valid email  |

### Response — Success (200)

```json
{
  "success": true,
  "message": "OTP has been sent to user@example.com",
  "data": null
}
```

### Response — Error (409 Conflict)

```json
{
  "success": false,
  "message": "This email is already registered",
  "data": null
}
```

---

## 2. Verify OTP (Registration)

Kiểm tra mã OTP trước khi đăng ký. Bước này không tiêu thụ mã và chưa cập nhật `users.email_verified`; request đăng ký vẫn phải gửi mã OTP để backend xác minh và lưu trạng thái.

**Endpoint**: `POST /api/email/otp/verify`

### Request Body

```json
{
  "email": "user@example.com",
  "otp": "123456"
}
```

| Field   | Type   | Required | Validation               |
|---------|--------|----------|--------------------------|
| `email` | string | ✅       | Must be valid email      |
| `otp`   | string | ✅       | Exactly 6 characters     |

### Response — Success (200)

```json
{
  "success": true,
  "message": "OTP verified successfully",
  "data": null
}
```

### Response — Error (400 Bad Request)

```json
{
  "success": false,
  "message": "OTP is invalid or expired",
  "data": null
}
```

---

## 3. Resend OTP

Gửi lại mã OTP mới đến email (OTP cũ sẽ bị ghi đè).

**Endpoint**: `POST /api/email/otp/resend`

### Request Body

```json
{
  "email": "user@example.com"
}
```

| Field   | Type   | Required | Validation           |
|---------|--------|----------|----------------------|
| `email` | string | ✅       | Must be valid email  |

### Response — Success (200)

```json
{
  "success": true,
  "message": "OTP resent to user@example.com",
  "data": null
}
```

---

## 4. Send Password Reset OTP

Gửi mã OTP để reset password. Email phải thuộc tài khoản đã đăng ký.

**Endpoint**: `POST /api/email/forgot-password/send`

### Request Body

```json
{
  "email": "user@example.com"
}
```

| Field   | Type   | Required | Validation           |
|---------|--------|----------|----------------------|
| `email` | string | ✅       | Must be valid email  |

### Response — Success (200)

```json
{
  "success": true,
  "message": "Password reset OTP sent to user@example.com",
  "data": null
}
```

### Response — Error (404 Not Found)

```json
{
  "success": false,
  "message": "No account found with this email",
  "data": null
}
```

---

## 5. Verify OTP & Reset Password

Xác minh OTP và đổi mật khẩu mới.

**Endpoint**: `POST /api/email/forgot-password/verify`

### Request Body

```json
{
  "email": "user@example.com",
  "otp": "123456",
  "newPassword": "NewSecurePass123"
}
```

| Field         | Type   | Required | Validation                         |
|---------------|--------|----------|------------------------------------|
| `email`       | string | ✅       | Must be valid email                |
| `otp`         | string | ✅       | Exactly 6 characters               |
| `newPassword` | string | ✅       | Min 8 chars, max 72 UTF-8 bytes    |

### Response — Success (200)

```json
{
  "success": true,
  "message": "Password reset successfully",
  "data": null
}
```

### Response — Error (400 Bad Request)

```json
{
  "success": false,
  "message": "OTP is invalid or expired",
  "data": null
}
```

---

## Error Codes

| HTTP Status | Error Message                        | Khi nào                              |
|-------------|--------------------------------------|--------------------------------------|
| `400`       | OTP is invalid or expired            | OTP sai hoặc hết hạn (5 phút)       |
| `400`/`409` | Password validation error            | Mật khẩu không hợp lệ: DTO trả 400, kiểm tra quy tắc trong service trả 409 |
| `404`       | No account found with this email     | Email không tồn tại (forgot password)|
| `409`       | This email is already registered     | Email đã đăng ký (send OTP)          |
| `500`       | Failed to send email                 | Lỗi SMTP server                     |

---

## Flow Diagrams

### 🔹 Registration Flow (Email Verification)

```
1. FE gọi POST /api/email/otp/send  { email }
   ↓
2. BE gửi OTP 6 số qua email
   ↓
3. User nhập OTP trên UI
   ↓
4. FE gọi POST /api/email/otp/verify  { email, otp }
   ↓
5. Nếu thành công → FE gọi POST /api/users/register bằng multipart với part `request` là JSON gồm `email`, `otp` và thông tin tài khoản; part `avatar` tùy chọn. Backend kiểm tra và tiêu thụ OTP, rồi lưu `email_verified=true`.
```

### 🔹 Forgot Password Flow

```
1. FE gọi POST /api/email/forgot-password/send  { email }
   ↓
2. BE check email tồn tại → gửi OTP qua email
   ↓
3. User nhập OTP + mật khẩu mới trên UI
   ↓
4. FE gọi POST /api/email/forgot-password/verify  { email, otp, newPassword }
   ↓
5. BE verify OTP → hash password → cập nhật DB
   ↓
6. Thành công → FE redirect về trang login
```

### 🔹 Resend OTP

```
1. User nhấn "Gửi lại mã"
   ↓
2. FE gọi POST /api/email/otp/resend  { email }
   ↓
3. OTP cũ bị ghi đè, OTP mới gửi qua email
```

---

## ⚙️ Ghi chú kỹ thuật

- **OTP có thời hạn 5 phút** — sau 5 phút sẽ tự hết hạn
- **OTP là 6 chữ số** (100000 – 999999)
- **Mỗi lần gửi/resend**, OTP cũ sẽ bị **ghi đè** bởi OTP mới
- **Sau khi đăng ký hoặc đặt lại mật khẩu thành công**, OTP sẽ bị **xóa** khỏi hệ thống. Endpoint kiểm tra OTP đăng ký không tiêu thụ mã.
- Email được gửi **bất đồng bộ** (async) nên response trả về ngay lập tức
- Tất cả endpoints đều là **public** — không cần Bearer token
