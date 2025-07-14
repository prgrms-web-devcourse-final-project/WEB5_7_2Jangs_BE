package io.ejangs.docsa.domain.user.app;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.ejangs.docsa.domain.user.dao.mysql.UserRepository;
import io.ejangs.docsa.domain.user.dto.request.UserLoginRequest;
import io.ejangs.docsa.domain.user.dto.request.UserSignupRequest;
import io.ejangs.docsa.domain.user.dto.response.UserLoginResponse;
import io.ejangs.docsa.domain.user.dto.response.UserSignupResponse;
import io.ejangs.docsa.domain.user.entity.User;
import io.ejangs.docsa.domain.user.security.CustomUserDetails;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.AuthErrorCode;
import io.ejangs.docsa.global.exception.errorcode.UserErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache passCodeCache;

    @InjectMocks
    private UserService userService;

    private UserSignupRequest signupRequest;
    private UserLoginRequest loginRequest;
    private User user;
    private UserSignupResponse signupResponse;

    @BeforeEach
    void setUp() {
        signupRequest = new UserSignupRequest(
                "이장님",
                "test@example.com",
                "password123",
                "abc12345"
        );

        loginRequest = new UserLoginRequest(
                "test@example.com",
                "password123"
        );

        user = User.builder()
                .name("이장님")
                .email("test@example.com")
                .password("encodedPassword")
                .build();

        signupResponse = new UserSignupResponse(1L, "이장님");

        ReflectionTestUtils.setField(userService, "passcodeCacheName", "passCodeCache");
        ReflectionTestUtils.setField(user, "id", 1L);
        lenient().when(cacheManager.getCache("passCodeCache")).thenReturn(passCodeCache);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("회원가입 성공")
    void signup_Success() {
        // given
        when(userRepository.existsByEmail(signupRequest.email())).thenReturn(false);
        when(passCodeCache.get(signupRequest.email())).thenReturn(() -> "abc12345");
        when(passwordEncoder.encode(signupRequest.password())).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenReturn(user);

        // when
        UserSignupResponse result = userService.signup(signupRequest);

        // then
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.name()).isEqualTo("이장님");

        verify(userRepository).existsByEmail(signupRequest.email());
        verify(passCodeCache).get(signupRequest.email());
        verify(passwordEncoder).encode(signupRequest.password());
        verify(userRepository).save(any(User.class));
        verify(passCodeCache).evict(signupRequest.email());
    }

    @Test
    @DisplayName("이미 가입된 이메일로 요청 시 예외 발생")
    void signup_DuplicateEmail() {
        // given
        when(userRepository.existsByEmail(signupRequest.email())).thenReturn(true);

        // when & then
        assertThatThrownBy(() -> userService.signup(signupRequest))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.DUPLICATE_EMAIL);

        verify(userRepository).existsByEmail(signupRequest.email());
        verify(passCodeCache, never()).get(any());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    @DisplayName("인증코드 만료된 경우 예외 발생")
    void signup_ExpiredCode() {
        // given
        when(userRepository.existsByEmail(signupRequest.email())).thenReturn(false);
        when(passCodeCache.get(signupRequest.email())).thenReturn(null);

        // when & then
        assertThatThrownBy(() -> userService.signup(signupRequest))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.EXPIRED_CODE);

        verify(userRepository).existsByEmail(signupRequest.email());
        verify(passCodeCache).get(signupRequest.email());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    @DisplayName("인증코드 불일치 시 예외 발생")
    void signup_InvalidCode() {
        // given
        String realCode = "real1234";
        String wrongCode = "wrong123";

        when(userRepository.existsByEmail(signupRequest.email())).thenReturn(false);
        when(passCodeCache.get(signupRequest.email())).thenReturn(() -> realCode);

        UserSignupRequest wrongRequest = new UserSignupRequest(
                signupRequest.name(),
                signupRequest.email(),
                signupRequest.password(),
                wrongCode
        );

        // when & then
        assertThatThrownBy(() -> userService.signup(wrongRequest))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.INVALID_CODE);

        verify(userRepository).existsByEmail(signupRequest.email());
        verify(passCodeCache).get(signupRequest.email());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    @DisplayName("로그인 성공 테스트")
    void login_Success() {
        // given
        CustomUserDetails userDetails = CustomUserDetails.from(user);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);

        // HttpServletRequest
        HttpServletRequest mockRequest = mock(HttpServletRequest.class);
        HttpSession mockSession = mock(HttpSession.class);
        when(mockRequest.getSession(true)).thenReturn(mockSession);

        // when
        UserLoginResponse response = userService.login(loginRequest, mockRequest);

        // then
        assertThat(response.id()).isEqualTo(1L);

        // SecurityContextHolder에 인증 정보가 저장되었는지 확인
        Authentication storedAuth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(storedAuth).isNotNull();
        assertThat(storedAuth.getPrincipal()).isInstanceOf(CustomUserDetails.class);

        CustomUserDetails storedUserDetails = (CustomUserDetails) storedAuth.getPrincipal();
        assertThat(storedUserDetails.getId()).isEqualTo(1L);
        assertThat(storedUserDetails.getEmail()).isEqualTo("test@example.com");
        assertThat(storedUserDetails.getName()).isEqualTo("이장님");

        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(mockRequest).getSession(true);
        verify(mockSession).setAttribute(eq("SPRING_SECURITY_CONTEXT"), any());
    }

    @Test
    @DisplayName("로그인 실패 테스트 - 잘못된 인증정보")
    void login_Fail_InvalidCredentials() {
        // given
        UserLoginRequest invalidRequest = new UserLoginRequest("test@example.com", "wrongPassword");

        HttpServletRequest mockRequest = mock(HttpServletRequest.class);

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Invalid credentials"));

        // when & then
        assertThatThrownBy(() -> userService.login(invalidRequest, mockRequest))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.INVALID_CREDENTIALS);

        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
    }

    @Test
    @DisplayName("로그인 실패 테스트 - 존재하지 않는 사용자")
    void login_Fail_UserNotFound() {
        // given
        UserLoginRequest notFoundRequest = new UserLoginRequest("notfound@example.com",
                "password123");

        HttpServletRequest mockRequest = mock(HttpServletRequest.class);

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new CustomException(UserErrorCode.USER_NOT_FOUND));

        // when & then
        assertThatThrownBy(() -> userService.login(notFoundRequest, mockRequest))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", UserErrorCode.USER_NOT_FOUND);

        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
    }
}