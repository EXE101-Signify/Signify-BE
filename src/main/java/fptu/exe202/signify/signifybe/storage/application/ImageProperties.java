package fptu.exe202.signify.signifybe.storage.application;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("storage.image")
public record ImageProperties(@DefaultValue("5242880") @Positive long maxSize) {
}
