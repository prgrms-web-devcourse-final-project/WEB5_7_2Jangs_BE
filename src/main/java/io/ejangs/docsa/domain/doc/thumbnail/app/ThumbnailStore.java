package io.ejangs.docsa.domain.doc.thumbnail.app;

import io.ejangs.docsa.domain.doc.entity.Doc;
import io.ejangs.docsa.domain.doc.thumbnail.dao.ThumbnailRepository;
import io.ejangs.docsa.domain.doc.thumbnail.entity.Thumbnail;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.ThumbnailErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ThumbnailStore {

    private final ThumbnailRepository thumbnailRepository;

    public Thumbnail getByDocIdForUpdate(Long docId) {
        return thumbnailRepository.findByDocIdForUpdate(docId)
                .orElseThrow(() -> new CustomException(ThumbnailErrorCode.THUMBNAIL_NOT_FOUND));
    }

    public Thumbnail getOrCreateByDocForUpdate(Doc doc) {
        return thumbnailRepository.findByDocIdForUpdate(doc.getId())
                .orElseGet(() -> thumbnailRepository.save(Thumbnail.builder()
                        .doc(doc)
                        .build()));
    }
}
