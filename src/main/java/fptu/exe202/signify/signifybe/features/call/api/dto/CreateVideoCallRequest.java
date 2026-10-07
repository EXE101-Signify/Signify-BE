package fptu.exe202.signify.signifybe.features.call.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateVideoCallRequest(@NotNull @Positive Long conversationId) { }
