package com.onpoom.remoteupdate.update;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DownloadHistoryRepository extends JpaRepository<DownloadHistory, Long> {
}
