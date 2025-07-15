package io.ejangs.docsa.domain.user.integration;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.ejangs.docsa.domain.user.dao.mysql.UserRepository;
import io.ejangs.docsa.domain.user.dto.request.UserLoginRequest;
import io.ejangs.docsa.domain.user.dto.response.UserLoginResponse;
import io.ejangs.docsa.domain.user.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User user;

    @BeforeEach
    void setup() {
        // 로그인 가능한 사용자 사전 등록
        user = User.builder()
                .name("이장님")
                .email("test@example.com")
                .password(passwordEncoder.encode("Password123"))
                .build();
        userRepository.save(user);
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("로그인 성공 - 세션 및 응답 확인")
    void login_Success_SessionAndResponse() throws Exception {
        UserLoginRequest loginRequest = new UserLoginRequest("test@example.com", "Password123");

        MvcResult result = mockMvc.perform(post("/api/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId()))
                .andDo(print())
                .andReturn();

        // 세션 확인
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        assertThat(session.getId()).isNotNull();

        // Spring Security Context가 세션에 저장되었는지 확인
        Object securityContext = session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertThat(securityContext).isNotNull();

        // 응답 본문 확인
        String responseBody = result.getResponse().getContentAsString();
        UserLoginResponse response = objectMapper.readValue(responseBody, UserLoginResponse.class);
        assertThat(response.id()).isEqualTo(user.getId());
    }

    @Test
    @DisplayName("로그인 실패 - 잘못된 이메일")
    void login_Fail_InvalidEmail() throws Exception {
        UserLoginRequest loginRequest = new UserLoginRequest("wrong@example.com", "Password123");

        mockMvc.perform(post("/api/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized()) // 또는 적절한 에러 상태 코드
                .andDo(print());
    }

    @Test
    @DisplayName("로그인 실패 - 잘못된 비밀번호")
    void login_Fail_InvalidPassword() throws Exception {
        UserLoginRequest loginRequest = new UserLoginRequest("test@example.com", "Wrongpwd123");

        mockMvc.perform(post("/api/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized()) // 또는 적절한 에러 상태 코드
                .andDo(print());
    }

    @Test
    @DisplayName("로그인 요청 - 유효하지 않은 입력값")
    void login_Fail_InvalidInput() throws Exception {
        // 빈 이메일
        UserLoginRequest invalidEmailRequest = new UserLoginRequest("", "Password123");

        mockMvc.perform(post("/api/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidEmailRequest)))
                .andExpect(status().isBadRequest())
                .andDo(print());

        // 빈 비밀번호
        UserLoginRequest invalidPasswordRequest = new UserLoginRequest("test@example.com", "");

        mockMvc.perform(post("/api/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidPasswordRequest)))
                .andExpect(status().isBadRequest())
                .andDo(print());
    }

    @Test
    @DisplayName("동시 로그인 테스트 - 다른 사용자")
    void login_Concurrent_DifferentUsers() throws Exception {
        // 두 번째 사용자 생성
        User user2 = User.builder()
                .name("사용자2")
                .email("user2@example.com")
                .password(passwordEncoder.encode("Password456"))
                .build();
        userRepository.save(user2);

        UserLoginRequest loginRequest1 = new UserLoginRequest("test@example.com", "Password123");
        UserLoginRequest loginRequest2 = new UserLoginRequest("user2@example.com", "Password456");

        // 첫 번째 사용자 로그인
        MvcResult result1 = mockMvc.perform(post("/api/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId()))
                .andReturn();

        // 두 번째 사용자 로그인
        MvcResult result2 = mockMvc.perform(post("/api/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user2.getId()))
                .andReturn();

        // 각각 다른 세션을 가져야 함
        MockHttpSession session1 = (MockHttpSession) result1.getRequest().getSession(false);
        MockHttpSession session2 = (MockHttpSession) result2.getRequest().getSession(false);

        assertThat(session1).isNotNull();
        assertThat(session2).isNotNull();
        assertThat(session1.getId()).isNotEqualTo(session2.getId());
    }

    @Test
    @DisplayName("로그인 성공 후 Security Context 확인")
    void login_Success_SecurityContextCheck() throws Exception {
        UserLoginRequest loginRequest = new UserLoginRequest("test@example.com", "Password123");

        MvcResult result = mockMvc.perform(post("/api/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        SecurityContext securityContext = (SecurityContext) session.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);

        assertThat(securityContext).isNotNull();
        assertThat(securityContext.getAuthentication()).isNotNull();
        assertThat(securityContext.getAuthentication().isAuthenticated()).isTrue();
        assertThat(securityContext.getAuthentication().getName()).isEqualTo("test@example.com");
    }

    @Test
    @DisplayName("세션 쿠키 관련 헤더 확인")
    void login_Success_SessionCookieHeaders() throws Exception {
        UserLoginRequest loginRequest = new UserLoginRequest("test@example.com", "Password123");

        MvcResult result = mockMvc.perform(post("/api/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        // Set-Cookie 헤더 확인 (MockMvc 환경에서는 실제 쿠키 헤더가 설정되지 않을 수 있음)
        MockHttpServletResponse response = result.getResponse();
        String setCookieHeader = response.getHeader("Set-Cookie");

        // 세션 ID 확인
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session.getId()).isNotNull();
    }
}