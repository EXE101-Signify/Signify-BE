package fptu.exe202.signify.signifybe.features.payment.api;

import fptu.exe202.signify.signifybe.features.auth.domain.CurrentUser;
import fptu.exe202.signify.signifybe.features.auth.domain.Role;
import fptu.exe202.signify.signifybe.features.auth.domain.exception.AuthException;
import fptu.exe202.signify.signifybe.features.payment.application.SubscriptionService;
import fptu.exe202.signify.signifybe.features.payment.domain.SubscriptionPlan;
import fptu.exe202.signify.signifybe.features.payment.infrastructure.persistence.JpaSubscriptionPlanRepository;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SubscriptionControllerTest {
    private final JpaSubscriptionPlanRepository plans = mock(JpaSubscriptionPlanRepository.class);
    private final SubscriptionService subscriptions = mock(SubscriptionService.class);
    private final SubscriptionController controller = new SubscriptionController(plans, subscriptions,
            Clock.fixed(Instant.ofEpochMilli(1_000), ZoneOffset.UTC));

    @Test void publicPlanUsesTrustedDatabaseValues() {
        SubscriptionPlan plan = mock(SubscriptionPlan.class);
        when(plan.getPlanCode()).thenReturn("PREMIUM_MONTHLY");
        when(plan.getPlanName()).thenReturn("Signify Premium");
        when(plan.getMonthlyPriceAmount()).thenReturn(49_000L);
        when(plan.getCurrencyCode()).thenReturn("VND");
        when(plan.getDurationMonths()).thenReturn(1);
        when(plans.findByPlanCodeAndActiveTrue("PREMIUM_MONTHLY")).thenReturn(Optional.of(plan));

        var result = controller.plans().getBody().getData();
        assertEquals(1, result.size());
        assertEquals(49_000, result.get(0).monthlyPriceAmount());
    }

    @Test void meWithoutSubscriptionReturnsInactiveContract() {
        CurrentUser user = new CurrentUser(7, 1, Role.USER);
        when(subscriptions.getCurrentSubscription(7)).thenReturn(Optional.empty());
        when(subscriptions.getSubscriptionHistory(7)).thenReturn(java.util.List.of());
        var result = controller.me(user).getBody().getData();
        assertFalse(result.isPremiumActive());
        assertNull(result.subscriptionEndAt());
        verify(subscriptions).getCurrentSubscription(7);
    }

    @Test void missingPrincipalCannotReadSubscription() {
        assertThrows(AuthException.class, () -> controller.me(null));
        verifyNoInteractions(subscriptions);
    }
}
