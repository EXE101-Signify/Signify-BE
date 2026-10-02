# User management APIs

All endpoints below require `Authorization: Bearer <access-token>` and use the existing `ApiResponse` envelope. User IDs remain database Long values. No schema migration or new environment variables are required.

| Method | Endpoint | Role | Behavior |
| --- | --- | --- | --- |
| GET | `/api/users/me` | USER, ADMIN | Existing current-user profile |
| PATCH | `/api/users/me` | USER, ADMIN | Update the authenticated user's profile |
| GET | `/api/admin/users?search=&page=0&size=20` | ADMIN | List users including banned users; search username, email or full name |
| GET | `/api/admin/users/{userId}` | ADMIN | Inspect one user including account status and soft-delete timestamp |
| PATCH | `/api/admin/users/{userId}` | ADMIN | Update an active user's profile |
| DELETE | `/api/admin/users/{userId}` | ADMIN | Ban / soft delete; never physically remove rows |
| PATCH | `/api/admin/users/{userId}/unban` | ADMIN | Unban / restore a soft-deleted user |

Pagination is zero-based, with size 1–100 and default 20. Search is case-insensitive, at most 150 characters. Results are ordered by user ID. List response data has `content`, `page`, `size`, `totalElements`, and `totalPages`.

## Update profile

Both PATCH endpoints accept JSON:

```json
{
  "email": "huy@example.com",
  "firstName": "Huy",
  "lastName": "Bui",
  "avatar": "https://cdn.example.com/images/avatar.png"
}
```

Omitted/null fields stay unchanged. Email must be valid and nonblank (maximum 150 characters), names are limited to 100 characters, avatar to 2048 characters with an HTTP(S) URL. Empty names clear the corresponding name; `avatar: ""` clears the avatar. `fullName` is derived from first/last names. To upload a new avatar, use the existing storage upload endpoint and pass its URL here; profile PATCH does not upload files or fetch remote URLs.

The request cannot change user ID, username, password, role, account status, creation/deletion timestamps or email verification. Extra identity/privilege fields are ignored by the existing JSON configuration. `/me` always gets the target ID from the authenticated principal. Changing email resets `emailVerified` to false; keeping the same email preserves verification. An email already used by another user returns 409. This follows the existing email uniqueness check; the database does not have a unique email constraint, so simultaneous updates across different users are not a database-enforced uniqueness guarantee.

The service locks the existing account and user in a transaction, then mutates the managed user. JPA dirty checking updates the same row. It never invokes registration, `new User`, `addUser`, `save` or `persist` for profile updates. A missing user returns 404 without inserting anything. ID, creation timestamp and account identity remain unchanged. Updating a banned user returns 403.

`PATCH /api/users/me` returns the existing `UserResponse` shape in `data`. Admin detail/update/ban returns:

```json
{
  "profile": {
    "userId": 42,
    "username": "huybg",
    "role": "USER",
    "email": "huy@example.com",
    "firstName": "Huy",
    "lastName": "Bui",
    "fullName": "Huy Bui",
    "avatar": null,
    "emailVerified": false
  },
  "status": "BANNED",
  "createdAt": 1758541800000,
  "updatedAt": 1758542700000,
  "deletedAt": 1758542700000
}
```

## Ban and authorization

Ban sets `users.deleted_at` and `updated_at`, sets `account.status=BANNED`, and revokes all refresh sessions in the same transaction. Repeating DELETE is safe and preserves the original deletion timestamp. Administrators cannot ban themselves (403). User/account rows and related data are retained. The admin list/detail endpoints can still inspect banned users.

Unban clears `users.deleted_at`, updates the user timestamp, and sets `account.status=ACTIVE` in the same transaction. Repeating the unban request for an active user is safe and preserves its timestamps. Revoked refresh sessions are never restored, so the user must log in again to obtain a new refresh session. Because access JWTs are stateless, an access token that has not yet expired becomes usable again after the account is active.

The JWT filter first validates the token cryptographically, then checks user/account existence and active state in the database. Authorization uses the current database role, so an old ADMIN token cannot retain admin rights after a role change. Every subsequent bearer request from a banned user is rejected with 401, including requests using a still-unexpired access JWT. Existing login and refresh checks also reject banned users. Requests already in flight at the moment a ban commits may finish.

Admin routes enforce ADMIN in the security chain and method authorization; own-profile updates also enforce ownership at the service layer. Protected routes require USER or ADMIN. Existing registration/login/refresh, public routes and Swagger stay public as before. No role-assignment endpoint is introduced by this change.

Errors: 400 invalid request/pagination; 401 missing/invalid token or inactive bearer identity; 403 insufficient role, self-ban or update of a banned target; 404 missing target; 409 email conflict. Responses do not expose password hashes or tokens and use `Cache-Control: no-store`.

## Verification

`UserManagementServiceTest` checks soft deletion, session revocation, self-ban prevention, missing users, and idempotent ban/unban transitions. `AdminUserControllerSecurityTest` exercises the real security chain for anonymous, USER and ADMIN requests to both lifecycle endpoints.

`AuthServiceBanLifecycleTest` verifies that banned users cannot log in, restored users can create a new session, and refresh sessions revoked during a ban remain invalid after unban.
