package com.onpoom.remoteupdate.config;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yml 의 app.* 설정 값.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Storage storage,
        Upload upload,
        Security security,
        Seed seed
) {

    /** 업로드 파일 저장소 */
    public record Storage(String root) {
    }

    /** 업로드 검증 (확장자는 소문자, 점 없이) */
    public record Upload(Set<String> allowedExtensions) {
    }

    public record Security(Login login, IpFilter ipFilter) {
    }

    /** 로그인 실패 잠금 기준 */
    public record Login(int maxFailures, Duration lockDuration) {
    }

    /** 개발자/관리자 기능 IP 제한 (Phase 7) */
    public record IpFilter(boolean enabled, List<String> trustedProxies) {
    }

    public record Seed(Admin admin) {
    }

    /** 최초 관리자 계정 */
    public record Admin(String email, String name, String password) {
    }
}
