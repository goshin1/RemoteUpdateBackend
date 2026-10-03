package com.onpoom.remoteupdate.ipfilter;

import jakarta.validation.constraints.NotNull;

public record AllowedIpStatusRequest(@NotNull(message = "사용 여부를 선택하세요.") Boolean enabled) {
}
