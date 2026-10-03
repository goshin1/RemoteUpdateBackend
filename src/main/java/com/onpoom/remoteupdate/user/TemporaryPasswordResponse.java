package com.onpoom.remoteupdate.user;

/**
 * 사용자 등록·비밀번호 초기화 결과.
 * temporaryPassword 는 DB 에 해시로만 저장되므로 **이 응답에서 한 번만** 볼 수 있다.
 * 관리자는 이 값을 사용자에게 전달하고, 사용자는 첫 로그인 때 새 비밀번호로 바꿔야 한다.
 */
public record TemporaryPasswordResponse(UserResponse user, String temporaryPassword) {
}
