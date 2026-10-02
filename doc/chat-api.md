# Signify Chat API Documentation

This document covers the REST API endpoints for the Chat module, including conversation creation, participant management, and 1-1 message sending.

## Base Path
`/api/conversations`

## Authentication
All endpoints require a valid JWT token passed in the `Authorization` header.
```http
Authorization: Bearer <your-jwt-token>
```

---

## 1. Create Conversation
Creates a new conversation. Currently supports `PRIVATE` (1-1) and `GROUP` conversations.
If a `PRIVATE` conversation already exists between the two users, it will return the existing conversation instead of creating a duplicate.

**Endpoint:** `POST /api/conversations`

### Request Body (PRIVATE)
```json
{
  "type": "PRIVATE",
  "participantIds": [2]
}
```

### Request Body (GROUP)
```json
{
  "type": "GROUP",
  "name": "Project Team",
  "participantIds": [2, 3, 4]
}
```

### Response (200 OK)
```json
{
  "success": true,
  "status": 200,
  "message": "Conversation created successfully",
  "data": {
    "conversationId": 1,
    "type": "PRIVATE",
    "name": null,
    "creatorId": 1,
    "participants": [
      {
        "userId": 1,
        "fullName": "Nguyen A",
        "avatar": "avatar_url",
        "joinedAt": 1695900000000
      },
      {
        "userId": 2,
        "fullName": "Tran B",
        "avatar": null,
        "joinedAt": 1695900000000
      }
    ],
    "createdAt": 1695900000000,
    "updatedAt": 1695900000000
  }
}
```

### Validation Rules & Error Codes
* **400 Bad Request**:
    * `participantIds` must not be empty.
    * Cannot create a conversation with yourself (must have at least one other user).
    * `type` must be either `PRIVATE` or `GROUP`.
    * `PRIVATE` conversation must have exactly 1 target participant (total 2 including creator).
    * `GROUP` conversation must have a non-blank `name`.
    * One or more `participantIds` do not exist in the system.

---

## 2. Get Conversations List
Retrieves a list of conversations for the currently authenticated user, sorted by `updatedAt` descending. Includes a preview of the last message and participants.

**Endpoint:** `GET /api/conversations`

### Response (200 OK)
```json
{
  "success": true,
  "status": 200,
  "message": "Success",
  "data": [
    {
      "conversationId": 1,
      "type": "PRIVATE",
      "name": null,
      "participants": [
        {
          "userId": 1,
          "fullName": "Nguyen A",
          "avatar": "avatar_url",
          "joinedAt": 1695900000000
        },
        {
          "userId": 2,
          "fullName": "Tran B",
          "avatar": null,
          "joinedAt": 1695900000000
        }
      ],
      "lastMessage": {
        "messageId": 15,
        "senderId": 2,
        "content": "Hello there!",
        "messageType": "TEXT",
        "createdAt": 1695900100000
      },
      "updatedAt": 1695900100000
    }
  ]
}
```

---

## 3. Get Conversation Detail
Retrieves the details of a specific conversation.

**Endpoint:** `GET /api/conversations/{conversationId}`

### Response (200 OK)
```json
{
  "success": true,
  "status": 200,
  "message": "Success",
  "data": {
    "conversationId": 1,
    "type": "PRIVATE",
    "name": null,
    "creatorId": 1,
    "participants": [
      {
        "userId": 1,
        "fullName": "Nguyen A",
        "avatar": "avatar_url",
        "joinedAt": 1695900000000
      },
      {
        "userId": 2,
        "fullName": "Tran B",
        "avatar": null,
        "joinedAt": 1695900000000
      }
    ],
    "createdAt": 1695900000000,
    "updatedAt": 1695900000000
  }
}
```

### Validation Rules & Error Codes
* **404 Not Found**: The conversation does not exist.
* **403 Forbidden**: The conversation exists, but the authenticated user is not an active participant of it.

---

## 4. Get Conversation Participants
Retrieves the list of active participants for a specific conversation. `PRIVATE` conversations have up to 2 active participants; `GROUP` conversations can have more. Does not expose sensitive user data.

**Endpoint:** `GET /api/conversations/{conversationId}/participants`

### Response (200 OK)
```json
{
  "success": true,
  "status": 200,
  "message": "Success",
  "data": [
    {
      "userId": 1,
      "fullName": "Nguyen A",
      "avatar": "avatar_url",
      "joinedAt": 1695900000000
    },
    {
      "userId": 2,
      "fullName": "Tran B",
      "avatar": null,
      "joinedAt": 1695900000000
    }
  ]
}
```

### Validation Rules & Error Codes
* **404 Not Found**: The conversation does not exist.
* **403 Forbidden**: The authenticated user is not an active participant of the conversation.

---

## 5. Send Message (1-1)
Sends a plain text message to an existing `PRIVATE` conversation. The authenticated user's ID is always used as the sender. The conversation must have exactly two active participants.

**Endpoint:** `POST /api/conversations/{conversationId}/messages`

### Request Body
```json
{
  "content": "Hello there!",
  "messageType": "TEXT"
}
```

Only `TEXT` is supported. `content` must not be blank and is limited to 5000 characters. Any `senderId` supplied by a client is ignored; identity comes from the JWT principal.

### Response (200 OK)
```json
{
  "success": true,
  "status": 200,
  "message": "Message sent successfully",
  "data": {
    "messageId": 15,
    "conversationId": 1,
    "senderId": 1,
    "content": "Hello there!",
    "messageType": "TEXT",
    "createdAt": 1695900100000
  }
}
```

`createdAt` and the conversation's `updatedAt` use Unix epoch milliseconds.

### Validation Rules & Error Codes
* **400 Bad Request**: blank or overlong content, missing message type, or a type other than `TEXT`.
* **403 Forbidden**: authenticated user is not an active participant (including a participant who has been deactivated).
* **404 Not Found**: conversation does not exist.
* **409 Conflict**: conversation is not `PRIVATE` or does not have exactly two active participants.

---

## 6. Send an Attachment Message (1-1)

Uploads a file and creates its message in the same request. The authenticated user is the sender. The conversation must be `PRIVATE` with exactly two active participants.

**Endpoint:** `POST /api/conversations/{conversationId}/messages/attachment`

**Content-Type:** `multipart/form-data`

| Part | Required | Description |
| --- | --- | --- |
| `file` | Yes | One JPEG, PNG, WebP, GIF, or PDF file. |
| `content` | No | Caption, up to 5000 characters. |

Example:

```bash
curl -X POST "http://localhost:8080/api/conversations/1/messages/attachment" \
  -H "Authorization: Bearer <token>" \
  -F "file=@photo.png;type=image/png" \
  -F "content=Here is the photo"
```

**Response (200 OK):**

```json
{
  "success": true,
  "status": 200,
  "message": "Attachment sent successfully",
  "data": {
    "messageId": 16,
    "attachmentId": 4,
    "conversationId": 1,
    "senderId": 1,
    "content": "Here is the photo",
    "fileName": "photo.png",
    "mimeType": "image/png",
    "fileSize": 123456,
    "createdAt": 1695900200000
  }
}
```

The message has `messageType` `FILE`. The response omits the object key and download URL; request a signed URL using the endpoint below. `createdAt` is Unix epoch milliseconds.

### File validation and storage

| MIME type | Extensions |
| --- | --- |
| `image/jpeg` | `.jpg`, `.jpeg` |
| `image/png` | `.png` |
| `image/webp` | `.webp` |
| `image/gif` | `.gif` |
| `application/pdf` | `.pdf` |

The file limit uses `storage.image.max-size` (`STORAGE_IMAGE_MAX_SIZE`, default 5 MiB). The multipart request limit uses `STORAGE_MULTIPART_MAX_REQUEST_SIZE` (default 6 MB). MIME type, extension, and file signature must match. Signature checking identifies the format; it is not a malware scan or full file decode.

The server generates a UUID object key under `chat-attachments/{conversationId}/`. It stores that key in `message_attachment.file_url`, with the original filename only as metadata. File bytes stay in the configured S3 or Cloudflare R2 storage, not PostgreSQL. If the database transaction rolls back after upload, the server attempts to delete the uploaded object.

The storage bucket must deny anonymous reads. A public bucket policy or public R2 domain would allow direct access to an object key even though this API checks permissions.

### Errors

- **400 Bad Request:** empty file, unsupported MIME type or extension, mismatched signature, or caption longer than 5000 characters.
- **403 Forbidden:** sender is not an active participant.
- **404 Not Found:** conversation does not exist.
- **409 Conflict:** conversation is not `PRIVATE` or does not have exactly two active participants.
- **413 Payload Too Large:** file or multipart request exceeds its configured limit.
- **502 Bad Gateway:** object storage is unavailable.

## 7. Get Attachment Metadata and Access URL

**Endpoint:** `GET /api/conversations/attachments/{attachmentId}`

The server resolves the attachment's message and conversation, then checks that the requester is an active participant. Attachments of deleted messages are unavailable.

**Response (200 OK):**

```json
{
  "success": true,
  "status": 200,
  "message": "Success",
  "data": {
    "attachmentId": 4,
    "messageId": 16,
    "fileName": "photo.png",
    "mimeType": "image/png",
    "fileSize": 123456,
    "createdAt": 1695900200000,
    "url": "<temporary signed URL>"
  }
}
```

The signed URL expires after `STORAGE_PRESIGNED_URL_DURATION` (default 15 minutes). Request a new URL after expiry.

### Errors

- **403 Forbidden:** requester is not an active participant.
- **404 Not Found:** attachment or its message is missing, the message is deleted, or the conversation is missing.
- **502 Bad Gateway:** object storage is unavailable.

### Upload flow choice

The API supports upload and message creation in one request. Upload first, attach to a message later is not supported: `message_attachment.message_id` is required and the project has no pending attachment state.

## 8. Get Message History (1-1)

**Endpoint:** `GET /api/conversations/{conversationId}/messages?limit=30&before={messageId}`

Only active participants of a `PRIVATE` conversation can read its history. `limit` defaults to 30 and must be between 1 and 100. Omit `before` for the newest page; for older messages, pass the previous response's `nextCursor`. Message IDs are the cursor, so new messages arriving during scrolling do not shift older pages.

**Response (200 OK):**

```json
{
  "success": true,
  "status": 200,
  "message": "Success",
  "data": {
    "messages": [
      {
        "messageId": 16,
        "conversationId": 1,
        "senderId": 1,
        "content": "Here is the photo",
        "messageType": "FILE",
        "createdAt": 1695900200000,
        "editedAt": null,
        "attachments": [
          {
            "attachmentId": 4,
            "fileName": "photo.png",
            "mimeType": "image/png",
            "fileSize": 123456,
            "createdAt": 1695900200000
          }
        ]
      }
    ],
    "nextCursor": 16,
    "hasMore": true
  }
}
```

Messages are returned newest first. The response has at most `limit` messages; `hasMore` indicates whether older messages exist. `nextCursor` is the oldest returned message ID when `hasMore` is true, otherwise `null`. Deleted messages and deleted attachments are omitted. Attachment URLs are requested separately through the attachment metadata endpoint.

**Errors:** 400 for invalid `limit` or `before`, 403 for a nonparticipant, 404 for a missing conversation, and 409 for a conversation that is not `PRIVATE`.

## 9. Edit a TEXT Message

**Endpoint:** `PUT /api/v1/conversations/{conversationId}/messages/{messageId}`

Only the original sender, while still an active participant, can edit a message in the specified `PRIVATE` conversation. The message must exist, belong to that conversation, be of type `TEXT`, and not be deleted. Content must be nonblank and at most 5000 characters. Editing sets `edited_at` to Unix epoch milliseconds; `created_at` remains unchanged.

**Request:**

```json
{"content":"Updated message"}
```

**Response (200 OK):**

```json
{
  "success": true,
  "status": 200,
  "message": "Message updated successfully",
  "data": {
    "messageId": 16,
    "conversationId": 1,
    "senderId": 1,
    "content": "Updated message",
    "messageType": "TEXT",
    "createdAt": 1695900200000,
    "editedAt": 1695900300000
  }
}
```

Returns 400 for blank or overlong content, 403 for a nonparticipant or non-sender, 404 for a missing, deleted, or wrong-conversation message, and 409 for a non-TEXT message or non-PRIVATE conversation. Edit and delete lock the same message row within a transaction, so whichever operation acquires the lock first is applied first. An edit waiting behind a delete returns 404. This service is the future persistence boundary for `MESSAGE_UPDATED`; no realtime event is sent yet.

## 10. Remove a Message or Attachment

Only the sender, while still an active participant, can remove their message or attachment. Both operations use soft deletion: `message.is_deleted` already exists, and `message_attachment.is_deleted` was added for individual attachment removal. Repeating a removal returns 404. A removed attachment no longer returns metadata or an access URL; removed messages are omitted from the conversation's last-message preview.

### Remove a message

**Endpoint:** `DELETE /api/v1/conversations/{conversationId}/messages/{messageId}`

The message must belong to the specified `PRIVATE` conversation. Removing it also marks its attachments as deleted. After the database commit, the server attempts to delete their storage objects.

**Response (200 OK):** `{"success":true,"status":200,"message":"Message removed successfully","data":null}`

### Remove one attachment

**Endpoint:** `DELETE /api/conversations/attachments/{attachmentId}`

This marks only the attachment as deleted. Its message remains, including any caption. After the database commit, the server attempts to delete the storage object.

**Response (200 OK):** `{"success":true,"status":200,"message":"Attachment removed successfully","data":null}`

Both endpoints return 403 if the requester is outside the conversation or is not the sender, and 404 if the target is missing or already removed. Message removal returns 409 if the conversation is not `PRIVATE`. An already issued signed URL may remain usable until its expiry if storage deletion fails; the server logs that failure for cleanup.

Message removal locks the row in the same transaction used by editing. It sets `message.is_deleted=true` without deleting the message row. `MESSAGE_DELETED` can be published after persistence when realtime delivery is added; this API does not send it yet.

---

## Postman Testing Guide

### Prerequisites
1. Setup a standard Environment in Postman (e.g., `Signify_Local`) with a variable `base_url` set to `http://localhost:8080`.
2. Authenticate and obtain a JWT Token, store it in an environment variable `token`.
3. In your requests, go to the **Authorization** tab, select **Bearer Token**, and use `{{token}}`.

### 1. Test: Create PRIVATE Conversation
* **Method:** `POST`
* **URL:** `{{base_url}}/api/conversations`
* **Body (raw JSON):**
  ```json
  {
    "type": "PRIVATE",
    "participantIds": [2]
  }
  ```
* **Expected Result:** 200 OK, returns conversation detail with 2 participants.

### 2. Test: Prevent duplicate PRIVATE Conversation
* Run the exact same request as Test 1 again.
* **Expected Result:** 200 OK, returns the *same* `conversationId` as Test 1, not a new one.

### 3. Test: Cannot chat with self
* **Method:** `POST`
* **URL:** `{{base_url}}/api/conversations`
* **Body (raw JSON):**
  ```json
  {
    "type": "PRIVATE",
    "participantIds": []
  }
  ```
  *(Or if your user ID is 1, send `[1]`)*
* **Expected Result:** 400 Bad Request, message: "Cannot create a conversation with yourself" (or similar validation error).

### 4. Test: Get Conversations List
* **Method:** `GET`
* **URL:** `{{base_url}}/api/conversations`
* **Expected Result:** 200 OK, array of conversations.

### 5. Test: Get Participants
* **Method:** `GET`
* **URL:** `{{base_url}}/api/conversations/1/participants`  *(Replace `1` with an actual ID from Test 4)*
* **Expected Result:** 200 OK, array of active participants containing `userId`, `fullName`, `avatar`, and `joinedAt`.

There is currently no WebSocket endpoint in this controller. The conversation list contains only `lastMessage`; use the message history endpoint for older messages.

### 6. Test: Membership Validation (403 Forbidden)
* Use an account that is *not* part of conversation ID `1`.
* **Method:** `GET`
* **URL:** `{{base_url}}/api/conversations/1/participants`
* **Expected Result:** 403 Forbidden, message: "You are not a participant of this conversation".

### 7. Test: Not Found (404 Not Found)
* **Method:** `GET`
* **URL:** `{{base_url}}/api/conversations/99999/participants`
* **Expected Result:** 404 Not Found, message: "Conversation not found".

### 8. Send TEXT Message
* **Method:** `POST`
* **URL:** `{{base_url}}/api/conversations/1/messages` *(replace `1` with an existing PRIVATE conversation ID)*
* **Body (raw JSON):**
  ```json
  {
    "content": "Hello there!",
    "messageType": "TEXT"
  }
  ```
* **Expected Result:** 200 OK with the persisted message, authenticated `senderId`, and epoch-millisecond `createdAt`.

### 9. Send Attachment Message
* **Method:** `POST`
* **URL:** `{{base_url}}/api/conversations/1/messages/attachment`
* **Body:** `form-data`; add `file` as a File and optional `content` as Text.
* **Expected Result:** 200 OK with `messageId` and `attachmentId`.

### 10. Get Attachment URL
* **Method:** `GET`
* **URL:** `{{base_url}}/api/conversations/attachments/4` *(replace `4` with the returned `attachmentId`)*
* **Expected Result:** 200 OK with file metadata and a temporary signed `url`.

### 11. Remove Message
* **Method:** `DELETE`
* **URL:** `{{base_url}}/api/conversations/1/messages/16` *(replace IDs with a message sent by the authenticated user)*
* **Expected Result:** 200 OK. The message and its attachment are no longer available.

### 12. Remove Attachment Only
* **Method:** `DELETE`
* **URL:** `{{base_url}}/api/conversations/attachments/4` *(replace `4` with an attachment sent by the authenticated user)*
* **Expected Result:** 200 OK. The attachment URL endpoint now returns 404; the message remains.

### 13. Scroll Older Messages
* **Method:** `GET`
* **URL:** `{{base_url}}/api/conversations/1/messages?limit=30` for the first page.
* **Next page:** `{{base_url}}/api/conversations/1/messages?limit=30&before={{nextCursor}}` using the returned `nextCursor`.
* **Expected Result:** At most 30 messages, newest first; stop when `hasMore` is `false`.

### 14. Edit TEXT Message
* **Method:** `PUT`
* **URL:** `{{base_url}}/api/v1/conversations/1/messages/16`
* **Body (raw JSON):** `{"content":"Updated message"}`
* **Expected Result:** 200 OK with the new `content` and `editedAt`.
