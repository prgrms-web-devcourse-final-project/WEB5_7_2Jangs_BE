package io.ejangs.docsa.domain.auth.util;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class AuthCodeGenerator {

    private static final SecureRandom random = new SecureRandom();

    public String generateVerifyCode() {
        return generateCode(6, true);
    }

    public String generatePassCode() {
        return generateCode(8, false);
    }

    private String generateCode(int length, boolean useUpperCase) {
        StringBuilder code = new StringBuilder(length);

        for (int i = 0; i < length; i++) {
            boolean isLetter = random.nextBoolean();

            if (isLetter) {
                char baseChar = useUpperCase ? 'A' : 'a';
                code.append((char) (random.nextInt(26) + baseChar));
            } else {
                code.append(random.nextInt(10));
            }
        }

        return code.toString();
    }
}