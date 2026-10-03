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

/**
 * 사용자 (직원/개발자/관리자). 삭제하지 않고 enabled=false 로 비활성화.
 *
 * <h3>어노테이션</h3>
 * <ul>
 *   <li>@Entity: JPA 가 관리하는 클래스 = DB 테이블 한 행과 대응</li>
 *   <li>@Table(name = "app_user"): 테이블 이름. "user" 는 DB 예약어라 피함</li>
 *   <li>@Getter (Lombok): 모든 필드의 getter 자동 생성. setter 는 일부러 만들지 않음 (아래 "설계" 참고)</li>
 *   <li>@NoArgsConstructor(access = PROTECTED): JPA 는 DB 에서 읽은 값으로 객체를 만들 때 기본 생성자가 필요하다.
 *       protected 로 두어 외부에서 빈 객체를 만들지 못하게 막는다</li>
 * </ul>
 *
 * <h3>설계</h3>
 * 값 변경은 setter 대신 의미 있는 메서드(recordLoginFailure, changePassword …)로만 한다.
 * "무엇을 하는지"가 이름에 드러나고, 관련 규칙(예: 비밀번호 변경 시 mustChangePassword 해제)이 한곳에 모인다.
 */
@Getter
@Entity
@Table(name = "app_user")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppUser extends BaseTimeEntity {

    /** 기본키. IDENTITY = DB 의 AUTO_INCREMENT 로 번호를 매김 (INSERT 후에 id 가 정해짐) */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 로그인 ID. unique = true → DB 에 유니크 제약이 생겨 같은 이메일이 두 번 들어가지 않음 */
    @Column(nullable = false, unique = true, length = 255)
    private String email;

    /** BCrypt 해시 (원문 비밀번호는 어디에도 저장하지 않음) */
    @Column(nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false, length = 100)
    private String name;

    /**
     * 역할.
     * - EnumType.STRING: "ADMIN" 처럼 이름으로 저장. 기본값(ORDINAL)은 0,1,2 숫자로 저장돼서 enum 순서를 바꾸면 데이터 의미가 바뀜
     * - @JdbcTypeCode(VARCHAR): Hibernate 는 MariaDB 에서 enum 을 ENUM('STAFF',...) 타입으로 만드는데, 기획서대로 VARCHAR 로 고정
     */
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

    /** 로그인 잠금 해제 시각 (null 이면 잠금 아님). nullable 이므로 @Column 생략 — 기본값으로 매핑됨 */
    private LocalDateTime lockedUntil;

    /**
     * 정적 팩토리 메서드: 생성자 대신 이름 있는 메서드로 객체를 만든다.
     * 필수 값과 초기 상태(활성, 비밀번호 변경 필요)를 여기서 한 번에 보장한다.
     * 관리자가 계정을 발급할 때 사용. 임시 비밀번호이므로 첫 로그인 시 변경해야 함
     */
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
