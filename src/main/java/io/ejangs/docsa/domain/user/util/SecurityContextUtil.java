package io.ejangs.docsa.domain.user.util;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
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

    public static void clearAuthentication(HttpServletRequest request,
            HttpServletResponse response) {

        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }

        SecurityContextHolder.clearContext();
        clearJSessionCookie(response);
    }

    private static void clearJSessionCookie(HttpServletResponse response) {
        Cookie jsessionCookie = new Cookie("JSESSIONID", null);
        jsessionCookie.setPath("/");
        jsessionCookie.setHttpOnly(true);
        jsessionCookie.setMaxAge(0);
        response.addCookie(jsessionCookie);
    }
}
