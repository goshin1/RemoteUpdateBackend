package com.onpoom.remoteupdate.update;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 업데이트 등록 (multipart/form-data 의 텍스트 필드). 파일은 별도 파라미터 file */
public record UpdateCreateRequest(
        @NotBlank(message = "버전을 입력하세요.")
        @Size(max = 50, message = "버전은 50자 이하로 입력하세요.")
        @Pattern(regexp = "^[0-9A-Za-z][0-9A-Za-z.+_-]*$",
                message = "버전은 영문, 숫자, . + _ - 만 사용할 수 있습니다. (예: 1.2.0)")
        String version,

        @NotBlank(message = "제목을 입력하세요.")
        @Size(max = 200, message = "제목은 200자 이하로 입력하세요.")
        String title,

        @Size(max = 2000, message = "내용은 2000자 이하로 입력하세요.")
        String content
) {
}
