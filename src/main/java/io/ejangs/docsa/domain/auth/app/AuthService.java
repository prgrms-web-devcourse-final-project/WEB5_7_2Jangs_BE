package io.ejangs.docsa.domain.auth.app;

import io.ejangs.docsa.domain.auth.dto.request.SignupCodeRequest;
import io.ejangs.docsa.domain.user.dao.mysql.UserRepository;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.AuthErrorCode;
import jakarta.mail.MessagingException;
import java.util.Random;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final MailService mailService;
    private final CacheManager cacheManager;

    @Value("${auth.signup-code-cache-name}")
    private String signupCacheName;

    public void sendSignupCode(SignupCodeRequest request) throws MessagingException {

        if (userRepository.existsByEmail(request.email())) {
            throw new CustomException(AuthErrorCode.DUPLICATE_EMAIL);
        }

        String code = generateVerifyCode();
        cacheManager.getCache(signupCacheName).put(request.email(), code);
        mailService.sendSignupAuthCode(request.email(), code);
    }

    private String generateVerifyCode() {
        Random random = new Random();
        StringBuilder key = new StringBuilder();

        for (int i = 0; i < 6; i++) {
            int index = random.nextInt(2);

            switch (index) {
                case 0 -> key.append((char) (random.nextInt(26) + 65)); // A-Z
                case 1 -> key.append(random.nextInt(10)); // 0-9
            }
        }
        return key.toString();
    }
}