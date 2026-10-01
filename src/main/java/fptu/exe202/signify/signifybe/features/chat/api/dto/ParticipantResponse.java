package fptu.exe202.signify.signifybe.features.chat.api.dto;

public record ParticipantResponse(
        long userId,
        String fullName,
        String avatar,
        Long joinedAt
) {
}
