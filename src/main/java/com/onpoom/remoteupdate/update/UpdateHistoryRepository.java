package com.onpoom.remoteupdate.update;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UpdateHistoryRepository extends JpaRepository<UpdateHistory, Long> {

    List<UpdateHistory> findByUpdateIdOrderByIdAsc(Long updateId);
}
