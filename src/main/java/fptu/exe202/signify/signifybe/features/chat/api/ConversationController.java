package fptu.exe202.signify.signifybe.features.chat.api;

import fptu.exe202.signify.apiresponse.response.ApiResponse;
import fptu.exe202.signify.signifybe.features.auth.domain.CurrentUser;
import fptu.exe202.signify.signifybe.features.chat.api.dto.ConversationListResponse;
import fptu.exe202.signify.signifybe.features.chat.api.dto.ConversationResponse;
import fptu.exe202.signify.signifybe.features.chat.api.dto.CreateConversationRequest;
import fptu.exe202.signify.signifybe.features.chat.api.dto.ParticipantResponse;
import fptu.exe202.signify.signifybe.features.chat.api.dto.MessageResponse;
import fptu.exe202.signify.signifybe.features.chat.api.dto.SendMessageRequest;
import fptu.exe202.signify.signifybe.features.chat.application.ConversationMembershipService;
import fptu.exe202.signify.signifybe.features.chat.application.ConversationService;
import fptu.exe202.signify.signifybe.features.chat.application.MessageService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping({"/api/conversations", "/api/v1/conversations"})
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@PreAuthorize("hasAnyRole('USER', 'ADMIN')")
public class ConversationController {

    ConversationService conversationService;
    ConversationMembershipService membershipService;
    MessageService messageService;

    @PostMapping
    public ApiResponse<ConversationResponse> createConversation(
            @AuthenticationPrincipal CurrentUser user,
            @Valid @RequestBody CreateConversationRequest request) {
        ConversationResponse response = conversationService.createConversation(user.userId(), request);
        return ApiResponse.success("Conversation created successfully", response);
    }

    @GetMapping
    public ApiResponse<List<ConversationListResponse>> getConversations(
            @AuthenticationPrincipal CurrentUser user) {
        return ApiResponse.success(conversationService.getConversations(user.userId()));
    }

    @GetMapping("/{conversationId}")
    public ApiResponse<ConversationResponse> getConversationDetail(
            @AuthenticationPrincipal CurrentUser user,
            @PathVariable long conversationId) {
        membershipService.validateActiveMembership(conversationId, user.userId());
        return ApiResponse.success(conversationService.getConversationDetail(conversationId));
    }

    @GetMapping("/{conversationId}/participants")
    public ApiResponse<List<ParticipantResponse>> getParticipants(
            @AuthenticationPrincipal CurrentUser user,
            @PathVariable long conversationId) {
        membershipService.validateActiveMembership(conversationId, user.userId());
        return ApiResponse.success(conversationService.getParticipants(conversationId));
    }

    @PostMapping("/{conversationId}/messages")
    public ApiResponse<MessageResponse> sendMessage(
            @AuthenticationPrincipal CurrentUser user,
            @PathVariable long conversationId,
            @Valid @RequestBody SendMessageRequest request) {
        return ApiResponse.success("Message sent successfully",
                messageService.sendMessage(conversationId, user.userId(), request));
    }
}
