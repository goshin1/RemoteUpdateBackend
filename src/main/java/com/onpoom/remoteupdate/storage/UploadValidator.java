package com.onpoom.remoteupdate.storage;

import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.onpoom.remoteupdate.common.error.ApiException;
import com.onpoom.remoteupdate.common.error.ErrorCode;
import com.onpoom.remoteupdate.config.AppProperties;

/**
 * 업로드 파일 검증. 크기 제한은 spring.servlet.multipart.max-file-size 가 먼저 처리한다 (초과 시 413).
 */
@Component
public class UploadValidator {

    private static final int MAX_NAME_LENGTH = 255;

    private final Set<String> allowedExtensions;

    public UploadValidator(AppProperties appProperties) {
        this.allowedExtensions = appProperties.upload().allowedExtensions().stream()
                .map(ext -> ext.trim().toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /** 검증 후 경로가 제거된 원본 파일명을 돌려준다. */
    public String validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(ErrorCode.FILE_REQUIRED);
        }
        String name = sanitizeFileName(file.getOriginalFilename());
        String extension = extensionOf(name);
        if (extension.isEmpty() || !allowedExtensions.contains(extension)) {
            throw new ApiException(ErrorCode.FILE_EXTENSION_NOT_ALLOWED,
                    "허용되지 않은 파일 형식입니다. 허용: " + String.join(", ", allowedExtensions.stream().sorted().toList()));
        }
        return name;
    }

    /**
     * 브라우저에 따라 원본명에 경로("C:\fakepath\a.zip")가 붙어 오므로 마지막 부분만 사용.
     * 제어 문자는 거부한다 (다운로드 응답 헤더에 그대로 들어가므로).
     */
    static String sanitizeFileName(String original) {
        if (original == null) {
            throw new ApiException(ErrorCode.FILE_NAME_INVALID);
        }
        String name = original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).trim();
        if (name.isEmpty() || name.equals(".") || name.equals("..") || name.length() > MAX_NAME_LENGTH
                || name.chars().anyMatch(Character::isISOControl)) {
            throw new ApiException(ErrorCode.FILE_NAME_INVALID);
        }
        return name;
    }

    static String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot <= 0 || dot == name.length() - 1 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
