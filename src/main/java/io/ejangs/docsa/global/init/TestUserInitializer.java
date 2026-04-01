package io.ejangs.docsa.global.init;

import io.ejangs.docsa.domain.user.dao.mysql.UserRepository;
import io.ejangs.docsa.domain.user.entity.User;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile({"local", "stg"})
@Transactional(rollbackFor = Exception.class)
@RequiredArgsConstructor
public class TestUserInitializer {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    private static final String testUserEmail = "test@test.com";
    private static final String testUserName = "test";
    private static final String testUserPassword = "Testtest1";
    private static final String perfUserName = "perf";
    private static final String perfUserPrefix = "perfdel";
    private static final String perfUserDomain = "test.com";

    @Value("${perf.seed.user-count:0}")
    private int perfSeedUserCount;

    @PostConstruct
    public void initTestUser() {
        String encodedPassword = createBaseTestUserIfAbsent();
        seedPerfUsersIfEnabled(encodedPassword);
    }

    private String createBaseTestUserIfAbsent() {
        if (userRepository.findByEmail(testUserEmail).isEmpty()) {
            String encodedPassword = passwordEncoder.encode(testUserPassword);
            User user = User.builder()
                    .email(testUserEmail)
                    .name(testUserName)
                    .password(encodedPassword)
                    .build();
            userRepository.save(user);
            return encodedPassword;
        }

        return userRepository.findByEmail(testUserEmail)
                .map(User::getPassword)
                .orElseGet(() -> passwordEncoder.encode(testUserPassword));
    }

    private void seedPerfUsersIfEnabled(String encodedPassword) {
        if (perfSeedUserCount <= 0) {
            return;
        }

        for (int i = 1; i <= perfSeedUserCount; i++) {
            String email = String.format("%s_u%03d@%s", perfUserPrefix, i, perfUserDomain);
            String name = String.format("%s_u%03d", perfUserName, i);
            if (userRepository.existsByEmail(email)) {
                continue;
            }
            userRepository.save(User.builder()
                    .email(email)
                    .name(name)
                    .password(encodedPassword)
                    .build());
        }
    }
}
