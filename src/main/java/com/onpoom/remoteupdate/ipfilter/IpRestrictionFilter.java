package com.onpoom.remoteupdate.ipfilter;

import java.io.IOException;
import java.util.Set;

import org.springframework.web.filter.OncePerRequestFilter;

import com.onpoom.remoteupdate.common.error.ErrorCode;
import com.onpoom.remoteupdate.common.web.ClientIpResolver;
import com.onpoom.remoteupdate.config.security.SecurityErrorWriter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 관리 기능 IP 제한 필터 (기획서 결정 #4, 범위는 "관리 기능 API 만").
 *
 * <h3>제한 대상</h3>
 * <ul>
 *   <li>/api/v1/admin/** — 사용자·허용 IP 관리 (조회 포함)</li>
 *   <li>데이터를 바꾸는 요청(POST/PUT/PATCH/DELETE) — 업데이트·가이드·프로젝트 등록·수정·상태 변경</li>
 * </ul>
 * <h3>제한하지 않는 것</h3>
 * <ul>
 *   <li>/api/v1/auth/** — 로그인·로그아웃·비밀번호 변경은 어디서나 (현장 직원도 써야 하므로)</li>
 *   <li>그 밖의 GET — 목록·상세·다운로드·이력 조회</li>
 * </ul>
 * 현장 직원은 조회·다운로드만 하므로 IP 가 바뀌어도 영향이 없고, 개발자는 밖에서도 조회는 할 수 있다.
 *
 * <h3>위치</h3>
 * 권한 검사(AuthorizationFilter) 뒤 — 로그인 안 한 요청은 먼저 401 을 받게 하고, 권한이 있는 요청만 IP 를 본다.
 */
@Slf4j
@RequiredArgsConstructor
public class IpRestrictionFilter extends OncePerRequestFilter {

    private static final Set<String> READ_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    private final IpAccessPolicy policy;
    private final ClientIpResolver clientIpResolver;
    private final SecurityErrorWriter errorWriter;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !policy.isEnabled() || !isRestricted(request);
    }

    /** 제한 대상 요청인지 */
    static boolean isRestricted(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (!path.startsWith("/api/")) {
            return false;
        }
        if (path.startsWith("/api/v1/admin/")) {
            return true;
        }
        return !READ_METHODS.contains(request.getMethod()) && !path.startsWith("/api/v1/auth/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String clientIp = clientIpResolver.resolve(request);
        if (!policy.isManagementAllowed(clientIp)) {
            log.warn("허용되지 않은 IP 의 관리 요청 차단: ip={}, {} {}", clientIp, request.getMethod(), request.getRequestURI());
            errorWriter.write(response, ErrorCode.IP_NOT_ALLOWED);
            return;
        }
        chain.doFilter(request, response);
    }
}
