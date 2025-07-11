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
import io.ejangs.docsa.domain.auth.dto.request.SignupCodeRequest;
import io.ejangs.docsa.domain.auth.dto.response.CodeCheckResponse;
import io.ejangs.docsa.domain.user.dao.UserRepository;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.AuthErrorCode;
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

    @InjectMocks
    private AuthService authService;

    private SignupCodeRequest request;

    @BeforeEach
    void setUp() {
        request = new SignupCodeRequest("test@example.com");
        ReflectionTestUtils.setField(authService, "signupCacheName", "signupCodeCache");
        ReflectionTestUtils.setField(authService, "passcodeCacheName", "passCodeCache");
        lenient().when(cacheManager.getCache("signupCodeCache")).thenReturn(signupCodeCache);
        lenient().when(cacheManager.getCache("passcodeCache")).thenReturn(passCodeCache);
    }

    @Test
    @DisplayName("정상적인 인증코드 전송")
    void sendSignupCode_Success() throws MessagingException {
        // given
        when(userRepository.existsByEmail(request.email())).thenReturn(false);

        // when
        authService.sendSignupCode(request);

        // then
        verify(userRepository).existsByEmail(request.email());
        verify(signupCodeCache).put(eq(request.email()), any(String.class));
        verify(mailService).sendSignupAuthCode(eq(request.email()), any(String.class));
    }

    @Test
    @DisplayName("이미 가입된 이메일로 요청 시 예외 발생")
    void sendSignupCode_DuplicateEmail() throws MessagingException {
        // given
        when(userRepository.existsByEmail(request.email())).thenReturn(true);

        // when & then
        assertThatThrownBy(() -> authService.sendSignupCode(request))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.DUPLICATE_EMAIL);

        verify(userRepository).existsByEmail(request.email());
        verify(signupCodeCache, never()).put(any(), any());
        verify(mailService, never()).sendSignupAuthCode(any(), any());
    }

    @Test
    @DisplayName("메일 전송 실패 시 예외 발생")
    void sendSignupCode_MailSendingFailed() throws MessagingException {
        // given
        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        doThrow(new MessagingException("Mail server error"))
                .when(mailService).sendSignupAuthCode(any(), any());

        // when & then
        assertThatThrownBy(() -> authService.sendSignupCode(request))
                .isInstanceOf(MessagingException.class)
                .hasMessage("Mail server error");

        verify(signupCodeCache).put(eq(request.email()), any(String.class));
        verify(mailService).sendSignupAuthCode(eq(request.email()), any(String.class));
    }

    @Test
    @DisplayName("정상적인 인증코드 검증 성공")
    void checkCode_Success() {
        // given
        String email = request.email();
        String code = "ABC123";

        when(signupCodeCache.get(email)).thenReturn(() -> code);
        when(cacheManager.getCache("passCodeCache")).thenReturn(passCodeCache);

        CodeCheckRequest checkRequest = new CodeCheckRequest(email, code);

        // when
        CodeCheckResponse response = authService.checkCode(checkRequest);

        // then
        assertThat(response).isNotNull();
        assertThat(response.passCode()).hasSize(8);
        verify(passCodeCache).put(eq(email), any(String.class));
        verify(signupCodeCache).evict(email);
    }

    @Test
    @DisplayName("인증코드 만료된 경우 예외 발생")
    void checkCode_CodeExpired() {
        // given
        String email = request.email();
        when(signupCodeCache.get(email)).thenReturn(null); // 캐시에 없음

        CodeCheckRequest checkRequest = new CodeCheckRequest(email, "ANYCODE");

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
        String email = request.email();
        String realCode = "REAL12";
        String wrongCode = "WRONG9";

        when(signupCodeCache.get(email)).thenReturn(() -> realCode);

        CodeCheckRequest checkRequest = new CodeCheckRequest(email, wrongCode);

        // when & then
        assertThatThrownBy(() -> authService.checkCode(checkRequest))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.INVALID_CODE);

        verify(signupCodeCache, never()).evict(any());
    }
}