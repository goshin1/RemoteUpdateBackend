package com.onpoom.remoteupdate.guide;

import com.onpoom.remoteupdate.common.BaseTimeEntity;
import com.onpoom.remoteupdate.project.Project;
import com.onpoom.remoteupdate.user.AppUser;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 프로젝트별 초기 세팅 가이드 (마크다운 본문 + 선택 첨부 파일) */
@Getter
@Entity
@Table(name = "setup_guide")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SetupGuide extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, updatable = false)
    private Project project;

    @Column(nullable = false, length = 200)
    private String title;

    /** 마크다운 본문 */
    @Column(columnDefinition = "mediumtext")
    private String content;

    /** 첨부 파일 저장 키 (없으면 null) */
    @Column(length = 500)
    private String fileKey;

    /** 첨부 파일 원본명 — 기획서 대비 추가 (다운로드 시 파일명 복원용) */
    @Column(length = 255)
    private String fileName;

    /** 첨부 파일 크기(byte) — 기획서 대비 추가 */
    private Long fileSize;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false, updatable = false)
    private AppUser createdBy;

    public static SetupGuide create(Project project, String title, String content, AppUser createdBy) {
        SetupGuide guide = new SetupGuide();
        guide.project = project;
        guide.title = title;
        guide.content = content;
        guide.createdBy = createdBy;
        return guide;
    }

    public void update(String title, String content) {
        this.title = title;
        this.content = content;
    }

    public void attachFile(String fileKey, String fileName, long fileSize) {
        this.fileKey = fileKey;
        this.fileName = fileName;
        this.fileSize = fileSize;
    }

    public boolean hasAttachment() {
        return fileKey != null;
    }
}
