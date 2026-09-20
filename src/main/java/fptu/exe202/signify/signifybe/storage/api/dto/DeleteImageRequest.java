package fptu.exe202.signify.signifybe.storage.api.dto;

import jakarta.validation.constraints.NotBlank;

public record DeleteImageRequest(@NotBlank String key) {
}
