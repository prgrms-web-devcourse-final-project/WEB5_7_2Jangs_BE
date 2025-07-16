package io.ejangs.docsa;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

@EnableRetry
@SpringBootApplication
public class DocsaApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocsaApplication.class, args);
    }

}
