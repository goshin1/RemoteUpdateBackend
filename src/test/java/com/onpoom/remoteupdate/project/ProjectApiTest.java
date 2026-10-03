package com.onpoom.remoteupdate.project;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import com.onpoom.remoteupdate.support.ApiTestSupport;
import com.onpoom.remoteupdate.user.Role;

class ProjectApiTest extends ApiTestSupport {

    @Test
    void 관리자는_프로젝트를_등록하고_수정할_수_있다() throws Exception {
        MockHttpSession admin = loginAs(Role.ADMIN);
        String name = "프로젝트-" + UUID.randomUUID();

        String location = mvc.perform(post("/api/v1/projects").session(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"description\":\"설명\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.name").value(name))
                .andReturn().getResponse().getHeader("Location");

        mvc.perform(put(location).session(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "-수정\",\"description\":\"변경\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(name + "-수정"))
                .andExpect(jsonPath("$.description").value("변경"));

        // 직원도 조회 가능
        MockHttpSession staff = loginAs(Role.STAFF);
        mvc.perform(get(location).session(staff)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/projects").session(staff)).andExpect(status().isOk());
    }

    @Test
    void 프로젝트명이_중복되면_409() throws Exception {
        MockHttpSession admin = loginAs(Role.ADMIN);
        String body = "{\"name\":\"중복-" + UUID.randomUUID() + "\"}";
        mvc.perform(post("/api/v1/projects").session(admin).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());
        mvc.perform(post("/api/v1/projects").session(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_PROJECT_NAME"));
    }

    @Test
    void 개발자는_프로젝트를_등록할_수_없다() throws Exception {
        MockHttpSession developer = loginAs(Role.DEVELOPER);
        mvc.perform(post("/api/v1/projects").session(developer).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"x\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void 없는_프로젝트는_404() throws Exception {
        mvc.perform(get("/api/v1/projects/999999").session(loginAs(Role.STAFF)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
    }
}
