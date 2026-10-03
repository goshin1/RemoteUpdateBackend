package com.onpoom.remoteupdate.update;

import java.time.LocalDateTime;

public record DownloadHistoryResponse(
        Long id,
        Long projectId,
        String projectName,
        Long updateId,
        String version,
        String fileName,
        Long userId,
        String downloaderName,
        String clientIp,
        LocalDateTime downloadedAt
) {

    static DownloadHistoryResponse from(DownloadHistory h) {
        UpdateInfo update = h.getUpdate();
        return new DownloadHistoryResponse(h.getId(), update.getProject().getId(), update.getProject().getName(),
                update.getId(), update.getVersion(), update.getFileName(), h.getUser().getId(),
                h.getDownloaderName(), h.getClientIp(), h.getDownloadedAt());
    }
}
