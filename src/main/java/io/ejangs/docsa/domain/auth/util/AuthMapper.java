package io.ejangs.docsa.domain.auth.util;

import io.ejangs.docsa.domain.auth.dto.response.SessionCheckResponse;
import io.ejangs.docsa.domain.user.entity.User;

public class AuthMapper {

    public static SessionCheckResponse toSessionCheckResponse(User user) {
        return new SessionCheckResponse(
                user.getId(),
                user.getName()
        );
    }
}