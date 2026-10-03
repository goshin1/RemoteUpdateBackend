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

import com.onpoom.remoteupdate.common.web.ClientIpResolver;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 인증 API. 로그아웃(POST /api/v1/auth/logout)은 SecurityConfig 의 logout 설정이 처리한다.
 * <ul>
 *   <li>@RestController = @Controller + @ResponseBody: 메서드가 돌려준 객체를 JSON 으로 바꿔 응답 본문에 씀</li>
 *   <li>@RequestMapping("/api/v1/auth"): 이 클래스 모든 메서드 주소의 공통 앞부분</li>
 *   <li>@RequiredArgsConstructor (Lombok): final 필드를 받는 생성자 자동 생성 → 스프링이 그 생성자로 빈을 주입(DI)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final SessionAuthenticator sessionAuthenticator;
    private final ClientIpResolver clientIpResolver;

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

    /**
     * 로그인.
     * - @RequestBody: 요청 본문 JSON 을 LoginRequest 로 변환
     * - @Valid: LoginRequest 의 @NotBlank 등 검증 실행 (실패 시 400 VALIDATION_FAILED)
     * - HttpServletRequest/Response: 세션 ID 교체와 세션 저장에 필요해서 직접 받음
     */
    @PostMapping("/login")
    public MeResponse login(@Valid @RequestBody LoginRequest body,
            HttpServletRequest request, HttpServletResponse response) {
        UserPrincipal principal = authService.authenticate(body);
        sessionAuthenticator.signIn(principal, request, response);
        return authService.me(principal.id(), clientIpResolver.resolve(request));
    }

    /** @AuthenticationPrincipal: 세션에 저장된 로그인 사용자(UserPrincipal)를 꺼내 파라미터로 넣어 줌 */
    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal UserPrincipal principal, HttpServletRequest request) {
        return authService.me(principal.id(), clientIpResolver.resolve(request));
    }

    @PutMapping("/password")
    public MeResponse changePassword(@AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody PasswordChangeRequest body,
            HttpServletRequest request, HttpServletResponse response) {
        UserPrincipal updated = authService.changePassword(principal.id(), body);
        sessionAuthenticator.refresh(updated, request, response);
        return authService.me(updated.id(), clientIpResolver.resolve(request));
    }
}
