package io.ejangs.docsa.global.init;

import io.ejangs.docsa.domain.user.dao.mysql.UserRepository;
import io.ejangs.docsa.domain.user.entity.User;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("local")
@RequiredArgsConstructor
public class TestUserInitializer {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    private static final String testUserEmail = "test@test.com";
    private static final String testUserName = "test";
    private static final String testUserPassword = "Testtest1";

    @PostConstruct
    @Transactional
    public void intiTestUser() {
        if (userRepository.findByEmail(testUserEmail).isEmpty()) {
            User user = User.builder()
                    .email(testUserEmail)
                    .name(testUserName)
                    .password(passwordEncoder.encode(testUserPassword))
                    .build();
            userRepository.save(user);
        }
    }
}
