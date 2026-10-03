package com.onpoom.remoteupdate.update;

import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.onpoom.remoteupdate.user.AppUser;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

/** 업데이트 등록·수정·비활성화·활성화 이력 (영구 보관) */
@Getter
@Entity
@Table(name = "update_history",
        indexes = @Index(name = "idx_update_history_update_changed", columnList = "update_id, changed_at"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UpdateHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "update_id", nullable = false, updatable = false)
    private UpdateInfo update;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10, updatable = false)
    private HistoryAction action;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "changed_by", nullable = false, updatable = false)
    private AppUser changedBy;

    /** 변경 전 값 (JSON 문자열) */
    @Column(columnDefinition = "longtext", updatable = false)
    private String beforeJson;

    /** 변경 후 값 (JSON 문자열) */
    @Column(columnDefinition = "longtext", updatable = false)
    private String afterJson;

    @Column(nullable = false, updatable = false)
    private LocalDateTime changedAt;

    public static UpdateHistory of(UpdateInfo update, HistoryAction action, AppUser changedBy,
            String beforeJson, String afterJson) {
        UpdateHistory history = new UpdateHistory();
        history.update = update;
        history.action = action;
        history.changedBy = changedBy;
        history.beforeJson = beforeJson;
        history.afterJson = afterJson;
        history.changedAt = LocalDateTime.now();
        return history;
    }
}
