package com.onpoom.remoteupdate.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import jakarta.servlet.http.Cookie;

/**
 * 실제 CSRF 쿠키 흐름 검증 (프런트엔드 axios 와 같은 방식).
 * spring-security-test 의 csrf() 는 공유 컨텍스트의 CSRF 저장소를 세션 방식으로 바꿔버리므로
 * 이 테스트는 csrf() 를 쓰지 않고, 새 컨텍스트에서 실행한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class CsrfCookieTest {

    @Autowired
    MockMvc mvc;

    @Test
    void 쿠키로_받은_토큰을_헤더에_담으면_로그인_요청이_통과한다() throws Exception {
        var result = mvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isNoContent())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(cookie().httpOnly("XSRF-TOKEN", false))
                .andReturn();
        String token = result.getResponse().getCookie("XSRF-TOKEN").getValue();

        // CSRF 검증을 통과해 인증 단계까지 도달 (없는 계정이므로 401)
        mvc.perform(post("/api/v1/auth/login")
                        .cookie(new Cookie("XSRF-TOKEN", token))
                        .header("X-XSRF-TOKEN", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@test.com\",\"password\":\"Temp1234\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        // 헤더가 없으면 차단
        mvc.perform(post("/api/v1/auth/login")
                        .cookie(new Cookie("XSRF-TOKEN", token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@test.com\",\"password\":\"Temp1234\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }
}
