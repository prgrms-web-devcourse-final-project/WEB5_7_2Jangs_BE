package io.ejangs.docsa.global.outbox.s3.dto;

public record S3DeleteTarget(
        Long imageId,
        String objectKey
) {
}
