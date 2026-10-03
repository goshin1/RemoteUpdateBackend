package com.onpoom.remoteupdate.update;

import java.net.URI;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.onpoom.remoteupdate.auth.UserPrincipal;
import com.onpoom.remoteupdate.common.PageResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 조회는 로그인한 모든 사용자(직원은 ACTIVE 만), 등록·수정·상태 변경은 DEVELOPER 이상 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class UpdateController {

    private final UpdateService updateService;

    @GetMapping("/projects/{projectId}/updates")
    public PageResponse<UpdateResponse> list(@PathVariable Long projectId,
            @RequestParam(required = false) UpdateStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        return updateService.list(projectId, status, page, size, principal);
    }

    /** multipart/form-data: version, title, content, file */
    @PostMapping(value = "/projects/{projectId}/updates", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('DEVELOPER')")
    public ResponseEntity<UpdateResponse> create(@PathVariable Long projectId,
            @Valid @ModelAttribute UpdateCreateRequest request,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @AuthenticationPrincipal UserPrincipal principal) {
        UpdateResponse created = updateService.create(projectId, request, file, principal);
        return ResponseEntity.created(URI.create("/api/v1/updates/" + created.id())).body(created);
    }

    @GetMapping("/updates/{id}")
    public UpdateResponse get(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        return updateService.findOne(id, principal);
    }

    @PutMapping("/updates/{id}")
    @PreAuthorize("hasRole('DEVELOPER')")
    public UpdateResponse updateMeta(@PathVariable Long id, @Valid @RequestBody UpdateMetaRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return updateService.updateMeta(id, request, principal);
    }

    @PatchMapping("/updates/{id}/status")
    @PreAuthorize("hasRole('DEVELOPER')")
    public UpdateResponse changeStatus(@PathVariable Long id, @Valid @RequestBody UpdateStatusRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return updateService.changeStatus(id, request.status(), principal);
    }
}
