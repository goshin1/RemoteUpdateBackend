package com.onpoom.remoteupdate.ipfilter;

import java.util.List;

/**
 * 허용 IP 관리 화면 정보.
 * @param filterEnabled        IP 제한 기능이 켜져 있는지 (서버 설정 IP_FILTER_ENABLED)
 * @param alwaysAllowLocalhost 서버 PC 자신은 항상 허용하는지
 * @param clientIp             지금 요청한 사람의 IP (서버가 본 값)
 * @param clientAllowed        그 IP 에서 관리 기능을 쓸 수 있는지
 */
public record AllowedIpOverview(
        boolean filterEnabled,
        boolean alwaysAllowLocalhost,
        String clientIp,
        boolean clientAllowed,
        List<AllowedIpResponse> items
) {
}
