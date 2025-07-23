package io.ejangs.docsa.domain.auth.app;

import io.ejangs.docsa.domain.auth.dto.request.CodeCheckRequest;
import io.ejangs.docsa.domain.auth.dto.request.PwdResetCodeRequest;
import io.ejangs.docsa.domain.auth.dto.request.SignupCodeRequest;
import io.ejangs.docsa.domain.auth.dto.response.SessionCheckResponse;
import io.ejangs.docsa.domain.auth.model.CodeType;
import io.ejangs.docsa.domain.auth.util.AuthCodeGenerator;
import io.ejangs.docsa.domain.auth.util.AuthMapper;
import io.ejangs.docsa.domain.user.dao.mysql.UserRepository;
import io.ejangs.docsa.domain.auth.dto.response.CodeCheckResponse;
import io.ejangs.docsa.domain.user.entity.User;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.AuthErrorCode;
import io.ejangs.docsa.global.exception.errorcode.UserErrorCode;
import jakarta.mail.MessagingException;
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
    private final AuthCodeGenerator authCodeGenerator;

    @Value("${auth.signup-code-cache-name}")
    private String signupCacheName;

    @Value("${auth.passcode-cache-name}")
    private String passcodeCacheName;

    @Value("${auth.pwd-reset-code-cache-name}")
    private String pwdResetCacheName;

    public void sendSignupCode(SignupCodeRequest request) throws MessagingException {

        if (userRepository.existsByEmail(request.email())) {
            throw new CustomException(AuthErrorCode.DUPLICATE_EMAIL);
        }

        String code = authCodeGenerator.generateVerifyCode();
        getRequiredCache(signupCacheName).put(request.email(), code);
        mailService.sendCodeMail(request.email(), code);
    }

    public void sendResetPwdCode(PwdResetCodeRequest request) throws MessagingException {

        if (!userRepository.existsByEmail(request.email())) {
            throw new CustomException(UserErrorCode.USER_NOT_FOUND);
        }

        String code = authCodeGenerator.generateVerifyCode();
        getRequiredCache(pwdResetCacheName).put(request.email(), code);
        mailService.sendCodeMail(request.email(), code);
    }

    public CodeCheckResponse checkCode(CodeCheckRequest request) {

        validateUserExistence(request.email(), request.type());

        Cache cache = getCodeCacheByType(request.type());
        Cache.ValueWrapper cachedValue = cache.get(request.email());

        if (ObjectUtils.isEmpty(cachedValue)) {
            throw new CustomException(AuthErrorCode.EXPIRED_CODE);
        }

        String cachedCode = (String) cachedValue.get();
        if (!cachedCode.equals(request.code())) {
            throw new CustomException(AuthErrorCode.INVALID_CODE);
        }

        String passCode = authCodeGenerator.generatePassCode();
        getRequiredCache(passcodeCacheName).put(request.email(), passCode);
        cache.evict(request.email());

        return new CodeCheckResponse(passCode);
    }

    private void validateUserExistence(String email, CodeType type) {

        boolean exists = userRepository.existsByEmail(email);

        switch (type) {
            case SIGNUP -> {
                if (exists) {
                    throw new CustomException(AuthErrorCode.ALREADY_REGISTERED_USER);
                }
            }
            case RESET_PASSWORD -> {
                if (!exists) {
                    throw new CustomException(UserErrorCode.USER_NOT_FOUND);
                }
            }
            default -> throw new CustomException(AuthErrorCode.UNSUPPORTED_CODE_TYPE);
        }
    }

    private Cache getCodeCacheByType(CodeType type) {

        return switch (type) {
            case SIGNUP -> getRequiredCache(signupCacheName);
            case RESET_PASSWORD -> getRequiredCache(pwdResetCacheName);
        };
    }

    private Cache getRequiredCache(String name) {
        Cache cache = cacheManager.getCache(name);
        if (cache == null) {
            throw new CustomException(AuthErrorCode.INTERNAL_ERROR);
        }
        return cache;
    }

    public SessionCheckResponse checkSession(Long userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));

        return AuthMapper.toSessionCheckResponse(user);
    }
}