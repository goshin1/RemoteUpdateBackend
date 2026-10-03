package com.onpoom.remoteupdate.project;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.onpoom.remoteupdate.common.error.ApiException;
import com.onpoom.remoteupdate.common.error.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectService {

    private final ProjectRepository projectRepository;

    public List<ProjectResponse> findAll() {
        return projectRepository.findAll(Sort.by("name")).stream().map(ProjectResponse::from).toList();
    }

    public ProjectResponse findOne(Long id) {
        return ProjectResponse.from(getProject(id));
    }

    @Transactional
    public ProjectResponse create(ProjectRequest request) {
        String name = request.name().trim();
        if (projectRepository.existsByName(name)) {
            throw new ApiException(ErrorCode.DUPLICATE_PROJECT_NAME);
        }
        return ProjectResponse.from(projectRepository.save(Project.create(name, request.description())));
    }

    @Transactional
    public ProjectResponse update(Long id, ProjectRequest request) {
        Project project = getProject(id);
        String name = request.name().trim();
        if (!project.getName().equals(name) && projectRepository.existsByName(name)) {
            throw new ApiException(ErrorCode.DUPLICATE_PROJECT_NAME);
        }
        project.update(name, request.description());
        return ProjectResponse.from(project);
    }

    /** 다른 서비스에서 프로젝트 존재 확인용 */
    public Project getProject(Long id) {
        return projectRepository.findById(id).orElseThrow(() -> new ApiException(ErrorCode.PROJECT_NOT_FOUND));
    }
}
