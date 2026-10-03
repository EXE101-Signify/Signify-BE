package fptu.exe202.signify.signifybe.features.chat.api;

import fptu.exe202.signify.apiresponse.response.ApiResponse;
import fptu.exe202.signify.signifybe.features.auth.domain.CurrentUser;
import fptu.exe202.signify.signifybe.features.chat.api.dto.*;
import fptu.exe202.signify.signifybe.features.chat.application.*;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@PreAuthorize("hasAnyRole('USER', 'ADMIN')")
public class ConversationController {

    ConversationService conversationService;

    ConversationMembershipService membershipService;

    MessageService messageService;

    AttachmentService attachmentService;

    MessageHistoryService messageHistoryService;

    ReactionService reactions;

    @PostMapping
    public ApiResponse<ConversationResponse> createConversation(
            @AuthenticationPrincipal CurrentUser user,
            @Valid @RequestBody CreateConversationRequest request
    ) {
        ConversationResponse response = conversationService.createConversation(user.userId(), request);
        return ApiResponse.success("Conversation created successfully", response);
    }

    @GetMapping
    public ApiResponse<List<ConversationListResponse>> getConversations(
            @AuthenticationPrincipal CurrentUser user
    ) {
        return ApiResponse.success(conversationService.getConversations(user.userId()));
    }

    @GetMapping("/{conversationId}")
    public ApiResponse<ConversationResponse> getConversationDetail(
            @AuthenticationPrincipal CurrentUser user,
            @PathVariable long conversationId
    ) {
        membershipService.validateActiveMembership(conversationId, user.userId());
        return ApiResponse.success(conversationService.getConversationDetail(conversationId));
    }

    @GetMapping("/{conversationId}/participants")
    public ApiResponse<List<ParticipantResponse>> getParticipants(
            @AuthenticationPrincipal CurrentUser user,
            @PathVariable long conversationId
    ) {
        membershipService.validateActiveMembership(conversationId, user.userId());
        return ApiResponse.success(conversationService.getParticipants(conversationId));
    }

    @PostMapping("/{conversationId}/messages")
    public ApiResponse<MessageResponse> sendMessage(
            @AuthenticationPrincipal CurrentUser user,
            @PathVariable long conversationId,
            @Valid @RequestBody SendMessageRequest request
    ) {
        return ApiResponse.success("Message sent successfully",
                messageService.sendMessage(conversationId, user.userId(), request));
    }

    @GetMapping("/{conversationId}/messages")
    public ApiResponse<MessageHistoryResponse> getMessages(
            @AuthenticationPrincipal CurrentUser user, @PathVariable long conversationId,
            @RequestParam(required = false) Long before,
            @RequestParam(defaultValue = "30") int limit) {
        return ApiResponse.success(messageHistoryService.get(conversationId, user.userId(), before, limit));
    }

    @PostMapping(value = "/{conversationId}/messages/attachment", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<AttachmentMessageResponse> sendAttachment(
            @AuthenticationPrincipal CurrentUser user,
            @PathVariable long conversationId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "content", required = false) String content
    ) {
        return ApiResponse.success("Attachment sent successfully",
                messageService.sendAttachment(conversationId, user.userId(), content, file));
    }

    @GetMapping("/attachments/{attachmentId}")
    public ApiResponse<AttachmentResponse> getAttachment(
            @AuthenticationPrincipal CurrentUser user,

            @PathVariable long attachmentId
    ) {
        return ApiResponse.success(attachmentService.get(attachmentId, user.userId()));
    }

    @DeleteMapping("/{conversationId}/messages/{messageId}")
    public ApiResponse<Void> removeMessage(
            @AuthenticationPrincipal CurrentUser user,
            @PathVariable long conversationId,
            @PathVariable long messageId
    ) {
        messageService.removeMessage(conversationId, messageId, user.userId());
        return ApiResponse.success("Message removed successfully", null);
    }

    @PutMapping("/{conversationId}/messages/{messageId}")
    public ApiResponse<EditedMessageResponse> editMessage(
            @AuthenticationPrincipal CurrentUser user,
            @PathVariable long conversationId,
            @PathVariable long messageId,
            @Valid @RequestBody EditMessageRequest request
    ) {
        return ApiResponse.success("Message updated successfully",
                messageService.editMessage(conversationId, messageId, user.userId(), request.content()));
    }

    @DeleteMapping("/attachments/{attachmentId}")
    public ApiResponse<Void> removeAttachment(
            @AuthenticationPrincipal CurrentUser user,
            @PathVariable long attachmentId
    ) {
        attachmentService.remove(attachmentId, user.userId());
        return ApiResponse.success("Attachment removed successfully", null);
    }

    @PostMapping("/{conversationId}/messages/{messageId}/reactions")
    public ApiResponse<ReactionSummaryResponse> addOrChange(
            @AuthenticationPrincipal CurrentUser user, @PathVariable long conversationId,
            @PathVariable long messageId, @Valid @RequestBody ReactionRequest request) {
        return ApiResponse.success(reactions.addOrChange(conversationId, messageId, user.userId(), request.reaction()));
    }

    @DeleteMapping("/{conversationId}/messages/{messageId}/reactions/{reaction}")
    public ApiResponse<ReactionSummaryResponse> remove(
            @AuthenticationPrincipal CurrentUser user, @PathVariable long conversationId,
            @PathVariable long messageId, @PathVariable String reaction) {
        return ApiResponse.success(reactions.remove(conversationId, messageId, user.userId(), reaction));
    }

    @GetMapping("/{conversationId}/messages/{messageId}/reactions")
    public ApiResponse<ReactionSummaryResponse> get(
            @AuthenticationPrincipal CurrentUser user, @PathVariable long conversationId,
            @PathVariable long messageId) {
        return ApiResponse.success(reactions.get(conversationId, messageId, user.userId()));
    }
}
