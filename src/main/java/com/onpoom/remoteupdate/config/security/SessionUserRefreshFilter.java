package com.onpoom.remoteupdate.config.security;

import java.io.IOException;
import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.onpoom.remoteupdate.auth.SessionAuthenticator;
import com.onpoom.remoteupdate.auth.UserPrincipal;
import com.onpoom.remoteupdate.common.error.ErrorCode;
import com.onpoom.remoteupdate.user.AppUser;
import com.onpoom.remoteupdate.user.AppUserRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

/**
 * 세션에 저장된 로그인 정보를 매 요청마다 DB 와 맞춰 주는 필터.
 *
 * <h3>왜 필요한가</h3>
 * 세션에는 로그인 "당시"의 사용자 정보(UserPrincipal: 역할, 비밀번호 변경 필요 여부 등)가 저장된다.
 * 그런데 관리자가 그 사이에
 * <ul>
 *   <li>계정을 <b>비활성화</b>하면 → 이미 로그인한 사람은 세션이 만료될 때까지(최대 30분) 계속 쓸 수 있고,</li>
 *   <li>역할을 <b>DEVELOPER → STAFF</b>로 낮추면 → 세션의 옛 역할로 개발자 기능을 계속 쓸 수 있다.</li>
 * </ul>
 * 그래서 요청마다 DB 에서 사용자를 다시 읽어
 * 비활성/삭제된 계정이면 세션을 끊고 401, 정보가 바뀌었으면 세션의 사용자 정보를 새 값으로 바꾼다.
 *
 * <h3>비용</h3>
 * 요청마다 기본키(id) 조회 1번이 추가된다. 사내 시스템 규모에서는 무시할 수준이고,
 * "권한 변경이 즉시 반영된다"는 이점이 더 크다고 판단했다.
 * (사용자가 아주 많아지면 캐시를 두거나, 변경 시 해당 사용자의 세션만 만료시키는 방식으로 바꿀 수 있다)
 *
 * <h3>위치</h3>
 * SecurityConfig 에서 권한 검사(AuthorizationFilter) "앞"에 넣는다. 그래야 바뀐 역할로 권한 검사를 한다.
 * 빈(@Component)으로 등록하지 않는 이유는 PasswordChangeRequiredFilter 주석 참고.
 */
@RequiredArgsConstructor
public class SessionUserRefreshFilter extends OncePerRequestFilter {

    private final AppUserRepository userRepository;
    private final SessionAuthenticator sessionAuthenticator;
    private final SecurityErrorWriter errorWriter;

    /** API 요청에만 적용 — 화면 파일(js, css, 이미지) 요청마다 DB 를 조회할 필요는 없다 */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(request.getContextPath() + "/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        // 로그인하지 않은 요청(익명)은 확인할 것이 없음
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            chain.doFilter(request, response);
            return;
        }

        Optional<AppUser> found = userRepository.findById(principal.id());
        if (found.isEmpty() || !found.get().isEnabled()) {
            // 비활성화된 계정: 세션을 없애고 401 → 프런트는 로그인 화면으로 이동
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            SecurityContextHolder.clearContext();
            errorWriter.write(response, ErrorCode.UNAUTHORIZED);
            return;
        }

        UserPrincipal current = UserPrincipal.from(found.get());
        if (!current.equals(principal)) {
            // 역할·이름·비밀번호 변경 필요 여부가 바뀜 → 세션 갱신 (record 의 equals 는 모든 필드를 비교)
            sessionAuthenticator.refresh(current, request, response);
        }
        chain.doFilter(request, response);
    }
}
