package com.onpoom.remoteupdate.auth;

/** 비밀번호 규칙: 8~64자, 영문과 숫자를 각각 1자 이상 포함 */
public final class PasswordPolicy {

    public static final String REGEX = "^(?=.*[A-Za-z])(?=.*\\d).{8,64}$";
    public static final String MESSAGE = "비밀번호는 영문과 숫자를 포함해 8~64자여야 합니다.";

    private PasswordPolicy() {
    }
}
