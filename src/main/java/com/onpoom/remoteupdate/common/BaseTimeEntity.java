package com.onpoom.remoteupdate.common;

import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;

/**
 * created_at / updated_at 공통 컬럼.
 * <ul>
 *   <li>@MappedSuperclass: 이 클래스 자체는 테이블이 아니고, 상속한 엔티티의 테이블에 컬럼만 물려준다</li>
 *   <li>@EntityListeners(AuditingEntityListener): 저장·수정 직전에 아래 시각 필드를 자동으로 채운다
 *       (JpaConfig 의 @EnableJpaAuditing 이 켜져 있어야 동작)</li>
 *   <li>abstract: 단독으로 new 할 일이 없으므로</li>
 * </ul>
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseTimeEntity {

    /** 처음 저장할 때 한 번 기록. updatable = false → UPDATE 문에서 제외되어 바뀌지 않음 */
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 저장·수정할 때마다 갱신 */
    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
