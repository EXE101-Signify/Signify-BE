# api-response

[![Java](https://img.shields.io/badge/Java-17%2B-orange)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-green)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-MIT-blue)](LICENSE)

Thư viện Java cung cấp **chuẩn hoá API response** và **xử lý exception tự động** cho các project Spring Boot.

Chỉ cần thêm dependency — không cần cấu hình thủ công.

---

## 📦 Cài đặt

### Gradle (Kotlin DSL)

```kotlin
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("io.github.gwuy.apiresponse:api-response:1.0.0")
}
```

### Gradle (Groovy DSL)

```groovy
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation 'io.github.gwuy.apiresponse:api-response:1.0.0'
}
```

### Maven

```xml
<dependency>
    <groupId>fptu.exe202.signify</groupId>
    <artifactId>api-response</artifactId>
    <version>1.0.0</version>
</dependency>
```

> **Note:** Hiện tại library được publish lên Maven Local. Chạy `./gradlew publishToMavenLocal` trong project library trước khi sử dụng.

---

## ✨ Tính năng

| Tính năng | Mô tả |
|-----------|--------|
| `ApiResponse<T>` | Response wrapper thống nhất cho mọi API |
| Custom Exceptions | `BadRequestException`, `ResourceNotFoundException`, `UnauthorizedException`, `ForbiddenException`, `ConflictException` |
| Global Exception Handler | Tự động bắt và chuyển đổi exception thành `ApiResponse` |
| Validation Error Handling | Hỗ trợ Jakarta Validation (`@NotBlank`, `@Email`, ...) |
| Spring Security (optional) | Xử lý `AuthenticationException`, `AccessDeniedException` nếu có Spring Security |
| Zero Configuration | Tự động đăng ký qua Spring Boot AutoConfiguration |

---

## 🚀 Bắt đầu nhanh

### 1. Response format

Mọi API response đều có cấu trúc thống nhất:

```json
{
    "success": true,
    "status": 200,
    "message": "Success",
    "data": { ... },
    "errors": null,
    "timestamp": "2026-09-17T10:00:00+07:00",
    "path": "/api/v1/users"
}
```

| Field | Kiểu | Mô tả |
|-------|------|--------|
| `success` | `boolean` | `true` nếu thành công, `false` nếu lỗi |
| `status` | `int` | HTTP status code |
| `message` | `String` | Thông báo mô tả kết quả |
| `data` | `T` | Dữ liệu trả về (generic type) |
| `errors` | `Map<String, String>` | Chi tiết lỗi validation theo field (nếu có) |
| `timestamp` | `OffsetDateTime` | Thời điểm tạo response |
| `path` | `String` | Request URI (chỉ có trong error response) |

> `data`, `errors`, `path` sẽ **không xuất hiện** trong JSON nếu giá trị là `null`.

### 2. Sử dụng trong Controller

```java
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    // GET — trả về dữ liệu
    @GetMapping("/{id}")
    public ApiResponse<UserResponse> getUser(@PathVariable Long id) {
        return ApiResponse.success(userService.getUser(id));
    }

    // POST — trả về dữ liệu với message tuỳ chỉnh
    @PostMapping
    public ApiResponse<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        UserResponse user = userService.createUser(request);
        return ApiResponse.success("User created successfully", user);
    }

    // DELETE — không có dữ liệu trả về
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ApiResponse.success("User deleted successfully", null);
    }
}
```

### 3. Sử dụng trong Service

Chỉ cần **throw exception** — không cần `try/catch`:

```java
@Service
public class UserService {

    private final UserRepository repository;
    private final UserMapper mapper;

    public UserResponse getUser(Long id) {
        User user = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return mapper.toResponse(user);
    }

    public UserResponse createUser(CreateUserRequest request) {
        if (repository.existsByUsername(request.getUsername())) {
            throw new ConflictException("Username already exists");
        }
        User user = mapper.toEntity(request);
        return mapper.toResponse(repository.save(user));
    }
}
```

`GlobalExceptionHandler` sẽ tự động bắt exception và trả về `ApiResponse` tương ứng.

---

## 📋 ApiResponse Factory Methods

### Success

```java
// Mặc định: status 200, message "Success"
ApiResponse.success(data);

// Tuỳ chỉnh message
ApiResponse.success("User created successfully", data);

// Không có data
ApiResponse.success("User deleted successfully", null);
```

### Error

```java
// Error đơn giản
ApiResponse.error(404, "User not found");

// Error với validation errors
Map<String, String> errors = Map.of(
    "username", "must not be blank",
    "email", "must be a valid email"
);
ApiResponse.error(400, "Validation failed", errors);
```

> Thông thường bạn **không cần gọi** `ApiResponse.error()` trực tiếp. Chỉ cần throw exception, `GlobalExceptionHandler` sẽ tạo error response tự động.

---

## ⚠️ Custom Exceptions

| Exception | HTTP Status | Ví dụ |
|-----------|:-----------:|-------|
| `BadRequestException` | 400 | `throw new BadRequestException("Invalid request")` |
| `UnauthorizedException` | 401 | `throw new UnauthorizedException("Authentication required")` |
| `ForbiddenException` | 403 | `throw new ForbiddenException("Access denied")` |
| `ResourceNotFoundException` | 404 | `throw new ResourceNotFoundException("User not found")` |
| `ConflictException` | 409 | `throw new ConflictException("Username already exists")` |

### Với Error Code (optional)

```java
throw new BadRequestException("Invalid request", "ERR_INVALID_INPUT");
throw new ResourceNotFoundException("User not found", "USER_NOT_FOUND");
```

### Tạo Custom Exception của riêng bạn

Kế thừa `BaseException`:

```java
public class PaymentFailedException extends BaseException {

    public PaymentFailedException(String message) {
        super(HttpStatus.PAYMENT_REQUIRED, message); // 402
    }
}
```

---

## 🛡️ Global Exception Handler

Library tự động xử lý các exception sau:

| Exception | HTTP Status | Message |
|-----------|:-----------:|---------|
| `BaseException` (và các subclass) | Tuỳ thuộc exception | Message từ exception |
| `MethodArgumentNotValidException` | 400 | `"Validation failed"` + chi tiết lỗi từng field |
| `ConstraintViolationException` | 400 | `"Validation failed"` + chi tiết lỗi từng field |
| `HttpMessageNotReadableException` | 400 | `"Malformed request body"` |
| `MissingServletRequestParameterException` | 400 | `"Missing required parameter: {name}"` |
| `MissingPathVariableException` | 400 | `"Missing path variable: {name}"` |
| `HttpRequestMethodNotSupportedException` | 405 | `"Method '{method}' is not supported"` |
| `NoResourceFoundException` | 404 | `"Resource not found"` |
| `AuthenticationException` ★ | 401 | `"Unauthorized"` |
| `AccessDeniedException` ★ | 403 | `"Forbidden"` |
| `Exception` (fallback) | 500 | `"Internal server error"` |

> ★ Chỉ khi có Spring Security trên classpath.

---

## ✅ Validation

Sử dụng Jakarta Validation annotations bình thường:

```java
public class CreateUserRequest {

    @NotBlank(message = "Username must not be blank")
    private String username;

    @Email(message = "Email must be valid")
    @NotBlank(message = "Email must not be blank")
    private String email;

    // getters, setters
}
```

Controller sử dụng `@Valid`:

```java
@PostMapping
public ApiResponse<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
    return ApiResponse.success(userService.createUser(request));
}
```

Khi validation fail, response tự động trả về:

```json
{
    "success": false,
    "status": 400,
    "message": "Validation failed",
    "errors": {
        "username": "Username must not be blank",
        "email": "Email must be valid"
    },
    "timestamp": "2026-09-17T10:00:00+07:00",
    "path": "/api/v1/users"
}
```

---

## 🔐 Spring Security (Optional)

Library **không bắt buộc** Spring Security. Nếu project của bạn có Spring Security, library sẽ tự động xử lý:

- `AuthenticationException` → HTTP 401
- `AccessDeniedException` → HTTP 403

Nếu project **không sử dụng** Spring Security, các handler này sẽ không được đăng ký — không ảnh hưởng gì.

> Library **không thay đổi** Security configuration của application.

---

## 📁 Ví dụ Response

### ✅ Success — GET /api/v1/users/1

```json
{
    "success": true,
    "status": 200,
    "message": "Success",
    "data": {
        "id": 1,
        "username": "john",
        "email": "john@example.com"
    },
    "timestamp": "2026-09-17T10:00:00+07:00"
}
```

### ✅ Success — POST /api/v1/users

```json
{
    "success": true,
    "status": 200,
    "message": "User created successfully",
    "data": {
        "id": 2,
        "username": "jane"
    },
    "timestamp": "2026-09-17T10:00:00+07:00"
}
```

### ✅ Success — DELETE /api/v1/users/1

```json
{
    "success": true,
    "status": 200,
    "message": "User deleted successfully",
    "timestamp": "2026-09-17T10:00:00+07:00"
}
```

### ❌ Not Found — GET /api/v1/users/999

```json
{
    "success": false,
    "status": 404,
    "message": "User not found",
    "timestamp": "2026-09-17T10:00:00+07:00",
    "path": "/api/v1/users/999"
}
```

### ❌ Conflict — POST /api/v1/users

```json
{
    "success": false,
    "status": 409,
    "message": "Username already exists",
    "timestamp": "2026-09-17T10:00:00+07:00",
    "path": "/api/v1/users"
}
```

### ❌ Validation Error — POST /api/v1/users

```json
{
    "success": false,
    "status": 400,
    "message": "Validation failed",
    "errors": {
        "username": "Username must not be blank",
        "email": "Email must be valid"
    },
    "timestamp": "2026-09-17T10:00:00+07:00",
    "path": "/api/v1/users"
}
```

### ❌ Internal Server Error

```json
{
    "success": false,
    "status": 500,
    "message": "Internal server error",
    "timestamp": "2026-09-17T10:00:00+07:00",
    "path": "/api/v1/users"
}
```

> Stack trace, class name, SQL, database info **không bao giờ** được expose cho client. Chi tiết lỗi chỉ được log trên server.

---

## 🔧 Yêu cầu

- **Java** 17+
- **Spring Boot** 3.x

---

## 🏗️ Build từ source

```bash
# Build và chạy test
./gradlew clean build

# Publish lên Maven Local
./gradlew publishToMavenLocal
```

---

## 📐 Package Structure

```
fptu.exe202.signify.apiresponse
├── response
│   └── ApiResponse          ← Generic response wrapper
├── exception
│   ├── BaseException        ← Abstract base (extend để tạo exception mới)
│   ├── BadRequestException
│   ├── ResourceNotFoundException
│   ├── UnauthorizedException
│   ├── ForbiddenException
│   └── ConflictException
├── handler
│   ├── GlobalExceptionHandler       ← @RestControllerAdvice
│   └── SecurityExceptionHandler     ← Spring Security (conditional)
├── model
│   └── ValidationError      ← Validation error builder utility
└── config
    └── ApiResponseAutoConfiguration ← Spring Boot 3 AutoConfiguration
```

---

## 📜 License

MIT
