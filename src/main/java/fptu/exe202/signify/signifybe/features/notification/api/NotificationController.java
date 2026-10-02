package fptu.exe202.signify.signifybe.features.notification.api;

import fptu.exe202.signify.apiresponse.response.ApiResponse;
import fptu.exe202.signify.signifybe.features.auth.domain.CurrentUser;
import fptu.exe202.signify.signifybe.features.notification.application.NotificationService;
import fptu.exe202.signify.signifybe.features.notification.domain.Notification;
import fptu.exe202.signify.signifybe.features.notification.domain.NotificationType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationService notifications;

    @GetMapping
    public ResponseEntity<ApiResponse<NotificationPage>> list(
            @AuthenticationPrincipal CurrentUser user,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(NotificationService.MAX_PAGE_SIZE) int size) {
        return ok(NotificationPage.of(notifications.list(user.userId(), unreadOnly, page, size)));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<UnreadCountResponse>> unreadCount(@AuthenticationPrincipal CurrentUser user) {
        return ok(new UnreadCountResponse(notifications.unreadCount(user.userId())));
    }

    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<ApiResponse<NotificationResponse>> markRead(
            @AuthenticationPrincipal CurrentUser user,
            @PathVariable @Positive long notificationId) {
        return ok(NotificationResponse.of(notifications.markRead(user.userId(), notificationId)));
    }

    @PatchMapping("/read-all")
    public ResponseEntity<ApiResponse<MarkAllReadResponse>> markAllRead(@AuthenticationPrincipal CurrentUser user) {
        return ok(new MarkAllReadResponse(notifications.markAllRead(user.userId())));
    }

    private <T> ResponseEntity<ApiResponse<T>> ok(T data) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(data));
    }

    public record NotificationResponse(long id, NotificationType type, String title, String content,
                                       String referenceType, Long referenceId, boolean read,
                                       long createdAt, Long readAt) {
        static NotificationResponse of(Notification notification) {
            return new NotificationResponse(notification.getId(), notification.getType(), notification.getTitle(),
                    notification.getContent(), notification.getReferenceType(), notification.getReferenceId(),
                    notification.isRead(), notification.getCreatedAt(), notification.getReadAt());
        }
    }

    public record NotificationPage(List<NotificationResponse> content, int page, int size,
                                   long totalElements, int totalPages) {
        static NotificationPage of(Page<Notification> page) {
            return new NotificationPage(page.getContent().stream().map(NotificationResponse::of).toList(),
                    page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
        }
    }

    public record UnreadCountResponse(long unreadCount) { }
    public record MarkAllReadResponse(int updatedCount) { }
}
