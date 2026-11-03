package fptu.exe202.signify.signifybe.features.payment.api;

import fptu.exe202.signify.apiresponse.response.ApiResponse;
import fptu.exe202.signify.signifybe.features.auth.domain.CurrentUser;
import fptu.exe202.signify.signifybe.features.auth.domain.exception.AuthException;
import fptu.exe202.signify.signifybe.features.payment.api.dto.SubscriptionPlanResponse;
import fptu.exe202.signify.signifybe.features.payment.api.dto.SubscriptionResponse;
import fptu.exe202.signify.signifybe.features.payment.application.SubscriptionService;
import fptu.exe202.signify.signifybe.features.payment.infrastructure.persistence.JpaSubscriptionPlanRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.time.Clock;
import java.util.List;

@RestController
@RequestMapping("/api/subscription")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SubscriptionController {

    JpaSubscriptionPlanRepository plans;

    SubscriptionService subscriptions;

    Clock clock;

    @GetMapping("/plans")
    public ResponseEntity<ApiResponse<List<SubscriptionPlanResponse>>> plans() {
        var result = plans.findByPlanCodeAndActiveTrue("PREMIUM_MONTHLY")
                .stream().map(SubscriptionPlanResponse::from).toList();
        return ok(result);
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<SubscriptionResponse>> me(@AuthenticationPrincipal CurrentUser user) {
        long userId = userId(user);
        long now = clock.millis();
        var current = subscriptions.getCurrentSubscription(userId);
        SubscriptionResponse result = current.map(s -> SubscriptionResponse.from(s, now))
                .orElseGet(() -> subscriptions.getSubscriptionHistory(userId).stream()
                        .filter(s -> "PENDING".equals(s.getStatus()) && s.getActivatedAt() != null
                                && s.getSubscriptionStartAt() > now)
                        .min(java.util.Comparator.comparingLong(s -> s.getSubscriptionStartAt()))
                        .map(s -> SubscriptionResponse.from(s, now))
                        .orElseGet(SubscriptionResponse::empty));
        if (result.planCode() != null) result = result.withSubscriptionEndAt(
                subscriptions.getSubscriptionExpiration(userId).orElse(result.subscriptionEndAt()));
        return ok(result);
    }

    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<SubscriptionResponse>>> history(@AuthenticationPrincipal CurrentUser user) {
        long now = clock.millis();
        var result = subscriptions.getSubscriptionHistory(userId(user)).stream()
                .map(s -> SubscriptionResponse.from(s, now)).toList();
        return ok(result);
    }

    private static long userId(CurrentUser user) {
        if (user == null) throw AuthException.invalidAccessToken();
        return user.userId();
    }

    private static <T> ResponseEntity<ApiResponse<T>> ok(T data) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(data));
    }
}
