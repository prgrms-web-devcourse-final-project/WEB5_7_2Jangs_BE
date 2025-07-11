package io.ejangs.docsa.domain.auth.app;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MailService {

    @Value("${spring.mail.username}")
    private String senderEmail;

    private final JavaMailSender javaMailSender;

    public void sendSignupAuthCode(String to, String authCode) throws MessagingException {
        MimeMessage message = createSignupCodeMail(to, authCode);
        javaMailSender.send(message);
    }

    private MimeMessage createSignupCodeMail(String to, String authCode) throws MessagingException {
        MimeMessage message = javaMailSender.createMimeMessage();

        message.setFrom(senderEmail);
        message.setRecipients(MimeMessage.RecipientType.TO, to);
        message.setSubject("Docsa 이메일 인증");

        String body = """
            <h3>요청하신 인증 번호입니다.</h3>
            <h1>%s</h1>
            <h3>감사합니다.</h3>
            """.formatted(authCode);

        message.setText(body, "UTF-8", "html");

        return message;
    }
}
