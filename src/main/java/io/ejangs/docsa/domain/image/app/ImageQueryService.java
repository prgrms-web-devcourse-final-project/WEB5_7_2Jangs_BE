package io.ejangs.docsa.domain.image.app;

import io.ejangs.docsa.domain.image.dao.ImageRepository;
import io.ejangs.docsa.domain.image.entity.Image;
import io.ejangs.docsa.global.exception.CustomException;
import io.ejangs.docsa.global.exception.errorcode.ImageErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ImageQueryService {

    private final ImageRepository imageRepository;

    public Image getByIdAndUserId(Long imageId, Long userId) {
        return imageRepository.findByIdAndUserId(imageId, userId)
                .orElseThrow(() -> new CustomException(ImageErrorCode.IMAGE_NOT_FOUND));
    }

}
