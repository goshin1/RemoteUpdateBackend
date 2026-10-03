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
 * <ul>
 *   <li>Serializable: 세션은 서버 재시작·클러스터 공유 시 직렬화(바이트로 저장)될 수 있어서 구현</li>
 *   <li>record 의 equals 는 모든 필드를 비교 → SessionUserRefreshFilter 가 DB 값과 달라졌는지 판단할 때 사용</li>
 *   <li>엔티티(AppUser)를 세션에 넣지 않는 이유: 엔티티는 트랜잭션 밖에서 지연 로딩 문제가 생기고 비밀번호 해시도 들어 있음</li>
 * </ul>
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
