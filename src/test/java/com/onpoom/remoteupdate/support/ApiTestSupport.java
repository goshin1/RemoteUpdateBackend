package com.onpoom.remoteupdate.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.onpoom.remoteupdate.user.AppUser;
import com.onpoom.remoteupdate.user.AppUserRepository;
import com.onpoom.remoteupdate.user.Role;

/** API 통합 테스트 공통: 사용자 생성과 로그인 세션 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class ApiTestSupport {

    protected static final String PASSWORD = "Temp1234";

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected AppUserRepository userRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    /** 비밀번호 변경이 끝난 활성 사용자 */
    protected AppUser createUser(Role role) {
        String email = role.name().toLowerCase() + "-" + UUID.randomUUID() + "@test.com";
        AppUser user = AppUser.create(email, passwordEncoder.encode(PASSWORD), "테스트" + role, role);
        user.changePassword(user.getPasswordHash());
        return userRepository.save(user);
    }

    protected MockHttpSession loginAs(Role role) throws Exception {
        return login(createUser(role));
    }

    protected MockHttpSession login(AppUser user) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + user.getEmail() + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);
    }
}
