package io.ejangs.docsa.domain.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "로그인 응답")
public record UserLoginResponse(

        @Schema(description = "로그인한 사용자 ID", example = "1")
        Long id
) {

}
