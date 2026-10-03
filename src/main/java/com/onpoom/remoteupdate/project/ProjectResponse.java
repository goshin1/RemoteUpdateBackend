package com.onpoom.remoteupdate.project;

import java.time.LocalDateTime;

public record ProjectResponse(
        Long id,
        String name,
        String description,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static ProjectResponse from(Project project) {
        return new ProjectResponse(project.getId(), project.getName(), project.getDescription(),
                project.getCreatedAt(), project.getUpdatedAt());
    }
}
