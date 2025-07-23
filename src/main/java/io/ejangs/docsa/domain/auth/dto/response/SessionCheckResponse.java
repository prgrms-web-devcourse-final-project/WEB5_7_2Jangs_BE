package io.ejangs.docsa.domain.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "세션 유효성 확인 응답")
public record SessionCheckResponse(

        @Schema(description = "로그인 상태의 사용자 ID", example = "1")
        Long id,

        @Schema(description = "로그인 상태의 사용자 이름", example = "홍길동")
        String name
) {

}