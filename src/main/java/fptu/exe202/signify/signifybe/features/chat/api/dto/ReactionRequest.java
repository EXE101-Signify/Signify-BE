package fptu.exe202.signify.signifybe.features.chat.api.dto;

import jakarta.validation.constraints.NotBlank;

public record ReactionRequest(@NotBlank String reaction) { }
