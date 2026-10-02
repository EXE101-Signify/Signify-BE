# Admin audit logs and notifications

All endpoints require `Authorization: Bearer <access-token>` and return the existing `ApiResponse` envelope.

## Admin audit logs

Admin profile updates, bans and unbans now create immutable audit rows in the same database transaction as the user change. A failed user change therefore cannot leave a successful audit record, and a failed audit write rolls back the user change.

The optional `reason` query parameter is accepted by all three mutation endpoints:

```text
PATCH  /api/admin/users/{userId}?reason=profile%20correction
DELETE /api/admin/users/{userId}?reason=policy%20violation
PATCH  /api/admin/users/{userId}/unban?reason=appeal%20accepted
```

Audit history is available only to admins:

```text
GET /api/admin/audit-logs
    ?adminId=1
    &action=BAN_USER
    &targetType=USER
    &targetId=42
    &fromTime=1760000000000
    &toTime=1769999999999
    &page=0
    &size=20
```

Every filter is optional. `action` is one of `UPDATE_USER`, `BAN_USER`, or `UNBAN_USER`. Results are ordered newest first. `metadata` is returned as JSON and contains safe before/after user fields; password hashes, sessions and tokens are never recorded.

## User notifications

| Method | Endpoint | Behavior |
| --- | --- | --- |
| GET | `/api/notifications?unreadOnly=false&page=0&size=20` | List only the authenticated user's notifications |
| GET | `/api/notifications/unread-count` | Return the unread count |
| PATCH | `/api/notifications/{notificationId}/read` | Mark one owned notification as read |
| PATCH | `/api/notifications/read-all` | Mark all owned notifications as read |

Notification IDs belonging to another user are deliberately returned as not found. Mark-read operations are idempotent. Supported types are `MISSED_CALL`, `SUBSCRIPTION_EXPIRING`, and `SUBSCRIPTION_EXPIRED`.

## Publishing events

Call and subscription modules should publish the supplied event records through Spring's `ApplicationEventPublisher` inside the transaction that commits the source state:

```java
events.publishEvent(new MissedCallEvent(callId, receiverId, callerId, callerDisplayName));
events.publishEvent(new SubscriptionExpiringEvent(subscriptionId, userId, expiresAt, daysRemaining));
events.publishEvent(new SubscriptionExpiredEvent(subscriptionId, userId, expiresAt));
```

`NotificationEventHandler` consumes these with Spring Modulith. The event publication table makes delivery recoverable, while `notifications.deduplication_key` prevents a retried event from creating a duplicate user notification.

The current repository does not yet contain call or subscription application services, so event producers and the subscription expiry scheduler must be connected when those modules are implemented.
