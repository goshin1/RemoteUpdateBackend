package com.onpoom.remoteupdate.common.web;

import java.nio.charset.StandardCharsets;

import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** 파일 다운로드 응답 (브라우저가 저장 대화상자를 띄우도록 attachment 로 내려준다) */
public final class FileResponses {

    private FileResponses() {
    }

    /**
     * @param fileName 원본 파일명. 한글 파일명도 깨지지 않게 RFC 5987 (filename*=UTF-8'') 형식으로 내려간다.
     */
    public static ResponseEntity<Resource> attachment(Resource resource, String fileName, long size) {
        return attachmentHeaders(fileName, size).body(resource);
    }

    /** 헤더를 더 붙이고 싶을 때 사용 (예: 체크섬) */
    public static ResponseEntity.BodyBuilder attachmentHeaders(String fileName, long size) {
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(fileName, StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(size);
    }
}
