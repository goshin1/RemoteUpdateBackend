package com.onpoom.remoteupdate.user;

import java.time.LocalDateTime;

/**
 * 관리자 화면용 사용자 정보.
 * 엔티티(AppUser)를 그대로 내보내지 않는 이유: passwordHash 같은 값이 응답에 섞이면 안 되기 때문.
 */
public record UserResponse(
        Long id,
        String email,
        String name,
        Role role,
        boolean enabled,
        boolean mustChangePassword,
        /** 지금 로그인 잠금 상태인지 (잠금 시각이 아직 지나지 않았는지) */
        boolean locked,
        LocalDateTime lockedUntil,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static UserResponse from(AppUser user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getName(), user.getRole(), user.isEnabled(),
                user.isMustChangePassword(), user.isLocked(LocalDateTime.now()), user.getLockedUntil(),
                user.getCreatedAt(), user.getUpdatedAt());
    }
}
