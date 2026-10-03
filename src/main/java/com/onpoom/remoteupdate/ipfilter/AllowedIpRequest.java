package com.onpoom.remoteupdate.ipfilter;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AllowedIpRequest(
        @NotBlank(message = "IP 또는 CIDR 을 입력하세요.")
        @Size(max = 50, message = "50자 이하로 입력하세요.")
        String ipOrCidr,

        @Size(max = 200, message = "설명은 200자 이하로 입력하세요.")
        String description
) {
}
