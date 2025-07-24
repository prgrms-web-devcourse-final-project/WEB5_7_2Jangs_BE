package io.ejangs.docsa.domain.auth.api.integration;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import io.ejangs.docsa.domain.user.entity.User;
import io.ejangs.docsa.domain.user.security.CustomUserDetails;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("세션 유효성 확인 성공")
    void checkSession_Authenticated() throws Exception {
        // given
        User user = createTestUser();
        CustomUserDetails userDetails = CustomUserDetails.from(user);

        // when & then
        mockMvc.perform(get("/api/auth/session/check")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities())))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.name").value("테스트유저"))
                .andDo(print());
    }

    private User createTestUser() {
        User user = User.builder()
                .name("테스트유저")
                .email("test@test.com")
                .password("password")
                .build();
        ReflectionTestUtils.setField(user, "id", 1L);
        return user;
    }

    @Test
    @DisplayName("세션 유효성 확인 실패")
    void checkSession_Unauthenticated() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/session/check")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andDo(print())
                .andReturn();

        String responseContent = result.getResponse().getContentAsString();
        System.out.println("Response content: " + responseContent);

        if (!responseContent.isEmpty()) {
            mockMvc.perform(get("/api/auth/session/check")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.message").value("로그인이 필요합니다."))
                    .andExpect(jsonPath("$.error").value("LOGIN_REQUIRED"));
        }
    }
}
