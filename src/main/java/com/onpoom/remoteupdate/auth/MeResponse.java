package com.onpoom.remoteupdate.auth;

import com.onpoom.remoteupdate.user.AppUser;
import com.onpoom.remoteupdate.user.Role;

/**
 * 로그인 사용자 정보.
 * @param clientIp          서버가 본 접속 IP
 * @param managementAllowed 지금 IP 에서 관리 기능(등록·수정·관리자 메뉴)을 쓸 수 있는지 — 화면 안내용
 */
public record MeResponse(
        Long id,
        String email,
        String name,
        Role role,
        boolean mustChangePassword,
        String clientIp,
        boolean managementAllowed
) {

    public static MeResponse from(AppUser user, String clientIp, boolean managementAllowed) {
        return new MeResponse(user.getId(), user.getEmail(), user.getName(), user.getRole(),
                user.isMustChangePassword(), clientIp, managementAllowed);
    }
}
