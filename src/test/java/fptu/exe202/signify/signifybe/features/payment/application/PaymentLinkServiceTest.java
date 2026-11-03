package fptu.exe202.signify.signifybe.features.payment.application;

import fptu.exe202.signify.signifybe.features.auth.domain.CurrentUser;
import fptu.exe202.signify.signifybe.features.auth.domain.Role;
import fptu.exe202.signify.signifybe.features.payment.domain.PaymentOrder;
import fptu.exe202.signify.signifybe.features.payment.domain.PaymentOrderException;
import fptu.exe202.signify.signifybe.features.payment.domain.SubscriptionPlan;
import fptu.exe202.signify.signifybe.features.payment.infrastructure.persistence.JpaPaymentOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentLinkServiceTest {
    @Mock JpaPaymentOrderRepository orders;
    @Mock PaymentGateway gateway;
    PaymentLinkService service;
    CurrentUser owner = new CurrentUser(7, 1, Role.USER);
    PaymentOrder order;

    @BeforeEach void setUp() {
        SubscriptionPlan plan = mock(SubscriptionPlan.class);
        lenient().when(plan.getMonthlyPriceAmount()).thenReturn(49_000L);
        lenient().when(plan.getCurrencyCode()).thenReturn("VND");
        order = new PaymentOrder(123_456_789_012L, 7, plan, "NEW_SUBSCRIPTION", 1_000);
        service = new PaymentLinkService(orders, gateway, Clock.fixed(Instant.ofEpochMilli(2_000), ZoneOffset.UTC));
    }

    @Test void createsAndPersistsVerifiedLink() {
        when(orders.findLockedById(4)).thenReturn(Optional.of(order));
        when(gateway.create(123_456_789_012L, 49_000L)).thenReturn(
                new PaymentGateway.GatewayPaymentLink(123_456_789_012L, 49_000L, "link-1", "https://pay.example/link-1", "PENDING", null));

        var result = service.createPaymentLink(4, owner);

        assertEquals("link-1", order.getProviderPaymentLinkId());
        assertEquals("https://pay.example/link-1", result.checkoutUrl());
        verify(gateway).get(123_456_789_012L);
    }

    @Test void rejectsMismatchedAmount() {
        when(orders.findLockedById(4)).thenReturn(Optional.of(order));
        when(gateway.create(123_456_789_012L, 49_000L)).thenReturn(
                new PaymentGateway.GatewayPaymentLink(123_456_789_012L, 1L, "link-1", "https://pay.example/link-1", "PENDING", null));

        assertThrows(PaymentGatewayException.class, () -> service.createPaymentLink(4, owner));
        assertNull(order.getProviderPaymentLinkId());
    }

    @Test void rejectsGatewayLinkWithoutCheckoutUrl() {
        when(orders.findLockedById(4L)).thenReturn(Optional.of(order));
        when(gateway.create(123_456_789_012L, 49_000L)).thenReturn(
                new PaymentGateway.GatewayPaymentLink(123_456_789_012L, 49_000L, "link-1", null, "PENDING", null));

        assertThrows(PaymentGatewayException.class, () -> service.createPaymentLink(4L, owner));
        assertNull(order.getProviderPaymentLinkId());
        assertNull(order.getCheckoutUrl());
    }

    @Test void existingRemoteLinkWithoutStoredCheckoutDoesNotCreateSecondPayOsLink() {
        when(orders.findLockedById(4L)).thenReturn(Optional.of(order));
        when(gateway.get(123_456_789_012L)).thenReturn(
                new PaymentGateway.GatewayPaymentLink(123_456_789_012L, 49_000L, "link-1", null, "PENDING", null));

        assertThrows(PaymentGatewayException.class, () -> service.createPaymentLink(4L, owner));
        verify(gateway, never()).create(anyLong(), anyLong());
        assertNull(order.getProviderPaymentLinkId());
    }

    @Test void retryUsesStoredLinkWithoutGatewayCall() {
        order.attachPaymentLink("link-1", "https://pay.example/link-1", null, 1_000);
        when(orders.findLockedById(4)).thenReturn(Optional.of(order));

        assertEquals("link-1", service.createPaymentLink(4, owner).paymentLinkId());
        verifyNoInteractions(gateway);
    }

    @Test void rejectsOtherUsersOrderBeforeGatewayCall() {
        when(orders.findLockedById(4)).thenReturn(Optional.of(order));
        assertThrows(PaymentOrderException.class,
                () -> service.createPaymentLink(4, new CurrentUser(8, 1, Role.USER)));
        verifyNoInteractions(gateway);
    }

    @Test void retrievalDoesNotMarkOrderPaid() {
        when(orders.findById(4L)).thenReturn(Optional.of(order));
        when(gateway.get(123_456_789_012L)).thenReturn(
                new PaymentGateway.GatewayPaymentLink(123_456_789_012L, 49_000L, "link-1", null, "PAID", null));

        assertEquals("PAID", service.getPaymentInformation(4, owner).gatewayStatus());
        assertEquals("PENDING", order.getStatus());
    }
}
