package com.onpoom.remoteupdate.user;

import jakarta.validation.constraints.NotNull;

public record UserStatusRequest(
        @NotNull(message = "사용 여부를 선택하세요.")
        Boolean enabled
) {
}
