package fptu.exe202.signify.signifybe.features.payment.infrastructure;

import fptu.exe202.signify.signifybe.features.payment.application.PaymentGatewayException;
import org.junit.jupiter.api.Test;
import vn.payos.PayOS;
import vn.payos.exception.ConnectionTimeoutException;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;
import vn.payos.service.blocking.v2.paymentRequests.PaymentRequestsService;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PayOSPaymentGatewayTest {
    @Test void sendsTrustedAmountCodeAndConfiguredUrlsToSdk() {
        PayOS sdk = mock(PayOS.class);
        PaymentRequestsService requests = mock(PaymentRequestsService.class);
        when(sdk.paymentRequests()).thenReturn(requests);
        CreatePaymentLinkResponse created = new CreatePaymentLinkResponse();
        created.setOrderCode(123L);
        created.setAmount(49_000L);
        created.setPaymentLinkId("link-1");
        created.setCheckoutUrl("https://pay.example/link-1");
        when(requests.create(any(CreatePaymentLinkRequest.class))).thenReturn(created);
        PayOSPaymentGateway gateway = new PayOSPaymentGateway(sdk, "https://app.example/return", "https://app.example/cancel");

        var result = gateway.create(123L, 49_000L);

        var request = org.mockito.ArgumentCaptor.forClass(CreatePaymentLinkRequest.class);
        verify(requests).create(request.capture());
        assertEquals(123L, request.getValue().getOrderCode());
        assertEquals(49_000L, request.getValue().getAmount());
        assertEquals("https://app.example/return", request.getValue().getReturnUrl());
        assertEquals("https://app.example/cancel", request.getValue().getCancelUrl());
        assertEquals("link-1", result.paymentLinkId());
    }

    @Test void mapsSdkTimeoutToGatewayTimeout() {
        PayOS sdk = mock(PayOS.class);
        PaymentRequestsService requests = mock(PaymentRequestsService.class);
        when(sdk.paymentRequests()).thenReturn(requests);
        when(requests.get(123L)).thenThrow(new ConnectionTimeoutException("timeout"));
        PayOSPaymentGateway gateway = new PayOSPaymentGateway(sdk, "https://app.example/return", "https://app.example/cancel");

        assertThrows(PaymentGatewayException.class, () -> gateway.get(123L));
    }

    @Test void rejectsSdkCreateResponseWithoutCheckoutUrl() {
        PayOS sdk = mock(PayOS.class);
        PaymentRequestsService requests = mock(PaymentRequestsService.class);
        when(sdk.paymentRequests()).thenReturn(requests);
        CreatePaymentLinkResponse incomplete = new CreatePaymentLinkResponse();
        incomplete.setOrderCode(123L);
        incomplete.setAmount(49_000L);
        incomplete.setPaymentLinkId("link-1");
        when(requests.create(any(CreatePaymentLinkRequest.class))).thenReturn(incomplete);

        PayOSPaymentGateway gateway = new PayOSPaymentGateway(sdk,
                "https://app.example/return", "https://app.example/cancel");

        assertThrows(PaymentGatewayException.class, () -> gateway.create(123L, 49_000L));
    }
}
