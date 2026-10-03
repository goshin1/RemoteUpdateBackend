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

import com.onpoom.remoteupdate.common.error.ErrorCode;

import jakarta.servlet.DispatcherType;

/**
 * 세션 + 쿠키 인증.
 * - 로그인/비밀번호 변경은 AuthController 가 처리 (JSON 요청)
 * - CSRF: XSRF-TOKEN 쿠키 → X-XSRF-TOKEN 헤더 (SPA 방식)
 * - 권한 계층: ADMIN ⊃ DEVELOPER ⊃ STAFF
 * - 세부 권한은 컨트롤러의 @PreAuthorize 로 지정
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityErrorWriter errorWriter,
            SecurityContextRepository securityContextRepository) throws Exception {
        http
                // spa(): XSRF-TOKEN 쿠키(JS에서 읽을 수 있게 HttpOnly 해제)로 내려주고 X-XSRF-TOKEN 헤더로 받음
                .csrf(csrf -> csrf.spa())
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, e) ->
                                errorWriter.write(response, ErrorCode.UNAUTHORIZED))
                        .accessDeniedHandler((request, response, e) ->
                                errorWriter.write(response,
                                        e instanceof CsrfException ? ErrorCode.CSRF_INVALID : ErrorCode.FORBIDDEN)))
                .logout(logout -> logout
                        .logoutUrl("/api/v1/auth/logout")
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
                .addFilterAfter(new PasswordChangeRequiredFilter(errorWriter), AuthorizationFilter.class);
        return http.build();
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new DelegatingSecurityContextRepository(
                new RequestAttributeSecurityContextRepository(),
                new HttpSessionSecurityContextRepository());
    }

    @Bean
    public static RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.withDefaultRolePrefix()
                .role("ADMIN").implies("DEVELOPER")
                .role("DEVELOPER").implies("STAFF")
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
