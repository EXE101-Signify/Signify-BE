package fptu.exe202.signify.signifybe.features.ai.api;

import fptu.exe202.signify.signifybe.features.ai.application.CallSttService;
import fptu.exe202.signify.signifybe.features.auth.domain.CurrentUser;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import tools.jackson.databind.JsonNode;

import java.security.Principal;

@Controller
public class CallSttController {
    private final CallSttService stt;

    public CallSttController(CallSttService stt) { this.stt = stt; }

    @MessageMapping("/calls/{callId}/stt")
    public void relay(@DestinationVariable long callId, @Payload JsonNode message,
                      @Header("simpSessionId") String sessionId, Principal principal) {
        stt.handle(callId, principal instanceof CurrentUser user ? user : null, sessionId, message);
    }
}
