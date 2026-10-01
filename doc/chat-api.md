# Signify Chat API Documentation

This document covers the REST API endpoints for the Chat module, including conversation creation, participant management, and 1-1 message sending.

## Base Path
`v1` - `/api/v1/conversations`

The controller also accepts `/api/conversations`. Frontend clients can use `/api/v1/conversations` consistently.

## Authentication
All endpoints require a valid JWT token passed in the `Authorization` header.
```http
Authorization: Bearer <your-jwt-token>
```

---

## 1. Create Conversation
Creates a new conversation. Currently supports `PRIVATE` (1-1) and `GROUP` conversations.
If a `PRIVATE` conversation already exists between the two users, it will return the existing conversation instead of creating a duplicate.

**Endpoint:** `POST /api/v1/conversations`

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

**Endpoint:** `GET /api/v1/conversations`

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

**Endpoint:** `GET /api/v1/conversations/{conversationId}`

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

**Endpoint:** `GET /api/v1/conversations/{conversationId}/participants`

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

**Endpoint:** `POST /api/v1/conversations/{conversationId}/messages`

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

## Postman Testing Guide

### Prerequisites
1. Setup a standard Environment in Postman (e.g., `Signify_Local`) with a variable `base_url` set to `http://localhost:8080`.
2. Authenticate and obtain a JWT Token, store it in an environment variable `token`.
3. In your requests, go to the **Authorization** tab, select **Bearer Token**, and use `{{token}}`.

### 1. Test: Create PRIVATE Conversation
* **Method:** `POST`
* **URL:** `{{base_url}}/api/v1/conversations`
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
* **URL:** `{{base_url}}/api/v1/conversations`
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
* **URL:** `{{base_url}}/api/v1/conversations`
* **Expected Result:** 200 OK, array of conversations.

### 5. Test: Get Participants
* **Method:** `GET`
* **URL:** `{{base_url}}/api/v1/conversations/1/participants`  *(Replace `1` with an actual ID from Test 4)*
* **Expected Result:** 200 OK, array of active participants containing `userId`, `fullName`, `avatar`, and `joinedAt`.

There is currently no REST endpoint for message history and no WebSocket endpoint in this controller. The conversation list contains only `lastMessage`.

### 6. Test: Membership Validation (403 Forbidden)
* Use an account that is *not* part of conversation ID `1`.
* **Method:** `GET`
* **URL:** `{{base_url}}/api/v1/conversations/1/participants`
* **Expected Result:** 403 Forbidden, message: "You are not a participant of this conversation".

### 7. Test: Not Found (404 Not Found)
* **Method:** `GET`
* **URL:** `{{base_url}}/api/v1/conversations/99999/participants`
* **Expected Result:** 404 Not Found, message: "Conversation not found".

### 8. Send TEXT Message
* **Method:** `POST`
* **URL:** `{{base_url}}/api/v1/conversations/1/messages` *(replace `1` with an existing PRIVATE conversation ID)*
* **Body (raw JSON):**
  ```json
  {
    "content": "Hello there!",
    "messageType": "TEXT"
  }
  ```
* **Expected Result:** 200 OK with the persisted message, authenticated `senderId`, and epoch-millisecond `createdAt`.
