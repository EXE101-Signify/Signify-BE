package fptu.exe202.signify.signifybe.features.chat.api;

import fptu.exe202.signify.signifybe.features.auth.domain.CurrentUser;
import fptu.exe202.signify.signifybe.features.chat.application.TypingService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class ChatTypingController {
    private final TypingService typing;

    @MessageMapping("/conversations/{conversationId}/typing")
    public void send(@DestinationVariable long conversationId, @Payload TypingRequest request,
                     Principal principal) {
        typing.send(conversationId, principal instanceof CurrentUser user ? user : null, request);
    }
}
