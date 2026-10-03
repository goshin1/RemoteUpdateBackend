package com.onpoom.remoteupdate.user;

/**
 * 권한은 ADMIN ⊃ DEVELOPER ⊃ STAFF 계층 (SecurityConfig 의 RoleHierarchy).
 * 선언 순서가 곧 권한 높낮이이므로 순서를 바꾸지 말 것.
 */
public enum Role {
    STAFF,
    DEVELOPER,
    ADMIN;

    /** 이 역할이 other 역할의 권한을 포함하는지 (예: ADMIN.includes(DEVELOPER) == true) */
    public boolean includes(Role other) {
        return this.ordinal() >= other.ordinal();
    }

    public String authority() {
        return "ROLE_" + name();
    }
}
