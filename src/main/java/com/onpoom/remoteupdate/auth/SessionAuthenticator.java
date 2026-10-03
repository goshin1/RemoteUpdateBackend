package com.onpoom.remoteupdate.auth;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/** 로그인 사용자 정보를 SecurityContext 에 넣고 세션에 저장 */
@Component
@RequiredArgsConstructor
public class SessionAuthenticator {

    private final SecurityContextRepository securityContextRepository;
    private final SecurityContextHolderStrategy holderStrategy = SecurityContextHolder.getContextHolderStrategy();

    /** 로그인: 세션 고정 공격 방지를 위해 세션 ID를 바꾼 뒤 저장 */
    public void signIn(UserPrincipal principal, HttpServletRequest request, HttpServletResponse response) {
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        save(principal, request, response);
    }

    /** 비밀번호 변경 등으로 사용자 정보가 바뀌었을 때 세션 갱신 */
    public void refresh(UserPrincipal principal, HttpServletRequest request, HttpServletResponse response) {
        save(principal, request, response);
    }

    private void save(UserPrincipal principal, HttpServletRequest request, HttpServletResponse response) {
        var authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null,
                principal.authorities());
        SecurityContext context = holderStrategy.createEmptyContext();
        context.setAuthentication(authentication);
        holderStrategy.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }
}
