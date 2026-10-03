package com.onpoom.remoteupdate.ipfilter;

import java.time.LocalDateTime;

public record AllowedIpResponse(
        Long id,
        String ipOrCidr,
        String description,
        boolean enabled,
        String createdByName,
        LocalDateTime createdAt
) {

    static AllowedIpResponse from(AllowedIp ip) {
        return new AllowedIpResponse(ip.getId(), ip.getIpOrCidr(), ip.getDescription(), ip.isEnabled(),
                ip.getCreatedBy() == null ? null : ip.getCreatedBy().getName(), ip.getCreatedAt());
    }
}
