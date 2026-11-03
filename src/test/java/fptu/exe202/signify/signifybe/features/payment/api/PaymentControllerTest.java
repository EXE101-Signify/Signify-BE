package fptu.exe202.signify.signifybe.features.payment.api;

import fptu.exe202.signify.signifybe.features.auth.domain.CurrentUser;
import fptu.exe202.signify.signifybe.features.auth.domain.Role;
import fptu.exe202.signify.signifybe.features.payment.api.dto.PaymentLinkResponse;
import fptu.exe202.signify.signifybe.features.payment.api.dto.PaymentOrderResponse;
import fptu.exe202.signify.signifybe.features.payment.application.PaymentLinkService;
import fptu.exe202.signify.signifybe.features.payment.application.PaymentOrderService;
import fptu.exe202.signify.signifybe.features.payment.domain.PaymentOrderException;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentControllerTest {
    private final PaymentOrderService orders = mock(PaymentOrderService.class);
    private final PaymentLinkService links = mock(PaymentLinkService.class);
    private final PaymentController controller = new PaymentController(orders, links);
    private final CurrentUser user = new CurrentUser(7, 1, Role.USER);

    @Test void createReturnsFrontendCheckoutFieldsForAuthenticatedUser() {
        var order = new PaymentOrderResponse(4, 123, "PREMIUM_MONTHLY", "NEW_SUBSCRIPTION",
                "PENDING", 49_000, "VND", null, null, null, 1_000);
        when(orders.createSubscriptionOrder(user)).thenReturn(order);
        when(links.createPaymentLink(4, user)).thenReturn(new PaymentLinkResponse(
                123, 49_000, "VND", "link-1", "https://pay.example/checkout", "PENDING", 2_000L));

        var response = controller.create(user).getBody().getData();

        assertEquals(4, response.paymentOrderId());
        assertEquals(49_000, response.totalAmount());
        assertEquals("PENDING", response.orderStatus());
        assertEquals("https://pay.example/checkout", response.checkoutUrl());
        verify(orders).createSubscriptionOrder(user);
        verify(links).createPaymentLink(4, user);
    }

    @Test void readEndpointsPassOnlyAuthenticatedPrincipalToOwnerChecks() {
        var order = new PaymentOrderResponse(4, 123, "PREMIUM_MONTHLY", "RENEWAL",
                "PAID", 49_000, "VND", null, null, 1_000L, 500);
        when(orders.getPaymentOrderById(4, user)).thenReturn(order);
        when(orders.getUserPaymentOrders(user, 0, 20)).thenReturn(List.of(order));

        assertEquals(4, controller.get(user, 4).getBody().getData().paymentOrderId());
        assertEquals(1, controller.history(user, 0, 20).getBody().getData().size());
        verify(orders).getPaymentOrderById(4, user);
        verify(orders).getUserPaymentOrders(user, 0, 20);
    }

    @Test void ownershipFailurePropagatesThroughExistingExceptionSystem() {
        when(orders.getPaymentOrderById(4, user)).thenThrow(PaymentOrderException.forbidden());
        assertThrows(PaymentOrderException.class, () -> controller.get(user, 4));
        verifyNoInteractions(links);
    }
}
