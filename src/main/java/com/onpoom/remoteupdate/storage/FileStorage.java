package com.onpoom.remoteupdate.storage;

import java.io.InputStream;

import org.springframework.core.io.Resource;

/**
 * 파일 저장소 추상화. 지금은 로컬 디스크(LocalFileStorage), 나중에 S3 같은 오브젝트 스토리지로 교체할 수 있게
 * 서비스 코드는 이 인터페이스만 사용한다.
 */
public interface FileStorage {

    /** 파일을 저장하면서 크기와 SHA-256 을 계산한다. 원본 파일명 검증은 호출 전에 끝나 있어야 한다. */
    StoredFile store(FileCategory category, String originalName, InputStream content);

    /** 저장 키로 파일을 읽는다 (다운로드 스트리밍용). */
    Resource load(String key);

    /** 저장 실패 정리용. 업데이트 파일은 정책상 삭제하지 않으며, DB 저장이 실패했을 때만 사용한다. */
    void delete(String key);
}
