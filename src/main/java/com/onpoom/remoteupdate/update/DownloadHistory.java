package com.onpoom.remoteupdate.update;

import java.time.LocalDateTime;

import com.onpoom.remoteupdate.user.AppUser;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 다운로드 이력 (영구 보관) */
@Getter
@Entity
@Table(name = "download_history", indexes = {
        @Index(name = "idx_download_history_update_downloaded", columnList = "update_id, downloaded_at"),
        @Index(name = "idx_download_history_downloaded", columnList = "downloaded_at"),
        @Index(name = "idx_download_history_user", columnList = "user_id")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DownloadHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "update_id", nullable = false, updatable = false)
    private UpdateInfo update;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private AppUser user;

    /** 다운로드 당시 사용자 이름 (스냅샷) */
    @Column(nullable = false, length = 100, updatable = false)
    private String downloaderName;

    /** IPv6 대응 */
    @Column(nullable = false, length = 45, updatable = false)
    private String clientIp;

    @Column(nullable = false, updatable = false)
    private LocalDateTime downloadedAt;

    public static DownloadHistory of(UpdateInfo update, AppUser user, String clientIp) {
        DownloadHistory history = new DownloadHistory();
        history.update = update;
        history.user = user;
        history.downloaderName = user.getName();
        history.clientIp = clientIp;
        history.downloadedAt = LocalDateTime.now();
        return history;
    }
}
