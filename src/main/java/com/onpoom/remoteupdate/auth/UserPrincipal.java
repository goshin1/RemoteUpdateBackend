package com.onpoom.remoteupdate.auth;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.onpoom.remoteupdate.user.AppUser;
import com.onpoom.remoteupdate.user.Role;

/**
 * 세션에 저장되는 로그인 사용자 정보. 비밀번호 해시는 담지 않는다.
 * 컨트롤러에서 @AuthenticationPrincipal UserPrincipal 로 받는다.
 */
public record UserPrincipal(
        Long id,
        String email,
        String name,
        Role role,
        boolean mustChangePassword
) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public static UserPrincipal from(AppUser user) {
        return new UserPrincipal(user.getId(), user.getEmail(), user.getName(), user.getRole(),
                user.isMustChangePassword());
    }

    /** 개발자 이상(DEVELOPER, ADMIN)인지 */
    public boolean isDeveloperOrAbove() {
        return role.includes(Role.DEVELOPER);
    }

    public Collection<? extends GrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority(role.authority()));
    }
}
