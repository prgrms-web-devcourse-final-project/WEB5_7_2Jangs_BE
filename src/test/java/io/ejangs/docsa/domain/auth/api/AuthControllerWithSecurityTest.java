package io.ejangs.docsa.domain.auth.api;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import io.ejangs.docsa.domain.auth.app.AuthService;
import io.ejangs.docsa.domain.auth.dto.response.SessionCheckResponse;
import io.ejangs.docsa.domain.user.entity.User;
import io.ejangs.docsa.domain.user.security.CustomUserDetails;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.UserErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = true) // 인증 필터 활성화
class AuthControllerWithSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @Test
    @DisplayName("로그인 유효성 확인 성공 - 사용자 정보 반환")
    void checkSession_ValidSession() throws Exception {
        // given
        User user = User.builder()
                .name("이장님")
                .email("test@example.com")
                .password("encodedPassword")
                .build();
        ReflectionTestUtils.setField(user, "id", 1L);
        CustomUserDetails userDetails = CustomUserDetails.from(user);

        given(authService.checkSession(user.getId()))
                .willReturn(new SessionCheckResponse(user.getId(), user.getName()));

        // when & then
        mockMvc.perform(get("/api/auth/session/check")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities())))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.name").value("이장님"));
    }

    @Test
    @DisplayName("로그인 유효성 확인 실패 - 사용자 없음")
    void checkSession_userNotFound() throws Exception {
        // given
        User user = User.builder()
                .name("유령")
                .email("ghost@example.com")
                .password("encodedPassword")
                .build();
        ReflectionTestUtils.setField(user, "id", 999L);
        CustomUserDetails userDetails = CustomUserDetails.from(user);

        given(authService.checkSession(999L))
                .willThrow(new CustomException(UserErrorCode.USER_NOT_FOUND));

        // when & then
        mockMvc.perform(get("/api/auth/session/check")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities())))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())  // 404
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value(UserErrorCode.USER_NOT_FOUND.getMessage()))
                .andExpect(jsonPath("$.error").value("USER_NOT_FOUND"))
                .andDo(print());
    }
}

