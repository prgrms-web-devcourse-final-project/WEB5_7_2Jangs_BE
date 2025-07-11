package io.ejangs.docsa.domain.auth.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.ejangs.docsa.domain.auth.app.AuthService;
import io.ejangs.docsa.domain.auth.dto.request.SignupCodeRequest;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.AuthErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("회원가입 시 인증코드 전송 요청 성공")
    void sendSignupCode_Success() throws Exception {
        // given
        SignupCodeRequest request = new SignupCodeRequest("test@example.com");

        doNothing().when(authService).sendSignupCode(request);

        // when & then
        mockMvc.perform(post("/api/auth/code/signup-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().string(""))
                .andDo(print());

        verify(authService).sendSignupCode(request);
    }

    @Test
    @DisplayName("잘못된 이메일 형식으로 요청 시 400 에러 발생")
    void sendSignupCode_InvalidEmail() throws Exception {
        // given
        SignupCodeRequest request = new SignupCodeRequest("invalid-email");

        // when & then
        mockMvc.perform(post("/api/auth/code/signup-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andDo(print());

        verify(authService, never()).sendSignupCode(any());
    }

    @Test
    @DisplayName("이메일이 null인 경우 400 에러 발생")
    void sendSignupCode_NullEmail() throws Exception {
        // given
        String requestBody = "{\"email\": null}";

        // when & then
        mockMvc.perform(post("/api/auth/code/signup-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andDo(print());

        verify(authService, never()).sendSignupCode(any());
    }

    @Test
    @DisplayName("이메일이 빈 문자열인 경우 400 에러")
    void sendSignupCode_EmptyEmail() throws Exception {
        // given
        SignupCodeRequest request = new SignupCodeRequest("");

        // when & then
        mockMvc.perform(post("/api/auth/code/signup-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andDo(print());

        verify(authService, never()).sendSignupCode(any());
    }

    @Test
    @DisplayName("이미 가입된 이메일로 요청 시 400 에러 발생")
    void sendSignupCode_DuplicateEmail() throws Exception {
        // given
        SignupCodeRequest request = new SignupCodeRequest("test@example.com");

        doThrow(new CustomException(AuthErrorCode.DUPLICATE_EMAIL))
                .when(authService).sendSignupCode(request);

        // when & then
        mockMvc.perform(post("/api/auth/code/signup-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("이미 가입된 이메일입니다."))
                .andExpect(jsonPath("$.error").value("DUPLICATE_EMAIL"))
                .andDo(print());

        verify(authService).sendSignupCode(request);
    }
}