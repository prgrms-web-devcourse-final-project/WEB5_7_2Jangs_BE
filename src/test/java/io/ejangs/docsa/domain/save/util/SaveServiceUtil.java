package io.ejangs.docsa.domain.save.util;

import io.ejangs.docsa.domain.user.entity.User;

public class SaveServiceUtil {

    public static User createUser() {
        return User.builder()
                .email("email@gmail.com")
                .name("han")
                .password("password")
                .build();
    }
}
