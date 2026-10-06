package fptu.exe202.signify.signifybe.features.call.api;

import tools.jackson.databind.JsonNode;
import fptu.exe202.signify.signifybe.features.auth.domain.CurrentUser;
import fptu.exe202.signify.signifybe.features.call.application.WebRtcSignalingService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class WebRtcSignalingController {
    private final WebRtcSignalingService signaling;

    public WebRtcSignalingController(WebRtcSignalingService signaling) {
        this.signaling = signaling;
    }

    @MessageMapping("/calls/{callId}/webrtc")
    public void relay(@DestinationVariable long callId, @Payload JsonNode request, Principal principal) {
        signaling.relay(callId, principal instanceof CurrentUser user ? user : null, request);
    }
}
