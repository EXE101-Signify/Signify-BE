package fptu.exe202.signify.signifybe.storage.infrastructure;

import fptu.exe202.signify.signifybe.storage.application.ImageProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({StorageProperties.class, ImageProperties.class})
public class StorageConfiguration {
}
