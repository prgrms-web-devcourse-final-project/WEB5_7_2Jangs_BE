package io.ejangs.docsa;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.test.context.ActiveProfiles;

@EnableAsync
@EnableScheduling
@SpringBootTest
@ActiveProfiles("test")
class DocsaApplicationTests {

    @Test
    void contextLoads() {
    }

}
