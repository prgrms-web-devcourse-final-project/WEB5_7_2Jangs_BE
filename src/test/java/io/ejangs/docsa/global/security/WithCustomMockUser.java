package io.ejangs.docsa.global.security;


import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.test.context.support.WithSecurityContext;

@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Documented
@WithSecurityContext(factory = WithCustomMockUserSecurityContextFactory.class)
public @interface WithCustomMockUser {
    long id() default 1L;
    String email() default "test@example.com";
    String password() default "password";
}
