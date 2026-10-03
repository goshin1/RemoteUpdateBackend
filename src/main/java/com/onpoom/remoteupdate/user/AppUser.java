package com.onpoom.remoteupdate.user;

import java.time.Duration;
import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.onpoom.remoteupdate.common.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 사용자 (직원/개발자/관리자). 삭제하지 않고 enabled=false 로 비활성화 */
@Getter
@Entity
@Table(name = "app_user")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppUser extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 로그인 ID */
    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(nullable = false)
    private boolean enabled;

    /** 관리자가 발급·초기화한 임시 비밀번호인 경우 true → 첫 로그인 시 변경 강제 */
    @Column(nullable = false)
    private boolean mustChangePassword;

    @Column(nullable = false)
    private int failedLoginCount;

    private LocalDateTime lockedUntil;

    /** 관리자가 계정을 발급할 때 사용. 임시 비밀번호이므로 첫 로그인 시 변경해야 함 */
    public static AppUser create(String email, String passwordHash, String name, Role role) {
        AppUser user = new AppUser();
        user.email = email;
        user.passwordHash = passwordHash;
        user.name = name;
        user.role = role;
        user.enabled = true;
        user.mustChangePassword = true;
        user.failedLoginCount = 0;
        return user;
    }

    public boolean isLocked(LocalDateTime now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /** 로그인 실패 기록. 실패 횟수가 maxFailures 에 도달하면 lockDuration 동안 잠금 */
    public void recordLoginFailure(int maxFailures, Duration lockDuration, LocalDateTime now) {
        if (lockedUntil != null && !lockedUntil.isAfter(now)) {
            // 이전 잠금이 풀린 뒤의 실패는 처음부터 다시 셈
            lockedUntil = null;
            failedLoginCount = 0;
        }
        failedLoginCount++;
        if (failedLoginCount >= maxFailures) {
            lockedUntil = now.plus(lockDuration);
        }
    }

    public void recordLoginSuccess() {
        failedLoginCount = 0;
        lockedUntil = null;
    }

    /** 본인이 비밀번호를 변경 */
    public void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
        this.mustChangePassword = false;
    }

    /** 관리자가 임시 비밀번호로 초기화 (잠금도 해제) */
    public void resetPassword(String temporaryPasswordHash) {
        this.passwordHash = temporaryPasswordHash;
        this.mustChangePassword = true;
        this.failedLoginCount = 0;
        this.lockedUntil = null;
    }

    public void updateProfile(String name, Role role) {
        this.name = name;
        this.role = role;
    }

    public void changeEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
