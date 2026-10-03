package com.onpoom.remoteupdate.config.web;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.onpoom.remoteupdate.config.AppProperties;

/**
 * 업로드 정책 조회 (DEVELOPER 이상).
 * <p>
 * 화면의 파일 선택 창에 허용 확장자를 걸고(accept), 너무 큰 파일은 올리기 전에 안내하려고 쓴다.
 * 설정값을 프런트에 따로 하드코딩하면 서버 설정과 어긋나기 쉬우므로 서버 값을 그대로 내려준다.
 * (최종 검사는 여전히 서버의 UploadValidator 와 multipart 설정이 한다)
 *
 * @param maxFileSize spring.servlet.multipart.max-file-size 값. @Value 로 설정 파일 값을 직접 주입받는 예시.
 *                    "500MB" 같은 문자열을 Spring 이 DataSize 로 변환해 준다.
 */
@RestController
public class UploadPolicyController {

    private final List<String> allowedExtensions;
    private final long maxFileSizeBytes;

    public UploadPolicyController(AppProperties appProperties,
            @Value("${spring.servlet.multipart.max-file-size}") DataSize maxFileSize) {
        this.allowedExtensions = appProperties.upload().allowedExtensions().stream().sorted().toList();
        this.maxFileSizeBytes = maxFileSize.toBytes();
    }

    public record UploadPolicyResponse(List<String> allowedExtensions, long maxFileSizeBytes) {
    }

    @GetMapping("/api/v1/config/upload")
    @PreAuthorize("hasRole('DEVELOPER')")
    public UploadPolicyResponse uploadPolicy() {
        return new UploadPolicyResponse(allowedExtensions, maxFileSizeBytes);
    }
}
