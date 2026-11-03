# Signify Premium: thanh toán và thuê bao

Tài liệu này mô tả mã backend hiện tại. Gói trả phí duy nhất là `PREMIUM_MONTHLY` (Signify Premium), giá **49.000 VND cho một tháng lịch** và gọi video không giới hạn khi còn hiệu lực. Gia hạn do người dùng chủ động mua; hệ thống không tự động thu tiền. Quyền Premium lấy từ kỳ thuê bao đã được kích hoạt, không lấy từ `account.role`.

## Luồng thanh toán

1. Người dùng đã đăng nhập gọi `POST /api/payments/orders` với **body rỗng**. Backend lấy `userId` từ security context, lấy giá/gói đang hoạt động trong database và tạo hoặc dùng lại đơn `PENDING` của người dùng. Không có trường giá hay `userId` do client gửi.
2. Backend tạo link bằng PayOS SDK và trả `checkoutUrl`. Frontend chuyển người dùng đến URL này. `returnUrl` và `cancelUrl` chỉ phục vụ điều hướng; chúng **không xác nhận thanh toán**.
3. PayOS gọi `POST /api/payments/webhooks/payos`. Endpoint public để PayOS truy cập, nhưng backend kiểm tra chữ ký bằng SDK, cấu trúc sự kiện, `orderCode`, VND, số tiền và trạng thái `PAID` từ API PayOS.
4. Trong một database transaction, backend khóa đơn, kiểm tra khóa sự kiện và mã giao dịch đã xử lý, lưu giao dịch, chuyển đơn sang `PAID` và tạo một kỳ thuê bao gắn với đơn. Webhook lặp lại không tạo thêm kỳ.
5. Frontend gọi `GET /api/payments/orders/{paymentOrderId}` và `GET /api/subscription/me` sau khi trở về từ PayOS. Chỉ coi thanh toán thành công khi **backend** báo đơn `PAID` và quyền Premium đang có hiệu lực.

## REST API

Các API trả dữ liệu trong `ApiResponse` dùng chung, trừ webhook trả `{"success":true}` sau khi xử lý thành công. Các thời điểm trong DTO là Unix epoch **milliseconds**. Mọi route trừ danh sách gói và webhook cần Bearer token.

| Method | Route | Kết quả chính |
| --- | --- | --- |
| GET | `/api/subscription/plans` | Mảng gói `PREMIUM_MONTHLY` đang hoạt động; public |
| GET | `/api/subscription/me` | Kỳ hiện tại, kỳ tương lai gần nhất hoặc trạng thái rỗng |
| GET | `/api/subscription/history` | Lịch sử các kỳ của người dùng |
| POST | `/api/payments/orders` | Tạo/dùng lại đơn và checkout link; không có request body |
| GET | `/api/payments/orders/{paymentOrderId}` | Đơn của chính người dùng |
| GET | `/api/payments/history?page=0&size=20` | Danh sách đơn của chính người dùng, tối đa 100 mục/trang |
| POST | `/api/payments/webhooks/payos` | PayOS gọi; chữ ký bắt buộc |

Ví dụ `data` của `POST /api/payments/orders`:

```json
{
  "paymentOrderId": 17,
  "orderCode": 123456789012,
  "totalAmount": 49000,
  "currencyCode": "VND",
  "orderStatus": "PENDING",
  "planCode": "PREMIUM_MONTHLY",
  "orderType": "NEW_SUBSCRIPTION",
  "checkoutUrl": "https://pay.payos.vn/web/example",
  "orderExpiresAt": null,
  "paidAt": null,
  "createdAt": 1790000000000
}
```

`checkoutUrl` trong endpoint đọc đơn/lịch sử có thể là `null` nếu link chưa được lưu. Ví dụ `data` của `GET /api/subscription/me`:

```json
{
  "planCode": "PREMIUM_MONTHLY",
  "planName": "Signify Premium",
  "subscriptionStatus": "ACTIVE",
  "subscriptionStartAt": 1790000000000,
  "subscriptionEndAt": 1792592000000,
  "isPremiumActive": true
}
```

Khi chưa có kỳ, các trường kế hoạch/trạng thái/thời gian là `null`, `isPremiumActive` là `false`. Nếu có kỳ gia hạn tương lai, `subscriptionStatus` là `PENDING` và `isPremiumActive` chỉ phản ánh quyền **tại thời điểm gọi API**. `subscriptionEndAt` của `/me` có thể là ngày hết hạn cuối cùng của các kỳ đã thanh toán, gồm cả kỳ tương lai; dùng `isPremiumActive` để quyết định quyền gọi hiện tại. Lịch sử trả từng kỳ riêng.

Đơn của người khác không được truy cập. Đơn có thể ở `PENDING`, `PAID`, `FAILED`, `CANCELLED` hoặc `EXPIRED`; các trạng thái sau không cấp quyền Premium. Client không gọi webhook và không gửi kết quả thanh toán tự khai báo.

## Gia hạn và quyền gọi

Thời gian kinh doanh dùng `Asia/Ho_Chi_Minh`. Mỗi đơn `PAID` tạo **một kỳ riêng** để giữ lịch sử. Nếu còn kỳ chưa hết hạn, kỳ mới bắt đầu ở ngày hết hạn cuối cùng; nếu đã hết hạn, bắt đầu tại thời điểm xác nhận thanh toán. Ngày kết thúc là ngày bắt đầu cộng một tháng lịch. Kỳ tương lai giữ `PENDING` cho đến khi bắt đầu; job định kỳ đồng bộ trạng thái, nhưng kiểm tra quyền luôn dùng `startAt <= now < endAt` và yêu cầu `activatedAt` tồn tại, nên hết hạn được từ chối ngay cả khi job chưa chạy.

Cả caller và receiver cần Premium hợp lệ khi tạo/nhận cuộc gọi. REST và STOMP/WebRTC signaling kiểm tra quyền trong vòng đời cuộc gọi. Nếu một bên hết hạn khi đang gọi, backend đóng bản ghi cuộc gọi và từ chối signaling/reconnect; trình duyệt phải dừng media P2P khi nhận sự kiện kết thúc.

## Database và cấu hình

Liquibase master nạp v2.0 theo thứ tự: `subscription_plans`, `user_subscriptions`, `payment_orders`, `payment_transactions`, `payment_webhook_events`, rồi seed gói Premium. Khóa ngoại đến `users.user_id`, gói, đơn và kỳ thuê bao. `payment_orders.user_subscription_id` là duy nhất; `order_code`, mã giao dịch PayOS và khóa webhook cũng có unique constraint. Thời gian lưu dạng `BIGINT` epoch milliseconds; webhook payload an toàn được lưu ở `JSONB`. Hibernate dùng `ddl-auto: validate`, không tạo schema.

Biến bắt buộc cho PayOS: `PAYOS_CLIENT_ID`, `PAYOS_API_KEY`, `PAYOS_CHECKSUM_KEY`, `PAYOS_RETURN_URL`, `PAYOS_CANCEL_URL`, `PAYOS_WEBHOOK_URL`. Cấu hình `PAYOS_WEBHOOK_URL` được validate khi khởi động nhưng **không tự đăng ký** với PayOS. Đăng ký URL HTTPS public trỏ đúng `/api/payments/webhooks/payos` trên kênh PayOS; giữ các khóa trong Dokploy secrets/environment, không commit hoặc ghi log. Xem [README](../README.md) để biết các biến DB, Redis và deploy.

## Kiểm thử trước production và giới hạn hiện tại

- Trên PostgreSQL staging, kiểm tra Liquibase, khóa ngoại/unique, Hibernate validation và transaction rollback. Unit test hiện có dùng mock; chưa thay thế được test đồng thời với DB thật.
- Thử thanh toán thật trong môi trường PayOS test, webhook hợp lệ/lặp/sai chữ ký/sai tiền/đơn lạ và việc gia hạn ở cuối tháng, năm nhuận. Redirect thành công đơn lẻ không được cấp quyền.
- Thử hai tài khoản có/không có Premium qua REST và WebSocket, kể cả hết hạn giữa cuộc gọi. Kiểm tra chat thường vẫn dùng được.
- Rủi ro còn mở: nếu PayOS đã tạo link nhưng ứng dụng timeout trước khi lưu `checkoutUrl`, lần retry hiện có thể báo lỗi vì API tra cứu không trả URL checkout. Đơn `PENDING` cũ cũng được dùng lại trước khi kiểm tra lại giá/gói. Cần xử lý và kiểm thử hai tình huống này trước production.
- Chưa xác nhận build, test, migration trên Neon hoặc thanh toán PayOS thật trong lần rà soát Section 10: Gradle bị chặn loopback trong môi trường kiểm tra và Docker daemon không truy cập được. Không coi các bài test là đã pass khi chưa chạy.
