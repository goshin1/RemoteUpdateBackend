package com.onpoom.remoteupdate.update;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DownloadHistoryRepository
        extends JpaRepository<DownloadHistory, Long>, JpaSpecificationExecutor<DownloadHistory> {

    /** 조건 검색 + 업데이트·프로젝트를 함께 조회 (N+1 쿼리 방지) */
    @Override
    @EntityGraph(attributePaths = {"update", "update.project"})
    Page<DownloadHistory> findAll(Specification<DownloadHistory> spec, Pageable pageable);
}
