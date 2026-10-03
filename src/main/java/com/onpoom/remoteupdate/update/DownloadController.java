package com.onpoom.remoteupdate.update;

import java.time.LocalDate;

import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.onpoom.remoteupdate.auth.UserPrincipal;
import com.onpoom.remoteupdate.common.PageResponse;
import com.onpoom.remoteupdate.common.web.ClientIpResolver;
import com.onpoom.remoteupdate.common.web.FileResponses;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class DownloadController {

    private final DownloadService downloadService;
    private final ClientIpResolver clientIpResolver;

    /**
     * 업데이트 파일 다운로드 (로그인한 모든 사용자). 다운로드 이력이 자동으로 남는다.
     * 프런트에서는 axios 가 아니라 &lt;a href&gt; 링크로 호출한다 (대용량 파일을 브라우저 메모리에 올리지 않기 위해).
     * 응답 헤더 X-Checksum-SHA256 로 체크섬을 함께 내려준다.
     */
    @GetMapping("/updates/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest request) {
        DownloadTarget target = downloadService.prepare(id, principal, clientIpResolver.resolve(request));
        return FileResponses.attachmentHeaders(target.fileName(), target.size())
                .header("X-Checksum-SHA256", target.checksum())
                .body(target.resource());
    }

    /** 다운로드 이력 조회 (DEVELOPER 이상). from/to 는 yyyy-MM-dd */
    @GetMapping("/downloads")
    @PreAuthorize("hasRole('DEVELOPER')")
    public PageResponse<DownloadHistoryResponse> search(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long updateId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return downloadService.search(projectId, updateId, userId, from, to, page, size);
    }
}
