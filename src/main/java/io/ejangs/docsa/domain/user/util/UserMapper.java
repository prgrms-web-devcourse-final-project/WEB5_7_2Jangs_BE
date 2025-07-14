package io.ejangs.docsa.domain.user.util;

import io.ejangs.docsa.domain.user.dto.request.UserSignupRequest;
import io.ejangs.docsa.domain.user.dto.response.UserSignupResponse;
import io.ejangs.docsa.domain.user.entity.User;

public class UserMapper {

    public static User toEntity(UserSignupRequest request, String encodedPassword) {
        return User.builder()
                .name(request.name())
                .email(request.email())
                .password(encodedPassword)
                .build();
    }

    public static UserSignupResponse toSignupResponse(User user) {
        return new UserSignupResponse(user.getId(), user.getName());
    }
}