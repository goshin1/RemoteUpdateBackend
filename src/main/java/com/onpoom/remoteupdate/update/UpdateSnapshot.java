package com.onpoom.remoteupdate.update;

/** 변경 이력(update_history)의 before/after JSON 에 저장하는 값 */
record UpdateSnapshot(
        String version,
        String title,
        String content,
        UpdateStatus status,
        String fileName,
        long fileSize,
        String checksum
) {

    static UpdateSnapshot of(UpdateInfo info) {
        return new UpdateSnapshot(info.getVersion(), info.getTitle(), info.getContent(), info.getStatus(),
                info.getFileName(), info.getFileSize(), info.getChecksum());
    }
}
