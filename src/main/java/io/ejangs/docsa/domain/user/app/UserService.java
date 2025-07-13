package io.ejangs.docsa.domain.user.app;

import io.ejangs.docsa.domain.user.dao.mysql.UserRepository;
import io.ejangs.docsa.domain.user.dto.request.UserSignupRequest;
import io.ejangs.docsa.domain.user.dto.response.UserSignupResponse;
import io.ejangs.docsa.domain.user.entity.User;
import io.ejangs.docsa.domain.user.util.UserMapper;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.AuthErrorCode;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

@Service
@Transactional
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CacheManager cacheManager;

    @Value("${auth.passcode-cache-name}")
    private String passcodeCacheName;

    public UserSignupResponse signup(UserSignupRequest request) {

        if (userRepository.existsByEmail(request.email())) {
            throw new CustomException(AuthErrorCode.DUPLICATE_EMAIL);
        }

        Cache cache = cacheManager.getCache(passcodeCacheName);
        Cache.ValueWrapper cachedValue = cache.get(request.email());

        if (ObjectUtils.isEmpty(cachedValue)) {
            throw new CustomException(AuthErrorCode.EXPIRED_CODE);
        }

        String cachedCode = (String) cachedValue.get();
        if (!cachedCode.equals(request.passCode())) {
            throw new CustomException(AuthErrorCode.INVALID_CODE);
        }

        String encodedPassword = passwordEncoder.encode(request.password());
        User user = userMapper.toEntity(request, encodedPassword);
        User savedUser = userRepository.save(user);

        // passCode 캐시 제거
        cache.evict(request.email());

        return userMapper.toSignupResponse(savedUser);
    }
}
