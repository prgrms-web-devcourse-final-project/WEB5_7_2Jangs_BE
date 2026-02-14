package io.ejangs.docsa.domain.doc.dto.swagger;

import io.ejangs.docsa.domain.doc.dto.response.DocSimplePageResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "문서 목록의 페이지 응답")
public class PageDocListSimpleResponse {

    @Schema(description = "문서 목록")
    private List<DocSimplePageResponse> content;

    @Schema(description = "페이지 정보")
    private PageableSchema pageable;

    @Schema(description = "마지막 페이지 여부")
    private boolean last;

    @Schema(description = "총 페이지 수")
    private int totalPages;

    @Schema(description = "총 요소 개수")
    private long totalElements;

    @Schema(description = "첫 번째 페이지 여부")
    private boolean first;

    @Schema(description = "현재 페이지 크기")
    private int size;

    @Schema(description = "현재 페이지 번호")
    private int number;

    @Schema(description = "정렬 정보")
    private SortSchema sort;

    @Schema(description = "현재 페이지 요소 개수")
    private int numberOfElements;

    @Schema(description = "페이지가 비어있는지 여부")
    private boolean empty;

}
