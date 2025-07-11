package io.ejangs.docsa.domain.auth.app;

import io.ejangs.docsa.domain.auth.dto.request.CodeCheckRequest;
import io.ejangs.docsa.domain.auth.dto.request.SignupCodeRequest;
import io.ejangs.docsa.domain.auth.dto.response.CodeCheckResponse;
import io.ejangs.docsa.domain.user.dao.UserRepository;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.AuthErrorCode;
import jakarta.mail.MessagingException;
import java.util.Random;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final MailService mailService;
    private final CacheManager cacheManager;

    @Value("${auth.signup-code-cache-name}")
    private String signupCacheName;

    @Value("${auth.passcode-cache-name}")
    private String passcodeCacheName;

    public void sendSignupCode(SignupCodeRequest request) throws MessagingException {

        if (userRepository.existsByEmail(request.email())) {
            throw new CustomException(AuthErrorCode.DUPLICATE_EMAIL);
        }

        String code = generateCode();
        cacheManager.getCache(signupCacheName).put(request.email(), code);
        mailService.sendSignupAuthCode(request.email(), code);
    }

    public CodeCheckResponse checkCode(CodeCheckRequest request) {

        Cache cache = cacheManager.getCache(signupCacheName);
        Cache.ValueWrapper cachedValue = cache.get(request.email());

        if (ObjectUtils.isEmpty(cachedValue)) {
            throw new CustomException(AuthErrorCode.EXPIRED_CODE);
        }

        String cachedCode = (String) cachedValue.get();
        if (!cachedCode.equals(request.code())) {
            throw new CustomException(AuthErrorCode.INVALID_CODE);
        }

        String passCode = generatePassCode();
        cacheManager.getCache(passcodeCacheName).put(request.email(), passCode);
        cache.evict(request.email());

        return new CodeCheckResponse(passCode);
    }

    private String generateCode() {
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

    private String generatePassCode() {
        Random random = new Random();
        StringBuilder passCode = new StringBuilder();

        for (int i = 0; i < 8; i++) {
            int index = random.nextInt(2);

            switch (index) {
                case 0 -> passCode.append((char) (random.nextInt(26) + 97)); // a-z
                case 1 -> passCode.append(random.nextInt(10)); // 0-9
            }
        }
        return passCode.toString();
    }
}