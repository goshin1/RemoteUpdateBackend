package com.onpoom.remoteupdate.ipfilter;

import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;

import com.onpoom.remoteupdate.config.AppProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * "이 IP 에서 관리 기능을 써도 되는가" 판단.
 *
 * <h3>CIDR 표기</h3>
 * 192.168.0.0/24 = 앞 24비트(192.168.0)가 같은 모든 주소 → 192.168.0.0 ~ 192.168.0.255 (256개).
 * 사무실처럼 IP 가 범위로 바뀌는 곳은 단일 IP 대신 CIDR 로 등록한다.
 * Spring Security 의 IpAddressMatcher 가 단일 IP·CIDR, IPv4·IPv6 를 모두 처리한다.
 *
 * <h3>판단 순서</h3>
 * 1. 기능이 꺼져 있으면 항상 허용
 * 2. 서버 PC 자신(127.0.0.1, ::1)이면 허용 (설정으로 끌 수 있음) — 허용 IP 를 실수로 다 지워도 서버 PC 에서 복구 가능
 * 3. 사용 중인 허용 IP 중 하나라도 맞으면 허용
 */
@Slf4j
@Component
public class IpAccessPolicy {

    /** IPv4: 점으로 구분된 숫자 4개 */
    private static final Pattern IPV4 = Pattern.compile("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$");
    /** IPv6: 16진수와 콜론 (점은 ::ffff:1.2.3.4 같은 표기용) */
    private static final Pattern IPV6_CHARS = Pattern.compile("^[0-9a-fA-F:.]+$");

    private static final List<IpAddressMatcher> LOOPBACK = List.of(
            new IpAddressMatcher("127.0.0.0/8"), new IpAddressMatcher("::1"));

    private final AllowedIpRepository allowedIpRepository;
    private final boolean enabled;
    private final boolean alwaysAllowLocalhost;

    public IpAccessPolicy(AllowedIpRepository allowedIpRepository, AppProperties appProperties) {
        this.allowedIpRepository = allowedIpRepository;
        AppProperties.IpFilter config = appProperties.security().ipFilter();
        this.enabled = config.enabled();
        this.alwaysAllowLocalhost = config.alwaysAllowLocalhost();
        log.info("관리 기능 IP 제한: {}", enabled ? "사용" : "사용 안 함");
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isAlwaysAllowLocalhost() {
        return alwaysAllowLocalhost;
    }

    /** 이 IP 에서 관리 기능 사용 가능 여부 (DB 의 현재 허용 목록 기준) */
    public boolean isManagementAllowed(String clientIp) {
        if (!enabled) {
            return true;
        }
        return isAllowedBy(clientIp, allowedIpRepository.findByEnabledTrue().stream().map(AllowedIp::getIpOrCidr).toList());
    }

    /**
     * 주어진 목록 기준으로 판단 (허용 IP 를 끄거나 지우기 "전에" 결과를 미리 계산할 때 사용).
     */
    public boolean isAllowedBy(String clientIp, Collection<String> ipOrCidrs) {
        if (!enabled) {
            return true;
        }
        if (!isIpLiteral(clientIp)) {
            return false;
        }
        if (alwaysAllowLocalhost && LOOPBACK.stream().anyMatch(m -> m.matches(clientIp))) {
            return true;
        }
        for (String entry : ipOrCidrs) {
            try {
                if (new IpAddressMatcher(entry).matches(clientIp)) {
                    return true;
                }
            } catch (IllegalArgumentException e) {
                log.warn("허용 IP 형식 오류로 건너뜀: {}", entry);
            }
        }
        return false;
    }

    /**
     * 글자 모양만 보고 IP 주소인지 판단.
     * 왜 필요한가: IpAddressMatcher 는 내부에서 InetAddress.getByName() 을 쓰는데, "999.1.1.1" 이나 "office" 처럼
     * IP 가 아닌 문자열을 넘기면 호스트 이름으로 보고 **DNS 조회**를 시도한다 (느리고, 외부 DNS 결과에 좌우됨).
     * 그래서 넘기기 전에 IPv4 는 숫자 범위(0~255)까지, IPv6 는 쓰이는 글자만 미리 확인한다.
     */
    static boolean isIpLiteral(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        if (value.contains(":")) {
            return IPV6_CHARS.matcher(value).matches(); // 콜론이 있으면 InetAddress 가 DNS 없이 IPv6 로만 해석
        }
        var m = IPV4.matcher(value);
        if (!m.matches()) {
            return false;
        }
        for (int i = 1; i <= 4; i++) {
            if (Integer.parseInt(m.group(i)) > 255) {
                return false;
            }
        }
        return true;
    }

    /** 등록 전 형식 검사. 올바르면 true */
    public static boolean isValidIpOrCidr(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String ipPart = value.contains("/") ? value.substring(0, value.indexOf('/')) : value;
        if (!isIpLiteral(ipPart)) {
            return false;
        }
        try {
            new IpAddressMatcher(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
