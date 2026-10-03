package com.onpoom.remoteupdate.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 관리자의 사용자 등록 요청.
 * 비밀번호는 받지 않는다 — 서버가 임시 비밀번호를 만들어 응답으로 한 번만 돌려준다.
 */
public record UserCreateRequest(
        @NotBlank(message = "이메일을 입력하세요.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @Size(max = 255, message = "이메일은 255자 이하로 입력하세요.")
        String email,

        @NotBlank(message = "이름을 입력하세요.")
        @Size(max = 100, message = "이름은 100자 이하로 입력하세요.")
        String name,

        @NotNull(message = "역할을 선택하세요.")
        Role role
) {
}
