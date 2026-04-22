package io.ejangs.docsa.domain.commit.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

public record CreateCommitRequest(
        @NotBlank(message = "기록 제목을 입력해주세요.")
        @Size(min = 1, max = 30, message = "기록 제목은 30자를 초과 할 수 없습니다.")
        String title,
        @Size(max = 100, message = "기록에 대한 설명은 100자를 초과 할 수 없습니다.")
        String description,
        Long branchId,
        List<Map<String, Object>> blocks,
        List<String> blockOrders
) {


}