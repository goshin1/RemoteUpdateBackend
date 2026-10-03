package com.onpoom.remoteupdate.project;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.onpoom.remoteupdate.common.error.ApiException;
import com.onpoom.remoteupdate.common.error.ErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * 프로젝트 비즈니스 로직.
 * <p>
 * 클래스에 @Transactional(readOnly = true) 를 걸면 모든 public 메서드가 읽기 전용 트랜잭션으로 실행된다.
 * 데이터를 바꾸는 메서드(create, update)에만 @Transactional 을 다시 붙여 쓰기 가능으로 덮어쓴다.
 * readOnly 는 실수로 수정하는 것을 막고, JPA 의 변경 감지를 생략해 조회가 조금 빨라진다.
 */
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
        project.update(name, request.description()); // save() 없이도 트랜잭션 종료 시 UPDATE (더티 체킹)
        return ProjectResponse.from(project);
    }

    /** 다른 서비스에서 프로젝트 존재 확인용 */
    public Project getProject(Long id) {
        return projectRepository.findById(id).orElseThrow(() -> new ApiException(ErrorCode.PROJECT_NOT_FOUND));
    }
}
