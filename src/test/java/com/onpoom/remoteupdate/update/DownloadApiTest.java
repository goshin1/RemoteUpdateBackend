package com.onpoom.remoteupdate.update;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jayway.jsonpath.JsonPath;
import com.onpoom.remoteupdate.project.Project;
import com.onpoom.remoteupdate.project.ProjectRepository;
import com.onpoom.remoteupdate.support.ApiTestSupport;
import com.onpoom.remoteupdate.user.AppUser;
import com.onpoom.remoteupdate.user.Role;

class DownloadApiTest extends ApiTestSupport {

    private static final byte[] FILE_BYTES = "firmware bytes 1234".getBytes(StandardCharsets.UTF_8);

    @Autowired
    ProjectRepository projectRepository;

    Project project;
    MockHttpSession developer;
    long updateId;
    String checksum;

    @BeforeEach
    void setUp() throws Exception {
        project = projectRepository.save(Project.create("다운로드테스트-" + UUID.randomUUID(), null));
        developer = loginAs(Role.DEVELOPER);
        String body = mvc.perform(multipart("/api/v1/projects/{id}/updates", project.getId())
                        .file(new MockMultipartFile("file", "통신모듈-1.0.0.zip", "application/zip", FILE_BYTES))
                        .param("version", "1.0.0").param("title", "통신 모듈")
                        .session(developer).with(csrf()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        updateId = ((Number) JsonPath.read(body, "$.id")).longValue();
        checksum = JsonPath.read(body, "$.checksum");
    }

    @Test
    void 직원이_다운로드하면_파일과_체크섬이_내려가고_이력이_남는다() throws Exception {
        AppUser staffUser = createUser(Role.STAFF);
        MockHttpSession staff = login(staffUser);

        mvc.perform(get("/api/v1/updates/{id}/download", updateId).session(staff).with(remoteAddr("10.20.30.40"))
                        .header("X-Forwarded-For", "1.1.1.1"))
                .andExpect(status().isOk())
                .andExpect(content().bytes(FILE_BYTES))
                .andExpect(header().string("X-Checksum-SHA256", checksum))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("filename*=UTF-8''")))
                .andExpect(header().longValue("Content-Length", FILE_BYTES.length));

        // 개발자가 이력 조회: 신뢰하지 않는 프록시이므로 X-Forwarded-For 는 무시하고 실제 접속 IP 기록
        mvc.perform(get("/api/v1/downloads").param("updateId", String.valueOf(updateId)).session(developer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].userId").value(staffUser.getId()))
                .andExpect(jsonPath("$.content[0].downloaderName").value(staffUser.getName()))
                .andExpect(jsonPath("$.content[0].clientIp").value("10.20.30.40"))
                .andExpect(jsonPath("$.content[0].version").value("1.0.0"))
                .andExpect(jsonPath("$.content[0].projectName").value(project.getName()));
    }

    @Test
    void 비활성_업데이트는_누구도_다운로드할_수_없다() throws Exception {
        mvc.perform(patch("/api/v1/updates/{id}/status", updateId).session(developer).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DISABLED\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/updates/{id}/download", updateId).session(loginAs(Role.STAFF)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("UPDATE_DISABLED"));
        mvc.perform(get("/api/v1/updates/{id}/download", updateId).session(developer))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("UPDATE_DISABLED"));

        mvc.perform(get("/api/v1/downloads").param("updateId", String.valueOf(updateId)).session(developer))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void 로그인하지_않으면_다운로드할_수_없다() throws Exception {
        mvc.perform(get("/api/v1/updates/{id}/download", updateId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 다운로드_이력_검색_조건과_권한() throws Exception {
        AppUser a = createUser(Role.STAFF);
        AppUser b = createUser(Role.STAFF);
        MockHttpSession sa = login(a);
        MockHttpSession sb = login(b);
        mvc.perform(get("/api/v1/updates/{id}/download", updateId).session(sa)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/updates/{id}/download", updateId).session(sa)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/updates/{id}/download", updateId).session(sb)).andExpect(status().isOk());

        String today = LocalDate.now().toString();
        mvc.perform(get("/api/v1/downloads").session(developer)
                        .param("projectId", String.valueOf(project.getId())))
                .andExpect(jsonPath("$.totalElements").value(3));
        mvc.perform(get("/api/v1/downloads").session(developer)
                        .param("projectId", String.valueOf(project.getId()))
                        .param("userId", String.valueOf(a.getId())))
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/v1/downloads").session(developer)
                        .param("projectId", String.valueOf(project.getId()))
                        .param("from", today).param("to", today))
                .andExpect(jsonPath("$.totalElements").value(3));
        mvc.perform(get("/api/v1/downloads").session(developer)
                        .param("projectId", String.valueOf(project.getId()))
                        .param("to", LocalDate.now().minusDays(1).toString()))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/downloads").session(developer)
                        .param("from", today).param("to", LocalDate.now().minusDays(1).toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"));

        // 직원은 이력 조회 불가
        mvc.perform(get("/api/v1/downloads").session(sa))
                .andExpect(status().isForbidden());
    }

    @Test
    void 변경_이력은_JSON_객체로_조회된다() throws Exception {
        mvc.perform(put("/api/v1/updates/{id}", updateId).session(developer).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"통신 모듈 안정화\",\"content\":null}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/updates/{id}/history", updateId).session(developer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].action").value("CREATE"))
                .andExpect(jsonPath("$[0].before").doesNotExist())
                .andExpect(jsonPath("$[0].after.title").value("통신 모듈"))
                .andExpect(jsonPath("$[1].action").value("UPDATE"))
                .andExpect(jsonPath("$[1].before.title").value("통신 모듈"))
                .andExpect(jsonPath("$[1].after.title").value("통신 모듈 안정화"))
                .andExpect(jsonPath("$[1].changedByName").value("테스트DEVELOPER"));

        mvc.perform(get("/api/v1/updates/{id}/history", updateId).session(loginAs(Role.STAFF)))
                .andExpect(status().isForbidden());
    }

    private static RequestPostProcessor remoteAddr(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    @Test
    void 다운로드_파일명은_원본_그대로() throws Exception {
        String disposition = mvc.perform(get("/api/v1/updates/{id}/download", updateId).session(developer))
                .andReturn().getResponse().getHeader("Content-Disposition");
        assertThat(disposition).startsWith("attachment;")
                .contains(java.net.URLEncoder.encode("통신모듈", StandardCharsets.UTF_8).replace("+", "%20"));
    }
}
