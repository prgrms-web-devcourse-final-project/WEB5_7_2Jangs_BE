package io.ejangs.docsa.domain.user.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.ejangs.docsa.global.exception.ErrorResponse;
import io.ejangs.docsa.global.exception.errorcode.AuthErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.session.SessionAuthenticationException;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public void commence(HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException) throws IOException {

        ErrorResponse errorResponse = determineErrorResponse(authException);

        response.setStatus(errorResponse.status());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        MAPPER.writeValue(response.getWriter(), errorResponse);
    }

    private ErrorResponse determineErrorResponse(AuthenticationException authException) {
        return switch (authException) {
            // 잘못된 이메일 or 비밀번호 입력
            case BadCredentialsException ignored ->
                    ErrorResponse.from(AuthErrorCode.INVALID_CREDENTIALS);
            // 존재하지 않는 이메일로 로그인 시도
            case UsernameNotFoundException ignored ->
                    ErrorResponse.from(AuthErrorCode.INVALID_CREDENTIALS);
            // 세션이 없거나 만료된 상황
            case InsufficientAuthenticationException ignored ->
                    ErrorResponse.from(AuthErrorCode.LOGIN_REQUIRED);
            // 동시 로그인 제한 초과 등 세션 관련 문제 발생
            case SessionAuthenticationException ignored ->
                    ErrorResponse.from(AuthErrorCode.INVALID_SESSION);
            default -> {
                log.error("Unhandled authentication exception: {}",
                        authException.getClass().getSimpleName(), authException);
                yield ErrorResponse.from(AuthErrorCode.AUTHENTICATION_FAILED);
            }
        };
    }
}
