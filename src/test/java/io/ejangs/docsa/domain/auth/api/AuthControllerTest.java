package io.ejangs.docsa.domain.auth.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.ejangs.docsa.domain.auth.app.AuthService;
import io.ejangs.docsa.domain.auth.dto.request.CodeCheckRequest;
import io.ejangs.docsa.domain.auth.dto.request.SignupCodeRequest;
import io.ejangs.docsa.domain.auth.dto.response.CodeCheckResponse;
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

    @Test
    @DisplayName("정상적인 인증코드 검증 성공")
    void checkCode_Success() throws Exception {
        // given
        CodeCheckRequest request = new CodeCheckRequest("test@example.com", "ABC123");
        CodeCheckResponse response = new CodeCheckResponse("generatedPass123");

        when(authService.checkCode(request)).thenReturn(response);

        // when & then
        mockMvc.perform(post("/api/auth/code/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passCode").value("generatedPass123"))
                .andDo(print());

        verify(authService).checkCode(request);
    }

    @Test
    @DisplayName("만료된 인증코드로 요청 시 400 에러 발생")
    void checkCode_Expired() throws Exception {
        // given
        CodeCheckRequest request = new CodeCheckRequest("test@example.com", "ABC123");
        doThrow(new CustomException(AuthErrorCode.EXPIRED_CODE))
                .when(authService).checkCode(request);

        // when & then
        mockMvc.perform(post("/api/auth/code/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("EXPIRED_CODE"))
                .andExpect(jsonPath("$.message").value("인증 코드가 만료되었습니다."))
                .andDo(print());

        verify(authService).checkCode(request);
    }

    @Test
    @DisplayName("잘못된 인증코드로 요청 시 400 에러 발생")
    void checkCode_Invalid() throws Exception {
        // given
        CodeCheckRequest request = new CodeCheckRequest("test@example.com", "WRONG123");
        doThrow(new CustomException(AuthErrorCode.INVALID_CODE))
                .when(authService).checkCode(request);

        // when & then
        mockMvc.perform(post("/api/auth/code/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_CODE"))
                .andExpect(jsonPath("$.message").value("인증 코드가 일치하지 않습니다."))
                .andDo(print());

        verify(authService).checkCode(request);
    }

    @Test
    @DisplayName("이메일 또는 코드가 비어있는 경우 400 에러 발생")
    void checkCode_EmptyFields() throws Exception {
        // given
        CodeCheckRequest request = new CodeCheckRequest("", "");

        // when & then
        mockMvc.perform(post("/api/auth/code/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andDo(print());

        verify(authService, never()).checkCode(any());
    }
}