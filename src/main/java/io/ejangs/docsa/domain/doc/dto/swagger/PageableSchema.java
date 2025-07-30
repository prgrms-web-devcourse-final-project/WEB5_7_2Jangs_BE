package io.ejangs.docsa.domain.doc.dto.swagger;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "페이지 요청 정보")
public class PageableSchema {

    @Schema(description = "페이지 번호")
    private int pageNumber;

    @Schema(description = "페이지 크기")
    private int pageSize;

    @Schema(description = "오프셋")
    private long offset;

    @Schema(description = "페이징 여부")
    private boolean paged;

    @Schema(description = "비페이징 여부")
    private boolean unpaged;

    @Schema(description = "정렬 정보")
    private SortSchema sort;

}
