package io.ejangs.docsa.domain.auth.app;

import io.ejangs.docsa.domain.auth.dto.request.CodeCheckRequest;
import io.ejangs.docsa.domain.auth.dto.request.PwdResetCodeRequest;
import io.ejangs.docsa.domain.auth.dto.request.SignupCodeRequest;
import io.ejangs.docsa.domain.auth.util.AuthCodeGenerator;
import io.ejangs.docsa.domain.user.dao.mysql.UserRepository;
import io.ejangs.docsa.domain.auth.dto.response.CodeCheckResponse;
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
        cacheManager.getCache(signupCacheName).put(request.email(), code);
        mailService.sendCodeMail(request.email(), code);
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

        String passCode = authCodeGenerator.generatePassCode();
        cacheManager.getCache(passcodeCacheName).put(request.email(), passCode);
        cache.evict(request.email());

        return new CodeCheckResponse(passCode);
    }

    public void sendResetPwdCode(PwdResetCodeRequest request) throws MessagingException {

        if (!userRepository.existsByEmail(request.email())) {
            throw new CustomException(UserErrorCode.USER_NOT_FOUND);
        }

        String code = authCodeGenerator.generateVerifyCode();
        cacheManager.getCache(pwdResetCacheName).put(request.email(), code);
        mailService.sendCodeMail(request.email(), code);
    }
}