package com.onpoom.remoteupdate.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import com.onpoom.remoteupdate.common.error.ApiException;
import com.onpoom.remoteupdate.common.error.ErrorCode;
import com.onpoom.remoteupdate.config.AppProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * 로컬 디스크 저장소.
 * <p>
 * 저장 키 형식: {category}/{yyyy}/{MM}/{UUID}  (예: updates/2026/10/3f2a...)
 * - 원본 파일명을 경로에 쓰지 않으므로 파일명에 "../" 를 넣는 경로 조작 공격이 불가능하다.
 * - 확장자도 붙이지 않는다. 서버에서 실행·해석될 여지를 없애고, 원본명은 DB 에 따로 보관한다.
 */
@Slf4j
@Component
public class LocalFileStorage implements FileStorage {

    private final Path root;

    public LocalFileStorage(AppProperties appProperties) {
        this.root = Path.of(appProperties.storage().root()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("파일 저장소 폴더를 만들 수 없습니다: " + root, e);
        }
        log.info("파일 저장소: {}", root);
    }

    @Override
    public StoredFile store(FileCategory category, String originalName, InputStream content) {
        LocalDate today = LocalDate.now();
        String key = String.format("%s/%d/%02d/%s", category.directory(), today.getYear(), today.getMonthValue(),
                UUID.randomUUID());
        Path target = resolve(key);
        Path temp = null;
        try {
            Files.createDirectories(target.getParent());
            // 임시 파일에 다 쓴 뒤 이동: 쓰는 도중 실패해도 반쯤 쓴 파일이 남지 않게
            temp = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long size;
            try (InputStream in = new DigestInputStream(content, digest);
                 OutputStream out = Files.newOutputStream(temp)) {
                size = in.transferTo(out);
            }
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
            return new StoredFile(key, originalName, size, HexFormat.of().formatHex(digest.digest()));
        } catch (IOException | NoSuchAlgorithmException e) {
            log.error("파일 저장 실패: key={}", key, e);
            throw new ApiException(ErrorCode.FILE_STORAGE_ERROR);
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                    // 임시 파일 정리 실패는 무시
                }
            }
        }
    }

    @Override
    public Resource load(String key) {
        Path path = resolve(key);
        if (!Files.isRegularFile(path)) {
            log.error("저장 파일이 없습니다: key={}", key);
            throw new ApiException(ErrorCode.FILE_STORAGE_ERROR, "저장된 파일을 찾을 수 없습니다. 관리자에게 문의하세요.");
        }
        return new FileSystemResource(path);
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            log.warn("파일 삭제 실패: key={}", key, e);
        }
    }

    /** 키를 실제 경로로 바꾸면서 저장소 루트 밖을 가리키지 않는지 확인 */
    private Path resolve(String key) {
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("저장소 밖 경로입니다: " + key);
        }
        return path;
    }
}
