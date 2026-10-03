package com.onpoom.remoteupdate.update;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 업데이트 메타데이터 수정. 버전과 파일은 바꿀 수 없다 (바꾸려면 새 버전 등록) */
public record UpdateMetaRequest(
        @NotBlank(message = "제목을 입력하세요.")
        @Size(max = 200, message = "제목은 200자 이하로 입력하세요.")
        String title,

        @Size(max = 2000, message = "내용은 2000자 이하로 입력하세요.")
        String content
) {
}
