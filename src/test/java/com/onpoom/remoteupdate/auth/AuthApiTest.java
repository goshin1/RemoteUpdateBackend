package com.onpoom.remoteupdate.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.onpoom.remoteupdate.config.AppProperties;
import com.onpoom.remoteupdate.user.AppUser;
import com.onpoom.remoteupdate.user.AppUserRepository;
import com.onpoom.remoteupdate.user.Role;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthApiTest {

    private static final String PASSWORD = "Temp1234";

    @Autowired
    MockMvc mvc;

    @Autowired
    AppUserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    AppProperties appProperties;

    @Test
    void 로그인하지_않으면_401() throws Exception {
        mvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void CSRF_토큰_없이_로그인하면_403() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("someone@test.com", PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }

    @Test
    void 시드_관리자는_첫_로그인_후_비밀번호를_바꿔야_다른_API를_쓸_수_있다() throws Exception {
        AppProperties.Admin admin = appProperties.seed().admin();

        MvcResult login = mvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(admin.email(), admin.password())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.mustChangePassword").value(true))
                .andReturn();
        MockHttpSession session = session(login);

        // 비밀번호 변경 전: 인증 API 외에는 차단
        mvc.perform(get("/api/v1/admin/users").session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
        mvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isOk());

        // 규칙에 맞지 않는 새 비밀번호
        mvc.perform(put("/api/v1/auth/password").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordJson(admin.password(), "short")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        // 현재 비밀번호 불일치
        mvc.perform(put("/api/v1/auth/password").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordJson("wrong-pass1", "NewPass1234")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_MISMATCH"));

        mvc.perform(put("/api/v1/auth/password").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordJson(admin.password(), "NewPass1234")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(false));

        // 변경 후: 차단이 풀림 (아직 없는 API 이므로 404)
        mvc.perform(get("/api/v1/admin/users").session(session))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void 역할_계층에_따라_관리자_API_접근이_제한된다() throws Exception {
        MockHttpSession staff = loginAs(createUser(Role.STAFF, true, false));
        mvc.perform(get("/api/v1/admin/users").session(staff))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        MockHttpSession developer = loginAs(createUser(Role.DEVELOPER, true, false));
        mvc.perform(get("/api/v1/admin/users").session(developer))
                .andExpect(status().isForbidden());
    }

    @Test
    void 로그인_연속_실패시_계정이_잠긴다() throws Exception {
        String email = createUser(Role.STAFF, true, false);
        int max = appProperties.security().login().maxFailures();

        for (int i = 0; i < max; i++) {
            mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content(loginJson(email, "Wrong1234")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }
        // 잠긴 뒤에는 올바른 비밀번호도 거부
        mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));

        AppUser user = userRepository.findByEmail(email).orElseThrow();
        assertThat(user.getLockedUntil()).isNotNull();
    }

    @Test
    void 비활성_계정은_로그인할_수_없다() throws Exception {
        String email = createUser(Role.STAFF, false, false);
        mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
    }

    @Test
    void 없는_계정은_비밀번호_오류와_같은_응답() throws Exception {
        mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("nobody@test.com", PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void 로그아웃하면_세션이_끊긴다() throws Exception {
        MockHttpSession session = loginAs(createUser(Role.STAFF, true, false));
        mvc.perform(get("/api/v1/auth/me").session(session)).andExpect(status().isOk());

        mvc.perform(post("/api/v1/auth/logout").session(session).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(session.isInvalid()).isTrue();
    }

    // ---- helpers ----

    private String createUser(Role role, boolean enabled, boolean mustChangePassword) {
        String email = role.name().toLowerCase() + "-" + UUID.randomUUID() + "@test.com";
        AppUser user = AppUser.create(email, passwordEncoder.encode(PASSWORD), "테스트" + role, role);
        if (!mustChangePassword) {
            user.changePassword(user.getPasswordHash());
        }
        user.changeEnabled(enabled);
        userRepository.save(user);
        return email;
    }

    private MockHttpSession loginAs(String email) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return session(result);
    }

    private static MockHttpSession session(MvcResult result) {
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private static String loginJson(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    private static String passwordJson(String current, String next) {
        return "{\"currentPassword\":\"" + current + "\",\"newPassword\":\"" + next + "\"}";
    }
}
