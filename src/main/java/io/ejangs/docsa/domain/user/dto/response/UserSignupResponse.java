package io.ejangs.docsa.domain.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "회원가입 응답 DTO")
public record UserSignupResponse(

        @Schema(description = "가입한 사용자 ID", example = "1")
        Long id,

        @Schema(description = "가입된 사용자 이름", example = "홍길동")
        String name
) {

}
