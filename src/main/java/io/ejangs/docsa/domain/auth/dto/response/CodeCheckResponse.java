package io.ejangs.docsa.domain.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "인증코드 검증 응답 DTO")
public record CodeCheckResponse(

        @Schema(description = "검증 완료를 나타내는 코드", example = "a1b2c3d4")
        String passCode
) {

}