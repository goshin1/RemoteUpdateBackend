package com.onpoom.remoteupdate.guide;

import java.net.URI;
import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.onpoom.remoteupdate.auth.UserPrincipal;
import com.onpoom.remoteupdate.common.web.FileResponses;
import com.onpoom.remoteupdate.update.DownloadTarget;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 조회·첨부 다운로드는 로그인한 모든 사용자, 등록·수정은 DEVELOPER 이상 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class GuideController {

    private final GuideService guideService;

    @GetMapping("/projects/{projectId}/guides")
    public List<GuideResponse> list(@PathVariable Long projectId) {
        return guideService.list(projectId);
    }

    /** multipart/form-data: title, content, file(선택) */
    @PostMapping(value = "/projects/{projectId}/guides", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('DEVELOPER')")
    public ResponseEntity<GuideResponse> create(@PathVariable Long projectId,
            @Valid @ModelAttribute GuideRequest request,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @AuthenticationPrincipal UserPrincipal principal) {
        GuideResponse created = guideService.create(projectId, request, file, principal);
        return ResponseEntity.created(URI.create("/api/v1/guides/" + created.id())).body(created);
    }

    @GetMapping("/guides/{id}")
    public GuideResponse get(@PathVariable Long id) {
        return guideService.findOne(id);
    }

    /** 제목·본문 수정 (JSON) */
    @PutMapping("/guides/{id}")
    @PreAuthorize("hasRole('DEVELOPER')")
    public GuideResponse update(@PathVariable Long id, @Valid @RequestBody GuideRequest request) {
        return guideService.update(id, request);
    }

    /** 첨부 파일 추가·교체 (multipart: file) */
    @PostMapping(value = "/guides/{id}/attachment", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('DEVELOPER')")
    public GuideResponse replaceAttachment(@PathVariable Long id,
            @RequestParam(value = "file", required = false) MultipartFile file) {
        return guideService.replaceAttachment(id, file);
    }

    @GetMapping("/guides/{id}/attachment")
    public ResponseEntity<Resource> downloadAttachment(@PathVariable Long id) {
        DownloadTarget target = guideService.attachment(id);
        return FileResponses.attachment(target.resource(), target.fileName(), target.size());
    }
}
