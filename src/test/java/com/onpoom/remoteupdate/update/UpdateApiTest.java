package com.onpoom.remoteupdate.update;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.onpoom.remoteupdate.config.AppProperties;
import com.onpoom.remoteupdate.project.Project;
import com.onpoom.remoteupdate.project.ProjectRepository;
import com.onpoom.remoteupdate.support.ApiTestSupport;
import com.onpoom.remoteupdate.user.Role;

class UpdateApiTest extends ApiTestSupport {

    private static final byte[] FILE_BYTES = "update binary content".getBytes(StandardCharsets.UTF_8);

    @Autowired
    ProjectRepository projectRepository;

    @Autowired
    UpdateInfoRepository updateRepository;

    @Autowired
    UpdateHistoryRepository historyRepository;

    @Autowired
    AppProperties appProperties;

    Project project;
    MockHttpSession developer;

    @BeforeEach
    void setUp() throws Exception {
        project = projectRepository.save(Project.create("업데이트테스트-" + UUID.randomUUID(), null));
        developer = loginAs(Role.DEVELOPER);
    }

    @Test
    void 개발자가_업데이트를_등록하면_체크섬과_이력이_저장된다() throws Exception {
        String body = register("1.0.0", "module-1.0.0.zip")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value("1.0.0"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.fileName").value("module-1.0.0.zip"))
                .andExpect(jsonPath("$.fileSize").value(FILE_BYTES.length))
                .andExpect(jsonPath("$.checksum").value(sha256(FILE_BYTES)))
                .andExpect(jsonPath("$.fileKey").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(body, "$.id")).longValue();

        UpdateInfo saved = updateRepository.findById(id).orElseThrow();
        Path stored = Path.of(appProperties.storage().root()).toAbsolutePath().normalize().resolve(saved.getFileKey());
        assertThat(Files.readAllBytes(stored)).isEqualTo(FILE_BYTES);
        assertThat(saved.getFileKey()).startsWith("updates/").doesNotContain("module");

        List<UpdateHistory> history = historyRepository.findByUpdateIdOrderByIdAsc(id);
        assertThat(history).extracting(UpdateHistory::getAction).containsExactly(HistoryAction.CREATE);
        assertThat(history.get(0).getBeforeJson()).isNull();
        assertThat(history.get(0).getAfterJson()).contains("\"version\":\"1.0.0\"");
    }

    @Test
    void 같은_버전은_등록할_수_없다() throws Exception {
        register("2.0.0", "a.zip").andExpect(status().isCreated());
        register("2.0.0", "b.zip")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_VERSION"));
    }

    @Test
    void 허용되지_않은_확장자와_빈_파일은_거부한다() throws Exception {
        register("3.0.0", "evil.jsp")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FILE_EXTENSION_NOT_ALLOWED"));

        mvc.perform(multipart("/api/v1/projects/{id}/updates", project.getId())
                        .file(new MockMultipartFile("file", "empty.zip", "application/zip", new byte[0]))
                        .param("version", "3.0.1").param("title", "빈 파일")
                        .session(developer).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FILE_REQUIRED"));
    }

    @Test
    void 파일명의_경로는_제거된다() throws Exception {
        register("4.0.0", "..\\..\\windows\\setup.msi")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileName").value("setup.msi"));
    }

    @Test
    void 입력값_검증_실패() throws Exception {
        mvc.perform(multipart("/api/v1/projects/{id}/updates", project.getId())
                        .file(new MockMultipartFile("file", "a.zip", "application/zip", FILE_BYTES))
                        .param("version", "1.0 beta").param("title", "제목")
                        .session(developer).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void 직원은_등록할_수_없고_비활성_업데이트는_보이지_않는다() throws Exception {
        long id = idOf(register("5.0.0", "a.zip"));
        register("5.1.0", "b.zip").andExpect(status().isCreated());

        MockHttpSession staff = loginAs(Role.STAFF);
        mvc.perform(multipart("/api/v1/projects/{id}/updates", project.getId())
                        .file(new MockMultipartFile("file", "c.zip", "application/zip", FILE_BYTES))
                        .param("version", "5.2.0").param("title", "직원 등록 시도")
                        .session(staff).with(csrf()))
                .andExpect(status().isForbidden());

        // 비활성화
        mvc.perform(patch("/api/v1/updates/{id}/status", id).session(developer).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DISABLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISABLED"));

        // 직원: 목록에서 빠지고, 상세는 404
        mvc.perform(get("/api/v1/projects/{id}/updates", project.getId()).session(staff))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].version").value("5.1.0"));
        mvc.perform(get("/api/v1/updates/{id}", id).session(staff))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("UPDATE_NOT_FOUND"));

        // 개발자: 전체 보임, 상태 필터 가능
        mvc.perform(get("/api/v1/projects/{id}/updates", project.getId()).session(developer))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].version").value("5.1.0"));
        mvc.perform(get("/api/v1/projects/{id}/updates", project.getId()).param("status", "DISABLED")
                        .session(developer))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].version").value("5.0.0"));
    }

    @Test
    void 메타데이터_수정과_상태_변경은_이력에_남는다() throws Exception {
        long id = idOf(register("6.0.0", "a.zip"));

        mvc.perform(put("/api/v1/updates/{id}", id).session(developer).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"새 제목\",\"content\":\"새 내용\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("새 제목"))
                .andExpect(jsonPath("$.version").value("6.0.0"));

        // 같은 값으로 다시 수정 → 이력 추가 없음
        mvc.perform(put("/api/v1/updates/{id}", id).session(developer).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"새 제목\",\"content\":\"새 내용\"}")).andExpect(status().isOk());

        for (String s : new String[] {"DISABLED", "DISABLED", "ACTIVE"}) {
            mvc.perform(patch("/api/v1/updates/{id}/status", id).session(developer).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + s + "\"}"))
                    .andExpect(status().isOk());
        }

        List<UpdateHistory> history = historyRepository.findByUpdateIdOrderByIdAsc(id);
        assertThat(history).extracting(UpdateHistory::getAction).containsExactly(
                HistoryAction.CREATE, HistoryAction.UPDATE, HistoryAction.DISABLE, HistoryAction.ENABLE);
        UpdateHistory update = history.get(1);
        assertThat(update.getBeforeJson()).contains("\"title\":\"제목 6.0.0\"");
        assertThat(update.getAfterJson()).contains("\"title\":\"새 제목\"");
    }

    @Test
    void 없는_프로젝트에는_등록할_수_없다() throws Exception {
        mvc.perform(multipart("/api/v1/projects/{id}/updates", 999999L)
                        .file(new MockMultipartFile("file", "a.zip", "application/zip", FILE_BYTES))
                        .param("version", "1.0.0").param("title", "제목")
                        .session(developer).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
    }

    // ---- helpers ----

    private ResultActions register(String version, String fileName) throws Exception {
        return mvc.perform(multipart("/api/v1/projects/{id}/updates", project.getId())
                .file(new MockMultipartFile("file", fileName, "application/octet-stream", FILE_BYTES))
                .param("version", version)
                .param("title", "제목 " + version)
                .param("content", "내용")
                .session(developer).with(csrf()));
    }

    private static long idOf(ResultActions result) throws Exception {
        String body = result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
