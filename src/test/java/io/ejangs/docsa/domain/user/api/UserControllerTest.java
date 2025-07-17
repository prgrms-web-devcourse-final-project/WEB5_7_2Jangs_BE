package io.ejangs.docsa.domain.user.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.ejangs.docsa.domain.user.app.UserService;
import io.ejangs.docsa.domain.user.dto.request.PasswordResetRequest;
import io.ejangs.docsa.domain.user.dto.request.UserSignupRequest;
import io.ejangs.docsa.domain.user.dto.response.UserSignupResponse;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.AuthErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @Autowired
    private ObjectMapper objectMapper;

    private UserSignupRequest signupRequest;
    private PasswordResetRequest passwordResetRequest;
    private UserSignupResponse signupResponse;

    @BeforeEach
    void setUp() {
        signupRequest = new UserSignupRequest(
                "이장님",
                "test@example.com",
                "Password123",
                "abc12345"
        );

        passwordResetRequest = new PasswordResetRequest(
                "test@example.com",
                "NewPassword123",
                "abc12345"
        );

        signupResponse = new UserSignupResponse(1L, "이장님");
    }

    @Test
    @DisplayName("회원가입 성공")
    void signup_Success() throws Exception {
        // given
        when(userService.signup(any(UserSignupRequest.class))).thenReturn(signupResponse);

        // when & then
        mockMvc.perform(post("/api/user/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signupRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.name").value("이장님"))
                .andDo(print());

        verify(userService).signup(any(UserSignupRequest.class));
    }

    @Test
    @DisplayName("이미 가입된 이메일로 요청 시 400 에러 발생")
    void signup_DuplicateEmail() throws Exception {
        // given
        doThrow(new CustomException(AuthErrorCode.DUPLICATE_EMAIL))
                .when(userService).signup(any(UserSignupRequest.class));

        // when & then
        mockMvc.perform(post("/api/user/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signupRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("이미 가입된 이메일입니다."))
                .andExpect(jsonPath("$.error").value("DUPLICATE_EMAIL"))
                .andDo(print());

        verify(userService).signup(any(UserSignupRequest.class));
    }

    @Test
    @DisplayName("만료된 인증코드로 요청 시 400 에러 발생")
    void signup_ExpiredCode() throws Exception {
        // given
        doThrow(new CustomException(AuthErrorCode.EXPIRED_CODE))
                .when(userService).signup(any(UserSignupRequest.class));

        // when & then
        mockMvc.perform(post("/api/user/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signupRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("EXPIRED_CODE"))
                .andExpect(jsonPath("$.message").value("인증 코드가 만료되었습니다."))
                .andDo(print());

        verify(userService).signup(any(UserSignupRequest.class));
    }

    @Test
    @DisplayName("잘못된 인증코드로 요청 시 400 에러 발생")
    void signup_InvalidCode() throws Exception {
        // given
        doThrow(new CustomException(AuthErrorCode.INVALID_CODE))
                .when(userService).signup(any(UserSignupRequest.class));

        // when & then
        mockMvc.perform(post("/api/user/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signupRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_CODE"))
                .andExpect(jsonPath("$.message").value("인증 코드가 일치하지 않습니다."))
                .andDo(print());

        verify(userService).signup(any(UserSignupRequest.class));
    }

    @Test
    @DisplayName("잘못된 이메일 형식으로 요청 시 400 에러 발생")
    void signup_InvalidEmail() throws Exception {
        // given
        UserSignupRequest invalidRequest = new UserSignupRequest(
                "이장님",
                "invalid-email",
                "password123",
                "abc12345"
        );

        // when & then
        mockMvc.perform(post("/api/user/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andDo(print());

        verify(userService, never()).signup(any());
    }

    @Test
    @DisplayName("이메일이 null인 경우 400 에러 발생")
    void signup_NullEmail() throws Exception {
        // given
        String requestBody = "{\"name\":\"이장님\",\"email\":null,\"password\":\"password123\",\"passCode\":\"abc12345\"}";

        // when & then
        mockMvc.perform(post("/api/user/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andDo(print());

        verify(userService, never()).signup(any());
    }

    @Test
    @DisplayName("이메일이 빈 문자열인 경우 400 에러 발생")
    void signup_EmptyEmail() throws Exception {
        // given
        UserSignupRequest invalidRequest = new UserSignupRequest(
                "이장님",
                "",
                "password123",
                "abc12345"
        );

        // when & then
        mockMvc.perform(post("/api/user/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andDo(print());

        verify(userService, never()).signup(any());
    }

    @Test
    @DisplayName("이름이 빈 문자열인 경우 400 에러 발생")
    void signup_EmptyName() throws Exception {
        // given
        UserSignupRequest invalidRequest = new UserSignupRequest(
                "",
                "test@example.com",
                "password123",
                "abc12345"
        );

        // when & then
        mockMvc.perform(post("/api/user/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andDo(print());

        verify(userService, never()).signup(any());
    }

    @Test
    @DisplayName("비밀번호가 8자 미만인 경우 400 에러 발생")
    void signup_ShortPassword() throws Exception {
        // given
        UserSignupRequest invalidRequest = new UserSignupRequest(
                "이장님",
                "test@example.com",
                "1234567",
                "abc12345"
        );

        // when & then
        mockMvc.perform(post("/api/user/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andDo(print());

        verify(userService, never()).signup(any());
    }

    @Test
    @DisplayName("비밀번호 변경 성공")
    void resetPassword_Success() throws Exception {
        // when & then
        mockMvc.perform(post("/api/user/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(passwordResetRequest)))
                .andExpect(status().isOk())
                .andDo(print());

        verify(userService).resetPassword(any(PasswordResetRequest.class));
    }

    @Test
    @DisplayName("기존 비밀번호와 동일한 경우 400 에러 발생")
    void resetPassword_SameAsOldPassword() throws Exception {
        // given
        doThrow(new CustomException(AuthErrorCode.SAME_AS_OLD_PASSWORD))
                .when(userService).resetPassword(any(PasswordResetRequest.class));

        // when & then
        mockMvc.perform(post("/api/user/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(passwordResetRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("SAME_AS_OLD_PASSWORD"))
                .andExpect(jsonPath("$.message").value("기존 비밀번호와 동일한 비밀번호입니다."))
                .andDo(print());
    }
}