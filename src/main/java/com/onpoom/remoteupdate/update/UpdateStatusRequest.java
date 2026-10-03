package com.onpoom.remoteupdate.update;

import jakarta.validation.constraints.NotNull;

public record UpdateStatusRequest(
        @NotNull(message = "상태를 선택하세요. (ACTIVE 또는 DISABLED)")
        UpdateStatus status
) {
}
