package com.onpoom.remoteupdate.guide;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 가이드 제목·본문(마크다운). 등록 시에는 multipart 필드, 수정 시에는 JSON 본문 */
public record GuideRequest(
        @NotBlank(message = "제목을 입력하세요.")
        @Size(max = 200, message = "제목은 200자 이하로 입력하세요.")
        String title,

        @Size(max = 100_000, message = "본문은 100,000자 이하로 입력하세요.")
        String content
) {
}
