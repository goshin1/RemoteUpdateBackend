package com.onpoom.remoteupdate.guide;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;

import com.jayway.jsonpath.JsonPath;
import com.onpoom.remoteupdate.project.Project;
import com.onpoom.remoteupdate.project.ProjectRepository;
import com.onpoom.remoteupdate.support.ApiTestSupport;
import com.onpoom.remoteupdate.user.Role;

class GuideApiTest extends ApiTestSupport {

    @Autowired
    ProjectRepository projectRepository;

    Project project;
    MockHttpSession developer;

    @BeforeEach
    void setUp() throws Exception {
        project = projectRepository.save(Project.create("가이드테스트-" + UUID.randomUUID(), null));
        developer = loginAs(Role.DEVELOPER);
    }

    @Test
    void 첨부_파일_있는_가이드를_등록하고_직원이_조회_다운로드한다() throws Exception {
        byte[] pdf = "%PDF-1.4 guide".getBytes(StandardCharsets.UTF_8);
        String body = mvc.perform(multipart("/api/v1/projects/{id}/guides", project.getId())
                        .file(new MockMultipartFile("file", "설치가이드.pdf", "application/pdf", pdf))
                        .param("title", "초기 설치")
                        .param("content", "# 1단계\n전원을 켭니다.")
                        .session(developer).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.hasAttachment").value(true))
                .andExpect(jsonPath("$.fileName").value("설치가이드.pdf"))
                .andExpect(jsonPath("$.fileSize").value(pdf.length))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(body, "$.id")).longValue();

        MockHttpSession staff = loginAs(Role.STAFF);
        mvc.perform(get("/api/v1/projects/{id}/guides", project.getId()).session(staff))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].content").value("# 1단계\n전원을 켭니다."))
                .andExpect(jsonPath("$[0].createdByName").value("테스트DEVELOPER"));
        mvc.perform(get("/api/v1/guides/{id}/attachment", id).session(staff))
                .andExpect(status().isOk())
                .andExpect(content().bytes(pdf));
    }

    @Test
    void 첨부_없는_가이드와_수정_첨부_교체() throws Exception {
        String body = mvc.perform(multipart("/api/v1/projects/{id}/guides", project.getId())
                        .param("title", "네트워크 설정").param("content", "IP 설정")
                        .session(developer).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.hasAttachment").value(false))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(body, "$.id")).longValue();

        mvc.perform(get("/api/v1/guides/{id}/attachment", id).session(developer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ATTACHMENT_NOT_FOUND"));

        mvc.perform(put("/api/v1/guides/{id}", id).session(developer).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"네트워크 설정 v2\",\"content\":\"게이트웨이 추가\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("네트워크 설정 v2"));

        byte[] v1 = "v1".getBytes(StandardCharsets.UTF_8);
        byte[] v2 = "version two".getBytes(StandardCharsets.UTF_8);
        mvc.perform(multipart("/api/v1/guides/{id}/attachment", id)
                        .file(new MockMultipartFile("file", "net-v1.zip", "application/zip", v1))
                        .session(developer).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileName").value("net-v1.zip"));
        mvc.perform(multipart("/api/v1/guides/{id}/attachment", id)
                        .file(new MockMultipartFile("file", "net-v2.zip", "application/zip", v2))
                        .session(developer).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileName").value("net-v2.zip"));
        mvc.perform(get("/api/v1/guides/{id}/attachment", id).session(developer))
                .andExpect(content().bytes(v2));

        // 파일 없이 교체 요청
        mvc.perform(multipart("/api/v1/guides/{id}/attachment", id).session(developer).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FILE_REQUIRED"));
    }

    @Test
    void 직원은_가이드를_등록할_수_없다() throws Exception {
        mvc.perform(multipart("/api/v1/projects/{id}/guides", project.getId())
                        .param("title", "x").session(loginAs(Role.STAFF)).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void 허용되지_않은_첨부_파일은_거부() throws Exception {
        mvc.perform(multipart("/api/v1/projects/{id}/guides", project.getId())
                        .file(new MockMultipartFile("file", "run.bat", "application/octet-stream", new byte[] {1}))
                        .param("title", "x").session(developer).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FILE_EXTENSION_NOT_ALLOWED"));
    }
}
