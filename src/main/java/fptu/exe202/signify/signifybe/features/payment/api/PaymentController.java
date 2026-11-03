package fptu.exe202.signify.signifybe.features.payment.api;

import fptu.exe202.signify.apiresponse.response.ApiResponse;
import fptu.exe202.signify.signifybe.features.auth.domain.CurrentUser;
import fptu.exe202.signify.signifybe.features.payment.api.dto.PaymentOrderView;
import fptu.exe202.signify.signifybe.features.payment.application.PaymentLinkService;
import fptu.exe202.signify.signifybe.features.payment.application.PaymentOrderService;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/payments")
public class PaymentController {
    private final PaymentOrderService orders;
    private final PaymentLinkService links;

    public PaymentController(PaymentOrderService orders, PaymentLinkService links) {
        this.orders = orders;
        this.links = links;
    }

    @PostMapping("/orders")
    public ResponseEntity<ApiResponse<PaymentOrderView>> create(@AuthenticationPrincipal CurrentUser user) {
        var order = orders.createSubscriptionOrder(user);
        var link = links.createPaymentLink(order.id(), user);
        return ok(PaymentOrderView.from(order).withCheckout(link));
    }

    @GetMapping("/orders/{paymentOrderId}")
    public ResponseEntity<ApiResponse<PaymentOrderView>> get(@AuthenticationPrincipal CurrentUser user,
            @PathVariable @Positive long paymentOrderId) {
        return ok(PaymentOrderView.from(orders.getPaymentOrderById(paymentOrderId, user)));
    }

    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<PaymentOrderView>>> history(
            @AuthenticationPrincipal CurrentUser user,
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "20") @Positive int size) {
        return ok(orders.getUserPaymentOrders(user, page, size).stream().map(PaymentOrderView::from).toList());
    }

    private static <T> ResponseEntity<ApiResponse<T>> ok(T data) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(data));
    }
}
