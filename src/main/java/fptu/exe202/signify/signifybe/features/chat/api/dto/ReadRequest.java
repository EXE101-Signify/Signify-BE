package fptu.exe202.signify.signifybe.features.chat.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ReadRequest(@NotNull @Positive Long lastReadMessageId) { }
