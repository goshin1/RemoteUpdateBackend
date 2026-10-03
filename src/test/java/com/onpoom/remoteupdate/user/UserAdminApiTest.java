package com.onpoom.remoteupdate.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import com.jayway.jsonpath.JsonPath;
import com.onpoom.remoteupdate.support.ApiTestSupport;

/**
 * 사용자 관리 API + 세션 즉시 반영(SessionUserRefreshFilter) 테스트.
 * 테스트 이름을 한글로 쓰면 실패했을 때 무엇이 깨졌는지 바로 알 수 있다.
 */
class UserAdminApiTest extends ApiTestSupport {

    private static String createJson(String email, String name, String role) {
        return "{\"email\":\"" + email + "\",\"name\":\"" + name + "\",\"role\":\"" + role + "\"}";
    }

    @Test
    void 관리자가_사용자를_등록하면_임시_비밀번호로_로그인하고_변경을_강제당한다() throws Exception {
        MockHttpSession admin = loginAs(Role.ADMIN);
        String email = "New-" + UUID.randomUUID() + "@Test.com";

        String body = mvc.perform(post("/api/v1/admin/users").session(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(createJson(email, "홍길동", "STAFF")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.email").value(email.toLowerCase())) // 소문자로 저장
                .andExpect(jsonPath("$.user.mustChangePassword").value(true))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String temporaryPassword = JsonPath.read(body, "$.temporaryPassword");
        assertThat(temporaryPassword).matches("^(?=.*[A-Za-z])(?=.*\\d).{12}$");

        // 대소문자를 바꿔 입력해도 로그인 가능
        mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email.toUpperCase() + "\",\"password\":\"" + temporaryPassword + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(true));

        // 같은 이메일 중복 등록
        mvc.perform(post("/api/v1/admin/users").session(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(createJson(email, "중복", "STAFF")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"));
    }

    @Test
    void 개발자는_사용자_관리_API를_쓸_수_없다() throws Exception {
        MockHttpSession developer = loginAs(Role.DEVELOPER);
        mvc.perform(get("/api/v1/admin/users").session(developer)).andExpect(status().isForbidden());
        // 단, 선택 목록은 조회 가능
        mvc.perform(get("/api/v1/users/options").session(developer)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/users/options").session(loginAs(Role.STAFF))).andExpect(status().isForbidden());
    }

    @Test
    void 사용자_검색() throws Exception {
        MockHttpSession admin = loginAs(Role.ADMIN);
        String tag = UUID.randomUUID().toString().substring(0, 8);
        for (String role : new String[] {"STAFF", "STAFF", "DEVELOPER"}) {
            mvc.perform(post("/api/v1/admin/users").session(admin).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createJson(tag + "-" + UUID.randomUUID() + "@x.com", "검색" + tag, role)))
                    .andExpect(status().isCreated());
        }
        mvc.perform(get("/api/v1/admin/users").param("keyword", tag).session(admin))
                .andExpect(jsonPath("$.totalElements").value(3));
        mvc.perform(get("/api/v1/admin/users").param("keyword", tag).param("role", "DEVELOPER").session(admin))
                .andExpect(jsonPath("$.totalElements").value(1));
        // LIKE 특수문자는 글자 그대로 검색
        mvc.perform(get("/api/v1/admin/users").param("keyword", "%").session(admin))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void 비활성화하면_이미_로그인한_세션도_즉시_끊긴다() throws Exception {
        MockHttpSession admin = loginAs(Role.ADMIN);
        AppUser staffUser = createUser(Role.STAFF);
        MockHttpSession staff = login(staffUser);
        mvc.perform(get("/api/v1/projects").session(staff)).andExpect(status().isOk());

        mvc.perform(patch("/api/v1/admin/users/{id}/status", staffUser.getId()).session(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        mvc.perform(get("/api/v1/projects").session(staff))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        assertThat(staff.isInvalid()).isTrue();
    }

    @Test
    void 역할을_낮추면_다음_요청부터_바로_권한이_줄어든다() throws Exception {
        MockHttpSession admin = loginAs(Role.ADMIN);
        AppUser devUser = createUser(Role.DEVELOPER);
        MockHttpSession developer = login(devUser);
        mvc.perform(get("/api/v1/downloads").session(developer)).andExpect(status().isOk());

        mvc.perform(put("/api/v1/admin/users/{id}", devUser.getId()).session(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"강등\",\"role\":\"STAFF\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("STAFF"));

        mvc.perform(get("/api/v1/downloads").session(developer)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/auth/me").session(developer))
                .andExpect(jsonPath("$.role").value("STAFF"))
                .andExpect(jsonPath("$.name").value("강등"));
    }

    @Test
    void 비밀번호_초기화는_잠금을_풀고_변경을_강제한다() throws Exception {
        MockHttpSession admin = loginAs(Role.ADMIN);
        AppUser user = createUser(Role.STAFF);
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"" + user.getEmail() + "\",\"password\":\"Wrong9999\"}"));
        }
        mvc.perform(get("/api/v1/admin/users").param("keyword", user.getEmail()).session(admin))
                .andExpect(jsonPath("$.content[0].locked").value(true));

        String body = mvc.perform(post("/api/v1/admin/users/{id}/reset-password", user.getId())
                        .session(admin).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.locked").value(false))
                .andExpect(jsonPath("$.user.mustChangePassword").value(true))
                .andReturn().getResponse().getContentAsString();
        String temporaryPassword = JsonPath.read(body, "$.temporaryPassword");

        mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + user.getEmail() + "\",\"password\":\"" + temporaryPassword + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(true));
    }

    @Test
    void 관리자는_본인_계정의_역할과_사용_여부를_바꿀_수_없다() throws Exception {
        AppUser me = createUser(Role.ADMIN);
        MockHttpSession admin = login(me);
        mvc.perform(patch("/api/v1/admin/users/{id}/status", me.getId()).session(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CANNOT_CHANGE_OWN_ACCOUNT"));
        mvc.perform(put("/api/v1/admin/users/{id}", me.getId()).session(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"나\",\"role\":\"STAFF\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CANNOT_CHANGE_OWN_ACCOUNT"));
        // 이름만 바꾸는 것은 가능
        mvc.perform(put("/api/v1/admin/users/{id}", me.getId()).session(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"새이름\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void 업로드_정책_조회() throws Exception {
        mvc.perform(get("/api/v1/config/upload").session(loginAs(Role.DEVELOPER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowedExtensions[0]").value("exe"))
                .andExpect(jsonPath("$.maxFileSizeBytes").value(500L * 1024 * 1024));
        mvc.perform(get("/api/v1/config/upload").session(loginAs(Role.STAFF)))
                .andExpect(status().isForbidden());
    }
}
