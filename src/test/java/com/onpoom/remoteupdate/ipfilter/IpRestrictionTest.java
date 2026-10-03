package com.onpoom.remoteupdate.ipfilter;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jayway.jsonpath.JsonPath;
import com.onpoom.remoteupdate.support.ApiTestSupport;
import com.onpoom.remoteupdate.user.Role;

/**
 * 관리 기능 IP 제한 (Phase 7).
 * <p>
 * @TestPropertySource: 이 테스트 클래스에서만 설정값을 바꿈 → IP 제한을 켠 별도 스프링 컨텍스트가 뜬다.
 * MockMvc 요청의 기본 IP 는 127.0.0.1(서버 자신 → 항상 허용)이므로, from("10.0.0.5") 로 다른 PC 에서 온 요청을 흉내 낸다.
 */
@TestPropertySource(properties = "app.security.ip-filter.enabled=true")
class IpRestrictionTest extends ApiTestSupport {

    private static final String OFFICE = "10.77.0.0/24";

    @Autowired
    AllowedIpRepository allowedIpRepository;

    MockHttpSession admin;

    @BeforeEach
    void setUp() throws Exception {
        allowedIpRepository.deleteAll();
        admin = loginAs(Role.ADMIN); // 127.0.0.1 에서 로그인
    }

    private static RequestPostProcessor from(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    private long addAllowedIp(String value) throws Exception {
        String body = mvc.perform(post("/api/v1/admin/allowed-ips").session(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ipOrCidr\":\"" + value + "\",\"description\":\"사무실\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private String projectJson() {
        return "{\"name\":\"IP테스트-" + UUID.randomUUID() + "\"}";
    }

    @Test
    void 허용되지_않은_IP에서는_데이터_변경과_관리자_메뉴만_막힌다() throws Exception {
        MockHttpSession developer = loginAs(Role.DEVELOPER);
        MockHttpSession staff = loginAs(Role.STAFF);
        String outside = "203.0.113.9";

        // 변경 요청 → 403 IP_NOT_ALLOWED
        mvc.perform(post("/api/v1/projects").session(admin).with(csrf()).with(from(outside))
                        .contentType(MediaType.APPLICATION_JSON).content(projectJson()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("IP_NOT_ALLOWED"));
        // 관리자 메뉴는 조회도 막힘
        mvc.perform(get("/api/v1/admin/users").session(admin).with(from(outside)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("IP_NOT_ALLOWED"));

        // 조회는 가능: 개발자의 다운로드 이력, 직원의 프로젝트 목록
        mvc.perform(get("/api/v1/downloads").session(developer).with(from(outside))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/projects").session(staff).with(from(outside))).andExpect(status().isOk());

        // 로그인·로그아웃은 어디서나
        mvc.perform(post("/api/v1/auth/login").with(csrf()).with(from(outside))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@test.com\",\"password\":\"Temp1234\"}"))
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS")); // IP 가 아니라 계정 때문에 실패
        mvc.perform(post("/api/v1/auth/logout").session(staff).with(csrf()).with(from(outside)))
                .andExpect(status().isNoContent());
    }

    @Test
    void CIDR로_등록한_범위에서는_관리_기능을_쓸_수_있다() throws Exception {
        addAllowedIp(OFFICE);

        mvc.perform(post("/api/v1/projects").session(admin).with(csrf()).with(from("10.77.0.25"))
                        .contentType(MediaType.APPLICATION_JSON).content(projectJson()))
                .andExpect(status().isCreated());
        // 범위 밖 (10.77.1.x)
        mvc.perform(post("/api/v1/projects").session(admin).with(csrf()).with(from("10.77.1.25"))
                        .contentType(MediaType.APPLICATION_JSON).content(projectJson()))
                .andExpect(status().isForbidden());
    }

    @Test
    void X_Forwarded_For_를_위조해도_통과하지_못한다() throws Exception {
        addAllowedIp(OFFICE);
        mvc.perform(post("/api/v1/projects").session(admin).with(csrf()).with(from("203.0.113.9"))
                        .header("X-Forwarded-For", "10.77.0.25")
                        .contentType(MediaType.APPLICATION_JSON).content(projectJson()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("IP_NOT_ALLOWED"));
    }

    @Test
    void 지금_접속한_IP를_막는_변경은_거부된다() throws Exception {
        long office = addAllowedIp(OFFICE);
        String inOffice = "10.77.0.7";

        // 사무실(10.77.0.7)에서 사무실 항목을 끄거나 지우려 하면 → 스스로 잠기므로 거부
        mvc.perform(patch("/api/v1/admin/allowed-ips/{id}", office).session(admin).with(csrf()).with(from(inOffice))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANNOT_LOCK_OUT_SELF"));
        mvc.perform(delete("/api/v1/admin/allowed-ips/{id}", office).session(admin).with(csrf()).with(from(inOffice)))
                .andExpect(status().isConflict());

        // 다른 허용 IP 를 먼저 등록하면 가능... 이 아니라, 지금 IP 가 여전히 허용되어야 함
        long single = addAllowedIp("10.77.0.7");
        mvc.perform(delete("/api/v1/admin/allowed-ips/{id}", office).session(admin).with(csrf()).with(from(inOffice)))
                .andExpect(status().isNoContent());
        // 서버 PC(127.0.0.1)에서는 항상 허용이므로 마지막 항목도 지울 수 있음
        mvc.perform(delete("/api/v1/admin/allowed-ips/{id}", single).session(admin).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void 잘못된_형식과_중복은_거부() throws Exception {
        for (String bad : new String[] {"999.1.1.1", "10.0.0.0/33", "office.example.com", "abc"}) {
            mvc.perform(post("/api/v1/admin/allowed-ips").session(admin).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"ipOrCidr\":\"" + bad + "\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_IP"));
        }
        addAllowedIp("192.168.10.5");
        mvc.perform(post("/api/v1/admin/allowed-ips").session(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ipOrCidr\":\"192.168.10.5\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_IP"));
        // IPv6 도 가능
        addAllowedIp("fd00:abcd::/32");
    }

    @Test
    void 내_정보와_관리_화면에_현재_IP와_허용_여부가_나온다() throws Exception {
        addAllowedIp(OFFICE);
        MockHttpSession developer = loginAs(Role.DEVELOPER);
        mvc.perform(get("/api/v1/auth/me").session(developer).with(from("203.0.113.9")))
                .andExpect(jsonPath("$.clientIp").value("203.0.113.9"))
                .andExpect(jsonPath("$.managementAllowed").value(false));
        mvc.perform(get("/api/v1/auth/me").session(developer).with(from("10.77.0.3")))
                .andExpect(jsonPath("$.managementAllowed").value(true));

        mvc.perform(get("/api/v1/admin/allowed-ips").session(admin))
                .andExpect(jsonPath("$.filterEnabled").value(true))
                .andExpect(jsonPath("$.clientIp").value("127.0.0.1"))
                .andExpect(jsonPath("$.clientAllowed").value(true))
                .andExpect(jsonPath("$.items[0].ipOrCidr").value(OFFICE))
                .andExpect(jsonPath("$.items[0].createdByName").value("테스트ADMIN"));
    }
}
