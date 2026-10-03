package com.onpoom.remoteupdate.config;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.onpoom.remoteupdate.support.ApiTestSupport;
import com.onpoom.remoteupdate.user.Role;

/**
 * 운영 배포 방식: Backend 가 빌드된 Frontend(dist) 를 함께 제공하는지.
 * <p>
 * @DynamicPropertySource: 테스트 실행 중에 만든 값(임시 폴더 경로)을 설정값으로 넣을 때 사용.
 * 설정이 다른 테스트와 달라지므로 스프링 컨텍스트도 따로 뜬다.
 */
class FrontendServingTest extends ApiTestSupport {

    static final Path DIST = createDist();

    static Path createDist() {
        try {
            Path dir = Files.createTempDirectory("dist");
            Files.writeString(dir.resolve("index.html"), "<!doctype html><div id=\"app\">INDEX</div>");
            Files.createDirectories(dir.resolve("assets"));
            Files.writeString(dir.resolve("assets/index-abc123.js"), "console.log('app')");
            Files.writeString(dir.resolve("favicon.svg"), "<svg/>");
            return dir;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void frontendDir(DynamicPropertyRegistry registry) {
        registry.add("app.frontend.dist-dir", DIST::toString);
    }

    @Test
    void 로그인하지_않아도_화면_파일은_받을_수_있다() throws Exception {
        // "/" 는 index.html 로 내부 전달(forward). MockMvc 는 forward 를 실제로 실행하지 않고 전달 주소만 기록한다
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
        mvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("INDEX")));
        mvc.perform(get("/favicon.svg")).andExpect(status().isOk());
    }

    @Test
    void 화면_주소로_새로고침하면_index_html을_준다() throws Exception {
        mvc.perform(get("/projects/3"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("INDEX")))
                .andExpect(header().string("Cache-Control", containsString("no-cache")));
    }

    @Test
    void 해시가_붙은_정적_파일은_오래_캐시한다() throws Exception {
        mvc.perform(get("/assets/index-abc123.js"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("max-age=31536000")));
    }

    @Test
    void 임시_비밀번호_상태에서도_화면_파일은_받을_수_있다() throws Exception {
        // 버그 재발 방지: 첫 로그인(비밀번호 변경 필요) 상태에서 js 를 막으면 비밀번호 변경 화면을 띄울 수 없었음
        var user = createUser(Role.STAFF);
        user.resetPassword(user.getPasswordHash()); // mustChangePassword = true
        userRepository.save(user);
        var session = login(user);
        mvc.perform(get("/assets/index-abc123.js").session(session)).andExpect(status().isOk());
        mvc.perform(get("/password").session(session)).andExpect(status().isOk());
        // API 는 여전히 차단
        mvc.perform(get("/api/v1/projects").session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
    }

    @Test
    void API_주소는_화면으로_대체되지_않는다() throws Exception {
        // 로그인 안 함 → 401 JSON (index.html 이 아님)
        mvc.perform(get("/api/v1/projects"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        // 로그인했지만 없는 API → 404 JSON
        mvc.perform(get("/api/v1/no-such-api").session(loginAs(Role.STAFF)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
