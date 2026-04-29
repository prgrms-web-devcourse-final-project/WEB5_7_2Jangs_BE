package io.ejangs.docsa;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@OpenAPIDefinition(
        info = @Info(
                title = "Docsa API",
                version = "v1",
                description = "Docsa의 백엔드 API 명세입니다."
        )
)
@EnableAsync
@EnableScheduling
@SpringBootApplication
public class DocsaApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocsaApplication.class, args);
    }

}
