package com.onpoom.remoteupdate.ipfilter;

import java.time.LocalDateTime;

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

/** 개발자/관리자 기능 허용 IP (단일 IP 또는 CIDR). Phase 7에서 사용 */
@Getter
@Entity
@Table(name = "allowed_ip")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AllowedIp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String ipOrCidr;

    @Column(length = 200)
    private String description;

    @Column(nullable = false)
    private boolean enabled;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private AppUser createdBy;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static AllowedIp create(String ipOrCidr, String description, AppUser createdBy) {
        AllowedIp ip = new AllowedIp();
        ip.ipOrCidr = ipOrCidr;
        ip.description = description;
        ip.enabled = true;
        ip.createdBy = createdBy;
        ip.createdAt = LocalDateTime.now();
        return ip;
    }

    public void changeEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
