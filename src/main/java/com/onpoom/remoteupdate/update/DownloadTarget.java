package com.onpoom.remoteupdate.update;

import org.springframework.core.io.Resource;

/** 다운로드할 파일 (권한 확인과 이력 기록이 끝난 상태) */
public record DownloadTarget(Resource resource, String fileName, long size, String checksum) {
}
