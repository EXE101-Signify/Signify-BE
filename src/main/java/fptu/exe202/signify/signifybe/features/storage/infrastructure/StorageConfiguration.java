package fptu.exe202.signify.signifybe.features.storage.infrastructure;

import fptu.exe202.signify.signifybe.features.storage.application.ImageProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({StorageProperties.class, ImageProperties.class})
public class StorageConfiguration {
}
