package com.onpoom.remoteupdate.guide;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SetupGuideRepository extends JpaRepository<SetupGuide, Long> {

    @EntityGraph(attributePaths = "createdBy")
    List<SetupGuide> findByProjectIdOrderByIdAsc(Long projectId);
}
