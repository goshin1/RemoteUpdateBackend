package com.onpoom.remoteupdate.update;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.onpoom.remoteupdate.common.BaseTimeEntity;
import com.onpoom.remoteupdate.project.Project;
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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 업데이트 정보. 파일(file_key, file_name, file_size, checksum)은 등록 후 변경할 수 없고,
 * 파일을 바꾸려면 새 버전을 등록한다.
 */
@Getter
@Entity
@Table(name = "update_info",
        uniqueConstraints = @UniqueConstraint(name = "uk_update_info_project_version",
                columnNames = {"project_id", "version"}),
        indexes = @Index(name = "idx_update_info_project_status_created",
                columnList = "project_id, status, created_at"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UpdateInfo extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, updatable = false)
    private Project project;

    /** 담당 개발자 = 등록한 로그인 사용자 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "developer_id", nullable = false, updatable = false)
    private AppUser developer;

    @Column(nullable = false, length = 50, updatable = false)
    private String version;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 2000)
    private String content;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private UpdateStatus status;

    /** 서버 저장 키 (실제 경로 아님) */
    @Column(nullable = false, length = 500, updatable = false)
    private String fileKey;

    /** 원본 파일명 */
    @Column(nullable = false, length = 255, updatable = false)
    private String fileName;

    @Column(nullable = false, updatable = false)
    private long fileSize;

    /** SHA-256 (hex 64자) */
    @Column(nullable = false, length = 64, columnDefinition = "char(64)", updatable = false)
    private String checksum;

    public static UpdateInfo create(Project project, AppUser developer, String version, String title,
            String content, String fileKey, String fileName, long fileSize, String checksum) {
        UpdateInfo info = new UpdateInfo();
        info.project = project;
        info.developer = developer;
        info.version = version;
        info.title = title;
        info.content = content;
        info.status = UpdateStatus.ACTIVE;
        info.fileKey = fileKey;
        info.fileName = fileName;
        info.fileSize = fileSize;
        info.checksum = checksum;
        return info;
    }

    /** 메타데이터만 수정 가능 */
    public void updateMeta(String title, String content) {
        this.title = title;
        this.content = content;
    }

    public void changeStatus(UpdateStatus status) {
        this.status = status;
    }

    public boolean isDownloadable() {
        return status == UpdateStatus.ACTIVE;
    }
}
