package com.onpoom.remoteupdate.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PasswordChangeRequest(
        @NotBlank(message = "현재 비밀번호를 입력하세요.")
        String currentPassword,

        @NotBlank(message = "새 비밀번호를 입력하세요.")
        @Pattern(regexp = PasswordPolicy.REGEX, message = PasswordPolicy.MESSAGE)
        String newPassword
) {
}
