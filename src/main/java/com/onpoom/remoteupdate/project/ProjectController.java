package com.onpoom.remoteupdate.project;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 조회는 로그인한 모든 사용자, 등록·수정은 ADMIN */
@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @GetMapping
    public List<ProjectResponse> list() {
        return projectService.findAll();
    }

    @GetMapping("/{id}")
    public ProjectResponse get(@PathVariable Long id) {
        return projectService.findOne(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProjectResponse> create(@Valid @RequestBody ProjectRequest request) {
        ProjectResponse created = projectService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/projects/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ProjectResponse update(@PathVariable Long id, @Valid @RequestBody ProjectRequest request) {
        return projectService.update(id, request);
    }
}
