package com.onpoom.remoteupdate.user;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.onpoom.remoteupdate.auth.UserPrincipal;
import com.onpoom.remoteupdate.common.PageResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 사용자 관리 API.
 * <p>
 * /api/v1/admin/** 는 SecurityConfig 의 URL 규칙으로 이미 ADMIN 만 통과한다.
 * 그래도 메서드마다 @PreAuthorize 를 한 번 더 붙여, URL 규칙이 바뀌어도 권한이 빠지지 않게 했다 (이중 안전장치).
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class UserController {

    private final UserAdminService userAdminService;

    /** 사용자 목록 (검색어: 이름·이메일, 역할, 사용 여부) */
    @GetMapping("/admin/users")
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<UserResponse> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return userAdminService.search(keyword, role, enabled, page, size);
    }

    /** 사용자 등록 → 201 Created + 임시 비밀번호(한 번만 표시) */
    @PostMapping("/admin/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TemporaryPasswordResponse> create(@Valid @RequestBody UserCreateRequest request) {
        TemporaryPasswordResponse created = userAdminService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/admin/users/" + created.user().id())).body(created);
    }

    @PutMapping("/admin/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse update(@PathVariable Long id, @Valid @RequestBody UserUpdateRequest request,
            @AuthenticationPrincipal UserPrincipal admin) {
        return userAdminService.update(id, request, admin);
    }

    @PatchMapping("/admin/users/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse changeStatus(@PathVariable Long id, @Valid @RequestBody UserStatusRequest request,
            @AuthenticationPrincipal UserPrincipal admin) {
        return userAdminService.changeEnabled(id, request.enabled(), admin);
    }

    @PostMapping("/admin/users/{id}/reset-password")
    @PreAuthorize("hasRole('ADMIN')")
    public TemporaryPasswordResponse resetPassword(@PathVariable Long id) {
        return userAdminService.resetPassword(id);
    }

    /** 활성 사용자 선택 목록 (DEVELOPER 이상 — 다운로드 이력의 다운로더 필터용) */
    @GetMapping("/users/options")
    @PreAuthorize("hasRole('DEVELOPER')")
    public List<UserOptionResponse> options() {
        return userAdminService.options();
    }
}
