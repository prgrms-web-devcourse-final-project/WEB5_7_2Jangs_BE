package io.ejangs.docsa.global.mongo.deletion.dto;

import java.util.List;

public record MongoIdsDto(
        List<String> saveContentsIds,
        List<String> commitBlockSequenceIds,
        // Doc 삭제 시에는 Block를 전부 삭제하므로 blockIds가 필요없으나 다른 곳에서도 사용 가능 하도록 만들어 두었습니다.
        List<String> blockIds
) {

    public MongoIdsDto {
        saveContentsIds = saveContentsIds == null ? List.of() : saveContentsIds;
        commitBlockSequenceIds =
                commitBlockSequenceIds == null ? List.of() : commitBlockSequenceIds;
        blockIds = blockIds == null ? List.of() : blockIds;
    }

    public static MongoIdsDto forSaveContent(String saveContentId) {
        return new MongoIdsDto(
                List.of(saveContentId),
                List.of(),
                List.of()
        );
    }

}
