package io.ejangs.docsa.domain.auth.app;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.ejangs.docsa.domain.auth.dto.request.CodeCheckRequest;
import io.ejangs.docsa.domain.auth.dto.request.PwdResetCodeRequest;
import io.ejangs.docsa.domain.auth.dto.request.SignupCodeRequest;
import io.ejangs.docsa.domain.auth.model.CodeType;
import io.ejangs.docsa.domain.auth.util.AuthCodeGenerator;
import io.ejangs.docsa.domain.user.dao.mysql.UserRepository;
import io.ejangs.docsa.domain.auth.dto.response.CodeCheckResponse;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.AuthErrorCode;
import io.ejangs.docsa.global.exception.errorcode.UserErrorCode;
import jakarta.mail.MessagingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private MailService mailService;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache signupCodeCache;

    @Mock
    private Cache passCodeCache;

    @Mock
    private Cache pwdResetCodeCache;

    @Mock
    private AuthCodeGenerator authCodeGenerator;

    @InjectMocks
    private AuthService authService;

    private SignupCodeRequest signupRequest;
    private PwdResetCodeRequest pwdResetRequest;

    @BeforeEach
    void setUp() {
        signupRequest = new SignupCodeRequest("test@example.com");
        pwdResetRequest = new PwdResetCodeRequest("test@example.com");

        ReflectionTestUtils.setField(authService, "signupCacheName", "signupCodeCache");
        ReflectionTestUtils.setField(authService, "passcodeCacheName", "passCodeCache");
        ReflectionTestUtils.setField(authService, "pwdResetCacheName", "pwdResetCodeCache");

        lenient().when(cacheManager.getCache("signupCodeCache")).thenReturn(signupCodeCache);
        lenient().when(cacheManager.getCache("passcodeCache")).thenReturn(passCodeCache);
        lenient().when(cacheManager.getCache("pwdResetCodeCache")).thenReturn(pwdResetCodeCache);
    }

    @Test
    @DisplayName("회원가입 - 정상적인 인증코드 전송")
    void sendSignupCode_Success() throws MessagingException {
        // given
        when(userRepository.existsByEmail(signupRequest.email())).thenReturn(false);
        when(authCodeGenerator.generateVerifyCode()).thenReturn("ABC123");

        // when
        authService.sendSignupCode(signupRequest);

        // then
        verify(signupCodeCache).put(eq(signupRequest.email()), eq("ABC123"));
        verify(mailService).sendCodeMail(eq(signupRequest.email()), eq("ABC123"));
    }

    @Test
    @DisplayName("회원가입 - 이미 가입된 이메일로 요청 시 예외 발생")
    void sendSignupCode_DuplicateEmail() throws MessagingException {
        // given
        when(userRepository.existsByEmail(signupRequest.email())).thenReturn(true);

        // when & then
        assertThatThrownBy(() -> authService.sendSignupCode(signupRequest))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.DUPLICATE_EMAIL);

        verify(userRepository).existsByEmail(signupRequest.email());
        verify(signupCodeCache, never()).put(any(), any());
        verify(mailService, never()).sendCodeMail(any(), any());
    }

    @Test
    @DisplayName("메일 전송 실패 시 예외 발생")
    void sendSignupCode_MailSendingFailed() throws MessagingException {
        // given
        when(userRepository.existsByEmail(signupRequest.email())).thenReturn(false);
        when(authCodeGenerator.generateVerifyCode()).thenReturn("MAIL01");

        doThrow(new MessagingException("Mail server error"))
                .when(mailService).sendCodeMail(signupRequest.email(), "MAIL01");

        // when & then
        assertThatThrownBy(() -> authService.sendSignupCode(signupRequest))
                .isInstanceOf(MessagingException.class)
                .hasMessage("Mail server error");

        verify(signupCodeCache).put(signupRequest.email(), "MAIL01");
        verify(mailService).sendCodeMail(signupRequest.email(), "MAIL01");
    }

    @Test
    @DisplayName("정상적인 인증코드 검증")
    void checkCode_Success() {
        // given
        String email = signupRequest.email();
        String code = "ABC123";
        String passCode = "PASS5678";

        when(signupCodeCache.get(email)).thenReturn(() -> code);
        when(cacheManager.getCache("passCodeCache")).thenReturn(passCodeCache);
        when(authCodeGenerator.generatePassCode()).thenReturn(passCode);

        CodeCheckRequest checkRequest = new CodeCheckRequest(email, code, CodeType.SIGNUP);

        // when
        CodeCheckResponse response = authService.checkCode(checkRequest);

        // then
        assertThat(response).isNotNull();
        assertThat(response.passCode()).isEqualTo(passCode);
        verify(passCodeCache).put(email, passCode);
        verify(signupCodeCache).evict(email);
    }

    @Test
    @DisplayName("인증코드 만료된 경우 예외 발생")
    void checkCode_CodeExpired() {
        // given
        String email = signupRequest.email();
        when(signupCodeCache.get(email)).thenReturn(null); // 캐시에 없음

        CodeCheckRequest checkRequest = new CodeCheckRequest(email, "ANYCODE", CodeType.SIGNUP);

        // when & then
        assertThatThrownBy(() -> authService.checkCode(checkRequest))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.EXPIRED_CODE);

        verify(signupCodeCache, never()).evict(any());
    }

    @Test
    @DisplayName("인증코드 불일치 시 예외 발생")
    void checkCode_InvalidCode() {
        // given
        String email = signupRequest.email();
        String realCode = "REAL12";
        String wrongCode = "WRONG9";

        when(signupCodeCache.get(email)).thenReturn(() -> realCode);

        CodeCheckRequest checkRequest = new CodeCheckRequest(email, wrongCode, CodeType.SIGNUP);

        // when & then
        assertThatThrownBy(() -> authService.checkCode(checkRequest))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.INVALID_CODE);

        verify(signupCodeCache, never()).evict(any());
    }

    @Test
    @DisplayName("비밀번호 변경 - 정상적인 인증코드 전송")
    void sendResetPwdCode_Success() throws MessagingException {
        // given
        when(userRepository.existsByEmail(pwdResetRequest.email())).thenReturn(true);
        when(authCodeGenerator.generateVerifyCode()).thenReturn("RESET1");

        // when
        authService.sendResetPwdCode(pwdResetRequest);

        // then
        verify(pwdResetCodeCache).put(eq(pwdResetRequest.email()), eq("RESET1"));
        verify(mailService).sendCodeMail(eq(pwdResetRequest.email()), eq("RESET1"));
    }

    @Test
    @DisplayName("비밀번호 변경 - 존재하지 않는 사용자인 경우 예외 발생")
    void sendResetPwdCode_UserNotFound() throws MessagingException {
        // given
        when(userRepository.existsByEmail(pwdResetRequest.email())).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> authService.sendResetPwdCode(pwdResetRequest))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", UserErrorCode.USER_NOT_FOUND);

        verify(pwdResetCodeCache, never()).put(any(), any());
        verify(mailService, never()).sendCodeMail(any(), any());
    }

    @Test
    @DisplayName("회원가입 - 이미 존재하는 이메일로 SIGNUP 타입 인증 요청 시 예외 발생")
    void checkCode_SignupWithExistingEmail() {
        // given
        String email = "existing@example.com";
        when(userRepository.existsByEmail(email)).thenReturn(true);

        CodeCheckRequest checkRequest = new CodeCheckRequest(email, "CODE123", CodeType.SIGNUP);

        // when & then
        assertThatThrownBy(() -> authService.checkCode(checkRequest))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.ALREADY_REGISTERED_USER);
    }

    @Test
    @DisplayName("비밀번호 재설정 - 존재하지 않는 사용자로 요청 시 예외 발생")
    void checkCode_ResetPasswordWithUnknownEmail() {
        // given
        String email = "unknown@example.com";
        when(userRepository.existsByEmail(email)).thenReturn(false);

        CodeCheckRequest checkRequest = new CodeCheckRequest(email, "CODE321",
                CodeType.RESET_PASSWORD);

        // when & then
        assertThatThrownBy(() -> authService.checkCode(checkRequest))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.USER_NOT_FOUND_FOR_RESET);
    }

}