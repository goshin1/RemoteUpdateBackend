package com.onpoom.remoteupdate.config.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import com.onpoom.remoteupdate.common.error.ErrorCode;
import com.onpoom.remoteupdate.common.error.ErrorResponse;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.json.JsonMapper;

/** 보안 필터 단계(컨트롤러 도달 전)에서 { code, message } 오류 응답을 직접 작성 */
@Component
@RequiredArgsConstructor
public class SecurityErrorWriter {

    private final JsonMapper jsonMapper;

    public void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(jsonMapper.writeValueAsString(ErrorResponse.of(errorCode)));
    }
}
