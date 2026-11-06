# Signify Call API

This document describes the call and AI prediction entry points present in the Signify Spring Boot backend. It records source and unit-test evidence; it does not claim a live deployment or browser-to-backend runtime test.

## Authentication

All `/api/**` call endpoints require a valid bearer JWT for an active session and user. `SecurityConfig` permits `USER` and `ADMIN` roles for these routes. The backend gets the caller identity from `CurrentUser`; the create-call request does not accept a caller or receiver user ID.

The WebSocket HTTP handshake at `/ws/chat` is public so a browser can upgrade the connection. Authentication happens in the STOMP `CONNECT` frame with the native header `Authorization: Bearer <JWT>`. The interceptor checks the session and active account again for `SUBSCRIBE` and `SEND` frames.

## REST endpoints

Successful REST responses use the shared `ApiResponse` wrapper. The examples below show the response data fields; IDs and timestamps are illustrative.

### Create a call

```http
POST /api/calls
Authorization: Bearer <JWT>
Content-Type: application/json
```

Request (`CreateVideoCallRequest`):

```json
{
  "conversationId": 7
}
```

Only `conversationId` is accepted. The backend verifies that the caller is an active member of a `PRIVATE` conversation with exactly two distinct active participants, checks that neither participant has blocked the other, and derives the receiver from the other participant.

Both participants must have an active Signify Premium subscription. A participant's `account.role` does not grant call access. The server uses participant locks to reject simultaneous ongoing call requests. See [payment-subscription.md](payment-subscription.md) for subscription validity and renewal.

Success is HTTP 200. `data` is a `VideoCallResponse`:

```json
{
  "id": 42,
  "conversationId": 7,
  "callerId": 10,
  "receiverId": 20,
  "status": "CALLING",
  "startedAt": null,
  "endedAt": null,
  "createdAt": 1720000000000
}
```

### Accept a call

```http
POST /api/calls/{callId}/accept
Authorization: Bearer <JWT>
```

Only the receiver can accept a `CALLING` call. The transition sets `startedAt` and changes the status to `ACCEPTED`. Success is HTTP 200 with the same `VideoCallResponse` fields.

### Reject a call

```http
POST /api/calls/{callId}/reject
Authorization: Bearer <JWT>
```

Only the receiver can reject a `CALLING` call. Success is HTTP 200 with the updated `VideoCallResponse`.

### End a call

```http
POST /api/calls/{callId}/end
Authorization: Bearer <JWT>
```

Either participant can end a `CALLING` or `ACCEPTED` call. The transition sets `endedAt` and changes the status to `COMPLETED`. Retrying `/end` for an already completed call returns that call. Success is HTTP 200 with the updated `VideoCallResponse`.

If the client has lost the call ID, `POST /api/calls/end-current` ends all `CALLING` or `ACCEPTED` calls belonging to the authenticated user. It returns HTTP 200 with an array of ended `VideoCallResponse` objects, or an empty array when there are none. Other users' calls are unaffected.

### Mark a call busy

```http
POST /api/calls/{callId}/busy
Authorization: Bearer <JWT>
```

Only the receiver can mark a `CALLING` call as `BUSY`. Success is HTTP 200 with the updated `VideoCallResponse`.

### Missed call

Unanswered calls become `MISSED` after 60 seconds by default. The timeout job sets `endedAt` and publishes a status event. The caller can also use `/end` to cancel immediately, producing `COMPLETED`. The ring timeout, maximum accepted-call duration (12 hours by default), and sweep interval (15 seconds by default) are configurable with `CALL_RINGING_TIMEOUT_MS`, `CALL_MAX_DURATION_MS`, and `CALL_TIMEOUT_SWEEP_MS`. Accepted calls that exceed the maximum duration become `COMPLETED`, preventing an abandoned call from blocking future calls indefinitely.

### Request an AI prediction and publish it

```http
POST /api/calls/{callId}/predictions
Authorization: Bearer <JWT>
Content-Type: multipart/form-data
```

The multipart request must contain a file part named `image`. For example:

```bash
curl -X POST "http://localhost:8080/api/calls/42/predictions" \
  -H "Authorization: Bearer <JWT>" \
  -F "image=@hand.jpg;type=image/jpeg"
```

The endpoint requires the authenticated requester to be a participant in an `ACCEPTED` call. It validates the call before invoking the AI service and checks it again after inference; if the call became inactive during inference, no event is sent. The Python HTTP API classifies each image independently. Spring Boot groups predictions by `callId` and emits an accepted letter after three consecutive valid predictions of the same A-Z character, each at confidence >= 0.75. A held character emits once. Two consecutive Python `No hand detected` responses release it, allowing the same character to be accepted again. A different character needs its own three consecutive predictions. Low-confidence and malformed results reset a candidate but do not release a held character. There is no cooldown or text buffer. When no letter is accepted, the REST endpoint returns HTTP 204 and sends no STOMP event. When a letter is accepted, it returns HTTP 200 with the `AiPredictionEvent` in `data`:

```json
{
  "eventId": "<generated UUID>",
  "type": "AI_SIGN_PREDICTION",
  "conversationId": 7,
  "callId": 42,
  "letter": "A",
  "confidence": 0.96,
  "timestamp": 1720000000000
}
```

The prediction `timestamp` and `confidence` come from the third confirming AI response. The event is also sent to each call participant through the call-specific user queue described below. No transcript is stored. An AI outage resets an unfinished candidate without releasing a held letter or ending the call. The in-memory decoder state is isolated per call and cleared after a terminal call-status event commits. It is local to one Spring Boot process, so multi-instance deployments need shared per-call state or sticky routing. The HTTP API does not use the webcam program's motion tracker; temporal J/Z recognition remains unsupported in this path.

### Live spelling text controls

For this MVP, the receiver is the signer and the caller is the viewer. Only the authenticated receiver of an `ACCEPTED` call can mutate its live spelling text. The backend checks the call and signer identity for accepted AI frames and for each control; neither a role nor a user ID is accepted in the request body. These controls have no body:

```http
POST /api/calls/{callId}/text/space
POST /api/calls/{callId}/text/delete
POST /api/calls/{callId}/text/clear
Authorization: Bearer <JWT>
```

Only letters accepted by `StableLetterTracker` are appended to the per-call text buffer. `SPACE` appends one ASCII space after nonempty text unless it already ends in a space. `DELETE` removes one trailing character, including a space. `CLEAR` empties the text. Empty `DELETE` and `CLEAR` are safe. Each successful control returns an `AiTextUpdateEvent` in the normal `ApiResponse.data` and sends the same event to both participants on `/user/queue/calls/{callId}`:

```json
{
  "eventId": "<generated UUID>",
  "type": "AI_TEXT_UPDATE",
  "callId": 42,
  "conversationId": 7,
  "text": "HELLO ",
  "timestamp": 1720000000000
}
```

An accepted AI letter also sends `AI_TEXT_UPDATE` with the appended text; the existing `AI_SIGN_PREDICTION` payload remains unchanged. Text state is in memory, separate for each call, and cleared after a terminal call-status event commits. It is not persisted or shared between backend instances. The buffer is literal ASL fingerspelling text, not semantic VSL translation.

## Call lifecycle

`VideoCallStatus` is persisted by name. Only `ACCEPTED` is considered active by `validateActiveCall` and by call-queue subscription authorization.

The receiver's `/accept` request rechecks Premium for both participants. If either entitlement expires during an accepted call, the backend closes the call when signaling/AI access is checked and a scheduled job also reconciles quiet calls. Further STOMP subscription/send and reconnect attempts fail. The frontend must stop its peer connection when it receives the terminal call event.

| Status | Meaning | Who can trigger | Next states |
| --- | --- | --- | --- |
| `CALLING` | Outgoing call created | Authenticated caller in `POST /api/calls` | `ACCEPTED`, `REJECTED`, `MISSED` (timeout or service), `BUSY`, `COMPLETED` |
| `ACCEPTED` | Receiver accepted; active/connected for backend validation | Receiver in `/accept` | `COMPLETED` |
| `REJECTED` | Receiver rejected | Receiver in `/reject` | None |
| `MISSED` | Unanswered call timed out | Scheduled timeout or caller through service method | None |
| `BUSY` | Receiver reports busy | Receiver in `/busy` | None |
| `COMPLETED` | A participant ended the call, or an accepted call timed out | Either participant in `/end` or scheduled timeout | None |

Invalid transitions return HTTP 409 with `Invalid call state transition`. Attempting active-call validation on a non-`ACCEPTED` state returns HTTP 409 with `Call is not active`.

## REST errors

Errors use the shared `ApiResponse` format. These are errors explicitly mapped by the call/AI code and shared security handling.

| HTTP status | Message | Condition |
| --- | --- | --- |
| 400 | `Invalid call ID` | Nonpositive ID passed to active-call validation |
| 400 | `Image is required` | Empty image, missing image content type, or non-image content type reaches the AI service |
| 400 | `Validation failed` | Invalid positive path ID or invalid JSON request field |
| 401 | `Authentication required or invalid bearer token` | Missing, invalid, expired, or revoked bearer authentication |
| 403 | `Call access denied` | User is not a participant or lacks permission for that transition |
| 403 | `Active Signify Premium is required for both call participants` | Caller or receiver has no current Premium entitlement |
| 403 | `Direct interaction is unavailable` | A block prevents creating the call |
| 404 | `Call not found` | Requested call ID does not exist |
| 404 | `Conversation not found` | Conversation for call creation does not exist |
| 409 | `Invalid call state transition` | Requested lifecycle transition is not allowed |
| 409 | `Call is not active` | Call exists and user is a participant, but status is not `ACCEPTED` |
| 409 | `Messages can only be sent in a PRIVATE conversation with exactly two active participants` | Call creation uses a non-private or invalid participant set |
| 502 | `AI service unavailable` | AI connection failure or unexpected upstream HTTP error |
| 504 | `AI service timed out` | AI connection/read timeout |
| 500 | `Internal server error` | Unhandled server error; details are not returned to the client |

Unexpected failures while reading the uploaded file use the shared generic 500 handling. The upload size limit follows the configured multipart settings (`storage.image.max-size`, 5 MiB by default; request limit 6 MiB by default).

## WebSocket and STOMP

### Connection

- WebSocket endpoint: `/ws/chat`
- SockJS: not enabled by `ChatWebSocketConfig`
- Application prefix: `/app`
- User prefix: `/user`
- Broker prefix: `/queue`
- Authentication: STOMP `CONNECT` native header `Authorization: Bearer <JWT>`

### Subscribe to a call's predictions

Both caller and receiver subscribe using their own authenticated STOMP session:

```text
SUBSCRIBE
id:call-42
destination:/user/queue/calls/42
ack:auto

\0
```

`ChatStompAuthorization` permits this destination only after `VideoCallService.validateActiveCall` confirms that the authenticated user belongs to call 42 and the status is `ACCEPTED`. Clients cannot subscribe directly to `/queue/calls/42`.

### Send / trigger

There is no STOMP `SEND` destination for call lifecycle or AI prediction. The prediction is triggered by the multipart REST endpoint above. WebRTC signaling uses the separate `SEND /app/calls/{callId}/webrtc` route below; chat typing and presence retain their own mappings.

### Event received

Each participant receives an `AiPredictionEvent` only for an accepted letter and an `AiTextUpdateEvent` for each accepted letter or text control on `/user/queue/calls/{callId}`. There is no global topic broadcast.

`curl` cannot subscribe to STOMP. Use a STOMP-over-WebSocket client to connect and subscribe; use the `curl` example above to trigger a prediction.

## WebRTC signaling

An authenticated participant in an `ACCEPTED` call can subscribe to `/user/queue/calls/{callId}/webrtc` and send to `/app/calls/{callId}/webrtc`. `WebRtcSignalingController` relays `WEBRTC_READY`, `WEBRTC_OFFER`, `WEBRTC_ANSWER` and `WEBRTC_ICE_CANDIDATE` only to the other participant. The request has exactly `type` and `payload`; SDP uses `{ "type": "offer|answer", "sdp": "..." }`, ICE uses `candidate`, `sdpMid`, `sdpMLineIndex` and optional `usernameFragment`. The receiver sends `READY`/`ANSWER`; the caller sends `OFFER`; either can send ICE. The server validates the call and current Premium entitlement before forwarding. Browser peer connection setup and P2P media transport remain client responsibilities.

Call history endpoint: **NOT IMPLEMENTED**.

## Current implementation status

`IMPLEMENTED — NOT RUNTIME VERIFIED` means the source and unit tests exist, but the feature has not been exercised against a running Spring Boot server, broker, or Python AI process in this verification.

| Feature | Status | Evidence |
| --- | --- | --- |
| Create call | IMPLEMENTED — NOT RUNTIME VERIFIED | `VideoCallController.create`, `VideoCallServiceTest` |
| Accept call | IMPLEMENTED — NOT RUNTIME VERIFIED | `VideoCallController.accept`, `VideoCallServiceTest` |
| Reject call | IMPLEMENTED — NOT RUNTIME VERIFIED | `VideoCallController.reject`, `VideoCallServiceTest` |
| End call | IMPLEMENTED — NOT RUNTIME VERIFIED | `VideoCallController.end`, `VideoCallServiceTest` |
| Call history | NOT IMPLEMENTED | No history route/service found |
| WebSocket call subscription | IMPLEMENTED — NOT RUNTIME VERIFIED | `ChatWebSocketConfig`, `ChatStompAuthorization`, `VideoCallStompAuthorizationTest` |
| AI prediction event | IMPLEMENTED — NOT RUNTIME VERIFIED | `AiPredictionController`, `AiPredictionService`, `VideoCallAiPredictionTest` |
| SDP offer | IMPLEMENTED — NOT RUNTIME VERIFIED | `WebRtcSignalingController`, `WebRtcSignalingService` |
| SDP answer | IMPLEMENTED — NOT RUNTIME VERIFIED | `WebRtcSignalingController`, `WebRtcSignalingService` |
| ICE candidate | IMPLEMENTED — NOT RUNTIME VERIFIED | `WebRtcSignalingController`, `WebRtcSignalingService` |
| Authentication | IMPLEMENTED — NOT RUNTIME VERIFIED | `SecurityConfig`, `JwtAuthenticationFilter`, STOMP interceptor |
| Call authorization | IMPLEMENTED — NOT RUNTIME VERIFIED | `VideoCallService.validateActiveCall`, `VideoCallSubscriptionAuthorizationTest` |
| Runtime test | NOT RUNTIME VERIFIED | Focused tests mock AI and messaging; no live service/broker test found |

## Section 4B scope

Section 4B accepts one uploaded image for an authenticated participant in an accepted call, invokes the configured external AI client, and returns and privately publishes one `AI_SIGN_PREDICTION` event to the caller and receiver. It does not persist transcripts.

Frontend camera capture/subscription UI and browser WebRTC peer connection are outside this backend. The external AI model runs in the separate Python service; this backend documentation does not claim that model recognition or end-to-end browser operation was runtime verified.
