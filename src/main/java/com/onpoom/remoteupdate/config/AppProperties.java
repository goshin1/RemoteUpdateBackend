package com.onpoom.remoteupdate.config;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yml 의 app.* 설정 값을 타입이 있는 객체로 받는다.
 * <p>
 * 예) app.security.login.max-failures: 5  →  appProperties.security().login().maxFailures() == 5
 * <ul>
 *   <li>yml 의 케밥 표기(max-failures)가 자바의 카멜 표기(maxFailures)로 자동 연결된다</li>
 *   <li>"15m" → Duration, "zip, exe" → Set&lt;String&gt; 처럼 타입 변환도 자동</li>
 *   <li>record 라 값이 바뀌지 않고(불변), 필요한 클래스에서 생성자로 주입받아 쓴다</li>
 *   <li>@Value("${...}") 를 여기저기 흩어 쓰는 것보다 설정이 한곳에 모여 관리가 쉽다</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Storage storage,
        Upload upload,
        Security security,
        Seed seed,
        Frontend frontend
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

    /**
     * 최초 관리자 계정.
     * @param resetOnStart true 로 한 번 실행하면 이 이메일 계정을 password 로 초기화하고 ADMIN·활성 상태로 되돌린다.
     *                     관리자 비밀번호를 잊었거나 관리자 계정이 모두 잠겼을 때의 복구용. 복구 후 반드시 false 로 되돌릴 것
     */
    public record Admin(String email, String name, String password, boolean resetOnStart) {
    }

    /**
     * 운영 배포용: 빌드된 Frontend(dist 폴더)를 Backend 가 함께 제공.
     * @param distDir Frontend 의 npm run build 결과 폴더 경로. 비어 있으면 제공하지 않음 (개발 중에는 Vite 개발 서버 사용)
     */
    public record Frontend(String distDir) {
    }
}
