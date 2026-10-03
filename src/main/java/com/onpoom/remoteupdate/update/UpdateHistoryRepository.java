package com.onpoom.remoteupdate.update;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UpdateHistoryRepository extends JpaRepository<UpdateHistory, Long> {

    @EntityGraph(attributePaths = "changedBy")
    List<UpdateHistory> findByUpdateIdOrderByIdAsc(Long updateId);
}
