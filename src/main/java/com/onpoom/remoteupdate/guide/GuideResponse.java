package com.onpoom.remoteupdate.guide;

import java.time.LocalDateTime;

/** content 는 마크다운 원문. 프런트에서 HTML 로 바꿀 때 반드시 XSS 필터링(원시 HTML 비활성 등)을 해야 한다 */
public record GuideResponse(
        Long id,
        Long projectId,
        String title,
        String content,
        boolean hasAttachment,
        String fileName,
        Long fileSize,
        Long createdById,
        String createdByName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    /** 트랜잭션 안에서 호출 (createdBy 지연 로딩) */
    static GuideResponse from(SetupGuide guide) {
        return new GuideResponse(guide.getId(), guide.getProject().getId(), guide.getTitle(), guide.getContent(),
                guide.hasAttachment(), guide.getFileName(), guide.getFileSize(), guide.getCreatedBy().getId(),
                guide.getCreatedBy().getName(), guide.getCreatedAt(), guide.getUpdatedAt());
    }
}
