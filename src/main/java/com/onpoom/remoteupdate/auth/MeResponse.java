package com.onpoom.remoteupdate.auth;

import com.onpoom.remoteupdate.user.AppUser;
import com.onpoom.remoteupdate.user.Role;

public record MeResponse(
        Long id,
        String email,
        String name,
        Role role,
        boolean mustChangePassword
) {

    public static MeResponse from(AppUser user) {
        return new MeResponse(user.getId(), user.getEmail(), user.getName(), user.getRole(),
                user.isMustChangePassword());
    }
}
