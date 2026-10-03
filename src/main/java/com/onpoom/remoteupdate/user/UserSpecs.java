package com.onpoom.remoteupdate.user;

import java.util.Locale;

import org.springframework.data.jpa.domain.Specification;

/**
 * 사용자 검색 조건 조각들.
 * <p>
 * Specification 은 "WHERE 절의 조건 하나"를 객체로 표현한 것이다.
 * 각 메서드는 값이 없으면 null 을 돌려주는데, Specification.allOf(...) 로 묶을 때 null 조건은 자동으로 빠진다.
 * 그래서 "검색어만", "역할만", "검색어 + 역할" 같은 모든 조합을 메서드 하나로 처리할 수 있다.
 */
final class UserSpecs {

    private UserSpecs() {
    }

    /** 이름 또는 이메일에 keyword 포함 (대소문자 무시) */
    static Specification<AppUser> keyword(String keyword) {
        return (root, query, cb) -> {
            if (keyword == null || keyword.isBlank()) {
                return null;
            }
            // LIKE 의 특수문자(%, _)를 그대로 검색하도록 이스케이프
            String escaped = keyword.trim().toLowerCase(Locale.ROOT)
                    .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            String pattern = "%" + escaped + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("name")), pattern, '\\'),
                    cb.like(cb.lower(root.get("email")), pattern, '\\'));
        };
    }

    static Specification<AppUser> role(Role role) {
        return (root, query, cb) -> role == null ? null : cb.equal(root.get("role"), role);
    }

    static Specification<AppUser> enabled(Boolean enabled) {
        return (root, query, cb) -> enabled == null ? null : cb.equal(root.get("enabled"), enabled);
    }
}
