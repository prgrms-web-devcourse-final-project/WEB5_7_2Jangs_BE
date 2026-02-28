package io.ejangs.docsa.global.config;// package io.ejangs.docsa.global.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Component
@ConfigurationProperties(prefix = "app.cors")
public class CorsProps {
    private List<String> allowedOrigin = new ArrayList<>();

}