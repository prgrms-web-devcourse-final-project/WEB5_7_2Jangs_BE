package io.ejangs.docsa.domain.user.util;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

public class SecurityContextUtil {

    private SecurityContextUtil() {
    }

    public static void saveAuthenticationToSession(HttpServletRequest request,
            Authentication authentication) {

        SecurityContextHolder.getContext().setAuthentication(authentication);

        HttpSession session = request.getSession(true);
        session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                SecurityContextHolder.getContext()
        );
    }

    public static Authentication createAuthentication(String email, String password) {
        return new UsernamePasswordAuthenticationToken(email, password);
    }
}
