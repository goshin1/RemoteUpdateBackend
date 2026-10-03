package com.onpoom.remoteupdate.config.security;

import java.io.IOException;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.onpoom.remoteupdate.auth.UserPrincipal;
import com.onpoom.remoteupdate.common.error.ErrorCode;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * 임시 비밀번호 상태(mustChangePassword)인 사용자는 인증 API(/api/v1/auth/**) 외에는 사용할 수 없게 막는다.
 * <ul>
 *   <li>OncePerRequestFilter: 한 요청에 정확히 한 번만 실행되는 필터의 기본 클래스 (내부 포워드 때 중복 실행 방지)</li>
 *   <li>chain.doFilter(...) 를 호출하면 다음 필터로 넘어가고, 호출하지 않고 return 하면 요청이 여기서 끝난다</li>
 *   <li>@Component 로 등록하지 않는다: 스프링 부트는 Filter 타입 빈을 서블릿 필터로 "자동 등록"하므로,
 *       빈으로 만들면 보안 필터 체인 밖에서 한 번 더 실행된다. 그래서 SecurityConfig 에서 new 로 만들어 체인에만 넣는다</li>
 * </ul>
 */
@RequiredArgsConstructor
public class PasswordChangeRequiredFilter extends OncePerRequestFilter {

    private static final String API_PATH_PREFIX = "/api/";
    private static final String AUTH_PATH_PREFIX = "/api/v1/auth/";

    private final SecurityErrorWriter errorWriter;

    /**
     * API 요청에만 적용한다.
     * 화면 파일(js, css)까지 막으면 비밀번호 변경 "화면"조차 불러오지 못한다 (운영 배포 E2E 에서 실제로 발견한 버그).
     * shouldNotFilter 가 true 를 돌려주면 이 필터를 건너뛴다.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(request.getContextPath() + API_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.getPrincipal() instanceof UserPrincipal principal
                && principal.mustChangePassword()
                && !request.getRequestURI().startsWith(request.getContextPath() + AUTH_PATH_PREFIX)) {
            errorWriter.write(response, ErrorCode.PASSWORD_CHANGE_REQUIRED);
            return;
        }
        chain.doFilter(request, response);
    }
}
