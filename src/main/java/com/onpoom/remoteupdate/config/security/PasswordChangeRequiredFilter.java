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
 * 빈으로 등록하지 않는다 (서블릿 필터로 중복 등록되는 것을 막기 위해 SecurityConfig 에서 직접 생성).
 */
@RequiredArgsConstructor
public class PasswordChangeRequiredFilter extends OncePerRequestFilter {

    private static final String AUTH_PATH_PREFIX = "/api/v1/auth/";

    private final SecurityErrorWriter errorWriter;

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
