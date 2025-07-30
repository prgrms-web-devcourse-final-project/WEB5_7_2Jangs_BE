package io.ejangs.docsa.domain.auth.app;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Service
@RequiredArgsConstructor
public class MailService {

    @Value("${spring.mail.username}")
    private String senderEmail;

    private final JavaMailSender javaMailSender;
    private final TemplateEngine templateEngine;

    public void sendCodeMail(String to, String code) throws MessagingException {
        MimeMessage message = createCodeMail(to, code);
        javaMailSender.send(message);
    }

    private MimeMessage createCodeMail(String to, String code) throws MessagingException {
        MimeMessage message = javaMailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        Context context = new Context();
        context.setVariable("code", code);
        String htmlContent = templateEngine.process("code-mail", context);

        helper.setFrom(senderEmail);
        helper.setTo(to);
        helper.setSubject("Docsa 이메일 인증");
        helper.setText(htmlContent, true);

        Resource resource = new ClassPathResource("static/img/docsa_logo.png");
        helper.addInline("docsa-logo", resource);

        return message;
    }
}
