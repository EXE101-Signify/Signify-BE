package fptu.exe202.signify.signifybe.features.payment.api;

import fptu.exe202.signify.signifybe.features.payment.application.PaymentWebhookService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PayOSWebhookController {

    PaymentWebhookService service;

    @PostMapping("/api/payment/payos/webhook")
    public ResponseEntity<Map<String, Object>> receive(@RequestBody Map<String, Object> payload) {
        service.handle(payload);
        return ResponseEntity.ok(Map.of("success", true));
    }
}
