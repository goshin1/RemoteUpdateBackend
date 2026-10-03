package com.onpoom.remoteupdate.ipfilter;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.onpoom.remoteupdate.auth.UserPrincipal;
import com.onpoom.remoteupdate.common.web.ClientIpResolver;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 허용 IP 관리 (ADMIN). /api/v1/admin/** 이므로 이 API 자체도 허용 IP 에서만 쓸 수 있다 */
@RestController
@RequestMapping("/api/v1/admin/allowed-ips")
@PreAuthorize("hasRole('ADMIN')") // 클래스에 붙이면 모든 메서드에 적용
@RequiredArgsConstructor
public class AllowedIpController {

    private final AllowedIpService allowedIpService;
    private final ClientIpResolver clientIpResolver;

    @GetMapping
    public AllowedIpOverview list(HttpServletRequest request) {
        return allowedIpService.overview(clientIpResolver.resolve(request));
    }

    /** @ResponseStatus: 정상 응답의 HTTP 상태를 지정 (ResponseEntity 를 쓰지 않을 때) */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AllowedIpResponse add(@Valid @RequestBody AllowedIpRequest body, @AuthenticationPrincipal UserPrincipal admin) {
        return allowedIpService.add(body, admin);
    }

    @PatchMapping("/{id}")
    public AllowedIpResponse changeEnabled(@PathVariable Long id, @Valid @RequestBody AllowedIpStatusRequest body,
            @AuthenticationPrincipal UserPrincipal admin, HttpServletRequest request) {
        return allowedIpService.changeEnabled(id, body.enabled(), clientIpResolver.resolve(request), admin);
    }

    /** 삭제 → 204 No Content (돌려줄 본문이 없음) */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal admin, HttpServletRequest request) {
        allowedIpService.delete(id, clientIpResolver.resolve(request), admin);
    }
}
