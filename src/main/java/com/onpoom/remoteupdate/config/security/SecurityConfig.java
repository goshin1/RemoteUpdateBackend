package com.onpoom.remoteupdate.config.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfException;

import com.onpoom.remoteupdate.auth.SessionAuthenticator;
import com.onpoom.remoteupdate.common.error.ErrorCode;
import com.onpoom.remoteupdate.user.AppUserRepository;

import jakarta.servlet.DispatcherType;

/**
 * Spring Security 설정 — 요청이 Controller 에 닿기 전에 지나가는 "필터 체인"을 정의한다.
 * (전체 흐름 그림은 docs/security-guide.md 1번)
 *
 * <ul>
 *   <li>@Configuration: 이 클래스의 @Bean 메서드들이 만드는 객체를 스프링 빈으로 등록</li>
 *   <li>@EnableWebSecurity: 웹 보안(필터 체인) 활성화</li>
 *   <li>@EnableMethodSecurity: 컨트롤러 메서드의 @PreAuthorize("hasRole('ADMIN')") 같은 권한 검사 활성화</li>
 * </ul>
 *
 * 필터 순서 (요약)
 * <pre>
 * 세션에서 로그인 정보 복원 → CSRF 검사 → 로그아웃 → [SessionUserRefreshFilter] → 권한 검사(AuthorizationFilter)
 *   → [PasswordChangeRequiredFilter] → Controller
 * </pre>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityErrorWriter errorWriter,
            SecurityContextRepository securityContextRepository, AppUserRepository userRepository,
            SessionAuthenticator sessionAuthenticator) throws Exception {
        http
                // CSRF: 상태 변경 요청(POST/PUT/PATCH/DELETE)은 XSRF-TOKEN 쿠키 값을 X-XSRF-TOKEN 헤더로 다시 보내야 통과.
                // spa() = 쿠키 저장소(JS 에서 읽을 수 있게 HttpOnly 해제) + SPA 용 토큰 처리기. GET 은 검사하지 않음
                .csrf(csrf -> csrf.spa())

                // 로그인 정보(SecurityContext)를 어디에 저장·복원할지. AuthController 의 로그인도 같은 저장소를 써야 함
                .securityContext(context -> context.securityContextRepository(securityContextRepository))

                // 기본 제공 로그인 폼 / 브라우저 팝업(Basic 인증)은 쓰지 않음 — 로그인은 JSON API(AuthController)로
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                // 로그인 전 요청을 저장했다가 로그인 후 되돌려 주는 기능 — SPA 에서는 프런트 라우터가 대신 하므로 끔
                .requestCache(AbstractHttpConfigurer::disable)

                // URL 단위 권한 규칙. 위에서부터 차례로 검사하고 처음 맞는 규칙이 적용된다
                .authorizeHttpRequests(auth -> auth
                        // 오류 응답을 만드는 내부 포워드(/error)는 막지 않음
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        // 로그인 전에 필요한 두 API 만 공개
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        // 관리자 API
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        // 나머지 API 는 로그인만 필요 (세부 역할은 각 메서드의 @PreAuthorize)
                        .requestMatchers("/api/**").authenticated()
                        // 화면 파일(index.html, js, css)은 누구나 받을 수 있어야 로그인 화면을 띄울 수 있음 (FrontendConfig)
                        // 화면 파일에는 데이터가 없고, 데이터는 전부 위의 /api 규칙으로 보호된다
                        .requestMatchers(HttpMethod.GET, "/**").permitAll()
                        // 그 외(API 가 아닌 POST 등)는 거부
                        .anyRequest().denyAll())

                // 필터 단계에서 거부될 때의 응답 형식을 { code, message } 로 통일
                .exceptionHandling(ex -> ex
                        // 로그인 안 함 → 401
                        .authenticationEntryPoint((request, response, e) ->
                                errorWriter.write(response, ErrorCode.UNAUTHORIZED))
                        // 로그인은 했지만 권한 없음 or CSRF 토큰 불일치 → 403
                        .accessDeniedHandler((request, response, e) ->
                                errorWriter.write(response,
                                        e instanceof CsrfException ? ErrorCode.CSRF_INVALID : ErrorCode.FORBIDDEN)))

                // 로그아웃: POST /api/v1/auth/logout → 세션 무효화 + 세션 쿠키 삭제 + 204 No Content
                .logout(logout -> logout
                        .logoutUrl("/api/v1/auth/logout")
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))

                // 우리가 만든 필터 끼워 넣기
                // 1) 권한 검사 "전": 세션의 사용자 정보를 DB 와 맞춤 (비활성화·역할 변경 즉시 반영)
                .addFilterBefore(new SessionUserRefreshFilter(userRepository, sessionAuthenticator, errorWriter),
                        AuthorizationFilter.class)
                // 2) 권한 검사 "후": 임시 비밀번호 상태면 인증 API 외 차단
                .addFilterAfter(new PasswordChangeRequiredFilter(errorWriter), AuthorizationFilter.class);
        return http.build();
    }

    /**
     * 로그인 정보 저장소.
     * - RequestAttribute: 같은 요청 안에서 공유
     * - HttpSession: 다음 요청에서도 복원 (세션 쿠키 JSESSIONID 로 찾아옴)
     */
    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new DelegatingSecurityContextRepository(
                new RequestAttributeSecurityContextRepository(),
                new HttpSessionSecurityContextRepository());
    }

    /**
     * 역할 계층: ADMIN 은 DEVELOPER 권한을, DEVELOPER 는 STAFF 권한을 포함.
     * 덕분에 "개발자 이상"을 hasRole('DEVELOPER') 하나로 쓸 수 있다.
     * static 인 이유: 메서드 보안(@PreAuthorize) 설정이 다른 빈보다 먼저 이 빈을 찾기 때문 (스프링 권장 방식)
     */
    @Bean
    public static RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.withDefaultRolePrefix()
                .role("ADMIN").implies("DEVELOPER")
                .role("DEVELOPER").implies("STAFF")
                .build();
    }

    /** 비밀번호 해시 방식: BCrypt (무작위 salt + 일부러 느린 계산 → docs/security-guide.md 6번) */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
