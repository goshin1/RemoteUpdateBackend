package com.onpoom.remoteupdate.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 이름·역할 수정. 이메일(로그인 ID)은 바꾸지 않는다 — 이력의 기준이 되기 때문 */
public record UserUpdateRequest(
        @NotBlank(message = "이름을 입력하세요.")
        @Size(max = 100, message = "이름은 100자 이하로 입력하세요.")
        String name,

        @NotNull(message = "역할을 선택하세요.")
        Role role
) {
}
