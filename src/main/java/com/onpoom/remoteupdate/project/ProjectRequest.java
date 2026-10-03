package com.onpoom.remoteupdate.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProjectRequest(
        @NotBlank(message = "프로젝트명을 입력하세요.")
        @Size(max = 200, message = "프로젝트명은 200자 이하로 입력하세요.")
        String name,

        @Size(max = 2000, message = "설명은 2000자 이하로 입력하세요.")
        String description
) {
}
