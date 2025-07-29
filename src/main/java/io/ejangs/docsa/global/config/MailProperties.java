package io.ejangs.docsa.global.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "spring.mail")
@Getter
@Setter
public class MailProperties {

    private String host;
    private int port;
    private String username;
    private String password;
    private boolean smtpAuth;
    private boolean starttlsEnable;
    private boolean starttlsRequired;
    private int connectionTimeout;
    private int timeout;
    private int writeTimeout;
}
