package io.ejangs.docsa.global.config;

import java.util.Properties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

@Configuration
public class MailConfig {

    private final MailProperties mailProperties;

    public MailConfig(MailProperties mailProperties) {
        this.mailProperties = mailProperties;
    }

    @Bean
    public JavaMailSender javaMailSender() {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(mailProperties.getHost());
        mailSender.setPort(mailProperties.getPort());
        mailSender.setUsername(mailProperties.getUsername());
        mailSender.setPassword(mailProperties.getPassword());
        mailSender.setDefaultEncoding("UTF-8");

        Properties props = new Properties();
        props.put("mail.smtp.auth", mailProperties.isSmtpAuth());
        props.put("mail.smtp.starttls.enable", mailProperties.isStarttlsEnable());
        props.put("mail.smtp.starttls.required", mailProperties.isStarttlsRequired());
        props.put("mail.smtp.connectiontimeout", mailProperties.getConnectionTimeout());
        props.put("mail.smtp.timeout", mailProperties.getTimeout());
        props.put("mail.smtp.writetimeout", mailProperties.getWriteTimeout());

        mailSender.setJavaMailProperties(props);

        return mailSender;
    }
}
