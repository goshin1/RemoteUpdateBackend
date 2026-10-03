package com.onpoom.remoteupdate.update;

import java.time.LocalDateTime;

/** 업데이트 응답. 서버 저장 키(fileKey)는 내보내지 않는다. */
public record UpdateResponse(
        Long id,
        Long projectId,
        String version,
        String title,
        String content,
        UpdateStatus status,
        String fileName,
        long fileSize,
        String checksum,
        Long developerId,
        String developerName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    /** 트랜잭션 안에서 호출해야 한다 (developer 지연 로딩) */
    public static UpdateResponse from(UpdateInfo info) {
        return new UpdateResponse(info.getId(), info.getProject().getId(), info.getVersion(), info.getTitle(),
                info.getContent(), info.getStatus(), info.getFileName(), info.getFileSize(), info.getChecksum(),
                info.getDeveloper().getId(), info.getDeveloper().getName(), info.getCreatedAt(), info.getUpdatedAt());
    }
}
