package com.onpoom.remoteupdate.common.web;

import java.util.List;

import org.springframework.stereotype.Component;

import com.onpoom.remoteupdate.config.AppProperties;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 요청한 클라이언트의 IP.
 * <p>
 * X-Forwarded-For 헤더는 누구나 위조할 수 있으므로, 요청이 "신뢰하는 프록시"(app.security.ip-filter.trusted-proxies)
 * 에서 왔을 때만 사용한다. 그 외에는 TCP 연결 상대 주소(remoteAddr)를 쓴다.
 * 다운로드 이력과 IP 제한(Phase 7)이 같은 규칙을 쓰도록 이 클래스 하나로 통일한다.
 */
@Component
public class ClientIpResolver {

    private static final int MAX_IP_LENGTH = 45;

    private final List<String> trustedProxies;

    public ClientIpResolver(AppProperties appProperties) {
        List<String> configured = appProperties.security().ipFilter().trustedProxies();
        this.trustedProxies = configured == null ? List.of() : List.copyOf(configured);
    }

    public String resolve(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        if (trustedProxies.contains(remote)) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                // "클라이언트, 프록시1, 프록시2" 형식 → 오른쪽부터 신뢰하는 프록시를 건너뛴 첫 주소
                String[] hops = forwarded.split(",");
                for (int i = hops.length - 1; i >= 0; i--) {
                    String hop = hops[i].trim();
                    if (!hop.isEmpty() && !trustedProxies.contains(hop)) {
                        return truncate(hop);
                    }
                }
            }
        }
        return truncate(remote);
    }

    private static String truncate(String ip) {
        return ip.length() > MAX_IP_LENGTH ? ip.substring(0, MAX_IP_LENGTH) : ip;
    }
}
