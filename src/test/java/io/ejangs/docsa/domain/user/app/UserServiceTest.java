package io.ejangs.docsa.domain.user.app;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.ejangs.docsa.domain.user.dao.UserRepository;
import io.ejangs.docsa.domain.user.dto.request.UserSignupRequest;
import io.ejangs.docsa.domain.user.dto.response.UserSignupResponse;
import io.ejangs.docsa.domain.user.entity.User;
import io.ejangs.docsa.domain.user.util.UserMapper;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.AuthErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache passCodeCache;

    @InjectMocks
    private UserService userService;

    private UserSignupRequest request;
    private User user;
    private UserSignupResponse response;

    @BeforeEach
    void setUp() {
        request = new UserSignupRequest(
                "이장님",
                "test@example.com",
                "password123",
                "abc12345"
        );

        user = User.builder()
                .name("이장님")
                .email("test@example.com")
                .password("encodedPassword")
                .build();

        response = new UserSignupResponse(1L, "이장님");

        ReflectionTestUtils.setField(userService, "passcodeCacheName", "passCodeCache");
        lenient().when(cacheManager.getCache("passCodeCache")).thenReturn(passCodeCache);
    }

    @Test
    @DisplayName("회원가입 성공")
    void signup_Success() {
        // given
        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(passCodeCache.get(request.email())).thenReturn(() -> "abc12345");
        when(passwordEncoder.encode(request.password())).thenReturn("encodedPassword");
        when(userMapper.toEntity(request, "encodedPassword")).thenReturn(user);
        when(userRepository.save(user)).thenReturn(user);
        when(userMapper.toSignupResponse(user)).thenReturn(response);

        // when
        UserSignupResponse result = userService.signup(request);

        // then
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.name()).isEqualTo("이장님");

        verify(userRepository).existsByEmail(request.email());
        verify(passCodeCache).get(request.email());
        verify(passwordEncoder).encode(request.password());
        verify(userRepository).save(user);
        verify(passCodeCache).evict(request.email());
    }

    @Test
    @DisplayName("이미 가입된 이메일로 요청 시 예외 발생")
    void signup_DuplicateEmail() {
        // given
        when(userRepository.existsByEmail(request.email())).thenReturn(true);

        // when & then
        assertThatThrownBy(() -> userService.signup(request))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.DUPLICATE_EMAIL);

        verify(userRepository).existsByEmail(request.email());
        verify(passCodeCache, never()).get(any());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    @DisplayName("인증코드 만료된 경우 예외 발생")
    void signup_ExpiredCode() {
        // given
        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(passCodeCache.get(request.email())).thenReturn(null);

        // when & then
        assertThatThrownBy(() -> userService.signup(request))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.EXPIRED_CODE);

        verify(userRepository).existsByEmail(request.email());
        verify(passCodeCache).get(request.email());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    @DisplayName("인증코드 불일치 시 예외 발생")
    void signup_InvalidCode() {
        // given
        String realCode = "real1234";
        String wrongCode = "wrong123";

        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(passCodeCache.get(request.email())).thenReturn(() -> realCode);

        UserSignupRequest wrongRequest = new UserSignupRequest(
                request.name(),
                request.email(),
                request.password(),
                wrongCode
        );

        // when & then
        assertThatThrownBy(() -> userService.signup(wrongRequest))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.INVALID_CODE);

        verify(userRepository).existsByEmail(request.email());
        verify(passCodeCache).get(request.email());
        verify(passwordEncoder, never()).encode(any());
    }
}