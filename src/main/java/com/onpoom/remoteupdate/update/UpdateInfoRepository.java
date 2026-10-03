package com.onpoom.remoteupdate.update;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UpdateInfoRepository extends JpaRepository<UpdateInfo, Long> {

    boolean existsByProjectIdAndVersion(Long projectId, String version);

    /** 목록 조회 시 담당 개발자를 함께 조회 (N+1 쿼리 방지) */
    @EntityGraph(attributePaths = "developer")
    Page<UpdateInfo> findByProjectId(Long projectId, Pageable pageable);

    @EntityGraph(attributePaths = "developer")
    Page<UpdateInfo> findByProjectIdAndStatus(Long projectId, UpdateStatus status, Pageable pageable);
}
