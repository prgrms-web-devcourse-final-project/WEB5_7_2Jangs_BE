package io.ejangs.docsa.domain.auth.app;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MailServiceTest {

    @Mock
    private JavaMailSender javaMailSender;

    @Mock
    private MimeMessage mimeMessage;

    @InjectMocks
    private MailService mailService;

    @BeforeEach
    void setUp() {
        // @Value 어노테이션 필드 수동 설정
        ReflectionTestUtils.setField(mailService, "senderEmail", "sender@example.com");
    }

    @Test
    @DisplayName("인증코드 메일 전송 성공")
    void sendSignupAuthCode_Success() throws MessagingException {
        // given
        String recipientEmail = "test@example.com";
        String authCode = "123456";

        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);

        // when
        mailService.sendSignupAuthCode(recipientEmail, authCode);

        // then
        verify(javaMailSender).createMimeMessage();
        verify(javaMailSender).send(mimeMessage);

        // MimeMessage 설정 검증
        verify(mimeMessage).setFrom("sender@example.com");
        verify(mimeMessage).setRecipients(MimeMessage.RecipientType.TO, recipientEmail);
        verify(mimeMessage).setSubject("Docsa 이메일 인증");
        verify(mimeMessage).setText(argThat(body ->
                body.contains(authCode) && body.contains("인증 번호")
        ), eq("UTF-8"), eq("html"));
    }

    @Test
    @DisplayName("MimeMessage 생성 실패 시 예외 발생")
    void sendSignupAuthCode_MimeMessageCreationFailed() throws MessagingException {
        // given
        String recipientEmail = "test@example.com";
        String authCode = "123456";

        when(javaMailSender.createMimeMessage()).thenThrow(new RuntimeException("Message creation failed"));

        // when & then
        assertThatThrownBy(() -> mailService.sendSignupAuthCode(recipientEmail, authCode))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Message creation failed");

        verify(javaMailSender).createMimeMessage();
        verify(javaMailSender, never()).send((MimeMessage) any());
    }

    @Test
    @DisplayName("메일 전송 실패 시 예외 발생")
    void sendSignupAuthCode_SendingFailed() throws MessagingException {
        // given
        String recipientEmail = "test@example.com";
        String authCode = "123456";

        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new MailSendException("SMTP server error"))
                .when(javaMailSender).send(mimeMessage);

        // when & then
        assertThatThrownBy(() -> mailService.sendSignupAuthCode(recipientEmail, authCode))
                .isInstanceOf(MailSendException.class)
                .hasMessage("SMTP server error");

        verify(javaMailSender).createMimeMessage();
        verify(javaMailSender).send(mimeMessage);
    }

    @Test
    @DisplayName("메일 내용에 인증코드가 포함되어 있는지 확인")
    void createSignupCodeMail_ContainsAuthCode() throws MessagingException {
        // given
        String recipientEmail = "test@example.com";
        String authCode = "123456";

        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);

        // when
        mailService.sendSignupAuthCode(recipientEmail, authCode);

        // then
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(mimeMessage).setText(bodyCaptor.capture(), eq("UTF-8"), eq("html"));

        String capturedBody = bodyCaptor.getValue();
        assertThat(capturedBody)
                .contains("인증 번호")
                .contains(authCode)
                .contains("감사합니다");
    }
}