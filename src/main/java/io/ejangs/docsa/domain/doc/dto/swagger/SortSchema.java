package io.ejangs.docsa.domain.doc.dto.swagger;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "정렬 정보")
public class SortSchema {

    @Schema(description = "정렬이 비어있는지 여부")
    private boolean empty;

    @Schema(description = "정렬되었는지 여부")
    private boolean sorted;

    @Schema(description = "정렬되지 않았는지 여부")
    private boolean unsorted;

}
