package fptu.exe202.signify.signifybe.features.chat.api;

import fptu.exe202.signify.signifybe.features.auth.domain.CurrentUser;
import fptu.exe202.signify.signifybe.features.chat.application.PresenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class PresenceWebSocketListener {
    private final PresenceService presence;

    @EventListener
    public void connected(SessionConnectedEvent event) {
        if (event.getUser() instanceof CurrentUser user) {
            String sessionId = org.springframework.messaging.simp.SimpMessageHeaderAccessor
                    .getSessionId(event.getMessage().getHeaders());
            presence.connect(user.userId(), sessionId);
        }
    }

    @EventListener
    public void disconnected(SessionDisconnectEvent event) {
        if (event.getUser() instanceof CurrentUser user)
            presence.disconnect(user.userId(), event.getSessionId());
    }

    @MessageMapping("/presence/heartbeat")
    public void heartbeat(Principal principal, @Header("simpSessionId") String sessionId) {
        if (principal instanceof CurrentUser user) presence.heartbeat(user.userId(), sessionId);
    }
}
