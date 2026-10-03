package com.onpoom.remoteupdate.user;

/** 선택 목록용 최소 정보 (다운로드 이력 화면의 다운로더 필터) */
public record UserOptionResponse(Long id, String name, String email) {

    public static UserOptionResponse from(AppUser user) {
        return new UserOptionResponse(user.getId(), user.getName(), user.getEmail());
    }
}
