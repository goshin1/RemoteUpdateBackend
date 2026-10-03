package com.onpoom.remoteupdate.auth;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 인증 API. 로그아웃(POST /api/v1/auth/logout)은 SecurityConfig 의 logout 설정이 처리한다.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final SessionAuthenticator sessionAuthenticator;

    /**
     * CSRF 토큰 쿠키 발급. 호출하면 XSRF-TOKEN 쿠키가 내려가고,
     * 이후 POST/PUT/PATCH/DELETE 요청은 X-XSRF-TOKEN 헤더에 그 쿠키 값을 담아야 한다 (axios 가 자동 처리).
     * <p>
     * 본문으로 토큰을 주지 않는 이유: 여기서 얻는 CsrfToken 값은 BREACH 공격 방지용으로 마스킹된 값이라
     * 헤더 검증(쿠키 원본 값과 비교)에 쓸 수 없다. 반드시 쿠키 값을 사용해야 한다.
     */
    @GetMapping("/csrf")
    public ResponseEntity<Void> csrf(CsrfToken csrfToken) {
        csrfToken.getToken(); // 지연 생성된 토큰을 실제로 만들어 쿠키로 저장시킴
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/login")
    public MeResponse login(@Valid @RequestBody LoginRequest body,
            HttpServletRequest request, HttpServletResponse response) {
        UserPrincipal principal = authService.authenticate(body);
        sessionAuthenticator.signIn(principal, request, response);
        return authService.me(principal.id());
    }

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal UserPrincipal principal) {
        return authService.me(principal.id());
    }

    @PutMapping("/password")
    public MeResponse changePassword(@AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody PasswordChangeRequest body,
            HttpServletRequest request, HttpServletResponse response) {
        UserPrincipal updated = authService.changePassword(principal.id(), body);
        sessionAuthenticator.refresh(updated, request, response);
        return authService.me(updated.id());
    }
}
