package com.onpoom.remoteupdate.common.error;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** API 오류 코드. 응답 형식: { "code": "...", "message": "..." } */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 공통
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다."),
    CSRF_INVALID(HttpStatus.FORBIDDEN, "요청 보안 토큰이 없거나 만료되었습니다. 페이지를 새로고침하세요."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "대상을 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청 방식입니다."),
    CONFLICT(HttpStatus.CONFLICT, "이미 존재하는 값입니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),

    // 인증
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    ACCOUNT_LOCKED(HttpStatus.UNAUTHORIZED, "로그인 실패 횟수를 초과해 계정이 잠겼습니다. 잠시 후 다시 시도하세요."),
    ACCOUNT_DISABLED(HttpStatus.UNAUTHORIZED, "사용이 중지된 계정입니다. 관리자에게 문의하세요."),
    PASSWORD_CHANGE_REQUIRED(HttpStatus.FORBIDDEN, "비밀번호를 변경한 후 이용할 수 있습니다."),
    CURRENT_PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "현재 비밀번호가 올바르지 않습니다."),
    SAME_AS_CURRENT_PASSWORD(HttpStatus.BAD_REQUEST, "새 비밀번호가 현재 비밀번호와 같습니다."),

    // 사용자 관리
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 등록된 이메일입니다."),
    CANNOT_CHANGE_OWN_ACCOUNT(HttpStatus.BAD_REQUEST, "본인 계정의 역할·사용 여부는 바꿀 수 없습니다. 다른 관리자에게 요청하세요."),
    LAST_ADMIN(HttpStatus.CONFLICT, "활성 관리자가 최소 한 명은 있어야 합니다."),

    // 프로젝트 / 업데이트
    PROJECT_NOT_FOUND(HttpStatus.NOT_FOUND, "프로젝트를 찾을 수 없습니다."),
    DUPLICATE_PROJECT_NAME(HttpStatus.CONFLICT, "이미 같은 이름의 프로젝트가 있습니다."),
    UPDATE_NOT_FOUND(HttpStatus.NOT_FOUND, "업데이트를 찾을 수 없습니다."),
    DUPLICATE_VERSION(HttpStatus.CONFLICT, "이 프로젝트에 같은 버전이 이미 등록되어 있습니다."),
    UPDATE_DISABLED(HttpStatus.FORBIDDEN, "다운로드가 중단된 업데이트입니다."),
    GUIDE_NOT_FOUND(HttpStatus.NOT_FOUND, "가이드를 찾을 수 없습니다."),
    ATTACHMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "첨부 파일이 없습니다."),
    INVALID_DATE_RANGE(HttpStatus.BAD_REQUEST, "조회 시작일이 종료일보다 늦습니다."),

    // 파일
    FILE_REQUIRED(HttpStatus.BAD_REQUEST, "파일을 선택하세요."),
    FILE_EXTENSION_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "허용되지 않은 파일 형식입니다."),
    FILE_NAME_INVALID(HttpStatus.BAD_REQUEST, "파일 이름이 올바르지 않습니다."),
    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "파일 크기가 허용 범위를 초과했습니다."),
    FILE_STORAGE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "파일 저장 중 오류가 발생했습니다."),

    // IP 제한 (Phase 7)
    INVALID_IP(HttpStatus.BAD_REQUEST, "IP 주소 형식이 올바르지 않습니다. 예: 192.168.0.10 또는 192.168.0.0/24"),
    DUPLICATE_IP(HttpStatus.CONFLICT, "이미 등록된 IP 입니다."),
    ALLOWED_IP_NOT_FOUND(HttpStatus.NOT_FOUND, "허용 IP 를 찾을 수 없습니다."),
    CANNOT_LOCK_OUT_SELF(HttpStatus.CONFLICT, "이 항목을 끄면 지금 접속한 IP 에서 관리 기능을 쓸 수 없게 됩니다. 다른 허용 IP 를 먼저 등록하세요."),
    IP_NOT_ALLOWED(HttpStatus.FORBIDDEN, "허용되지 않은 IP입니다.");

    private final HttpStatus status;
    private final String message;
}
