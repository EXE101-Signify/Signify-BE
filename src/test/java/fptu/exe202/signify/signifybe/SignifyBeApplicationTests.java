package fptu.exe202.signify.signifybe;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_APPLICATION_INTEGRATION_TESTS", matches = "true")
class SignifyBeApplicationTests {

    @Test
    void contextLoads() {
    }

}
