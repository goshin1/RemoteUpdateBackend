package com.onpoom.remoteupdate.common.error;

import lombok.Getter;

/** 서비스 계층에서 던지는 비즈니스 예외. GlobalExceptionHandler 가 ErrorResponse 로 변환 */
@Getter
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;

    public ApiException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public ApiException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
