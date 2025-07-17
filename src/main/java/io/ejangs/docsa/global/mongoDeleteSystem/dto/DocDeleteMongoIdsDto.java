package io.ejangs.docsa.global.mongoDeleteSystem.dto;

import java.util.List;

public record DocDeleteMongoIdsDto(
        List<String> saveContentsIds,
        List<String> commitBlockSequenceIds,
        // Doc 삭제 시에는 Block를 전부 삭제하므로 blockIds가 필요없으나 다른 곳에서도 사용 가능 하도록 만들어 두었습니다.
        List<String> blockIds
) {

}
