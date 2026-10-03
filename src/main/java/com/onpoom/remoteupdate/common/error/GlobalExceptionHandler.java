package com.onpoom.remoteupdate.common.error;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import lombok.extern.slf4j.Slf4j;

/** 모든 API 오류를 { code, message } 형식으로 통일 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException e) {
        ErrorCode code = e.getErrorCode();
        return ResponseEntity.status(code.getStatus()).body(ErrorResponse.of(code, e.getMessage()));
    }

    /** @PreAuthorize 등 메서드 보안에서 거부된 경우 */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException e) {
        return toResponse(ErrorCode.FORBIDDEN);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException e) {
        return toResponse(ErrorCode.UNAUTHORIZED);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("처리되지 않은 예외", e);
        return toResponse(ErrorCode.INTERNAL_ERROR);
    }

    /** @Valid 검증 실패: 첫 번째 필드 오류 메시지를 사용 */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse(ErrorCode.VALIDATION_FAILED.getMessage());
        return ResponseEntity.badRequest().body(ErrorResponse.of(ErrorCode.VALIDATION_FAILED, message));
    }

    /** Spring MVC 기본 예외(404, 405, 본문 파싱 실패 등)도 같은 형식으로 */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        ErrorCode code = switch (statusCode.value()) {
            case 404 -> ErrorCode.NOT_FOUND;
            case 405 -> ErrorCode.METHOD_NOT_ALLOWED;
            case 413 -> ErrorCode.FILE_TOO_LARGE;
            case 401 -> ErrorCode.UNAUTHORIZED;
            case 403 -> ErrorCode.FORBIDDEN;
            default -> statusCode.is4xxClientError() ? ErrorCode.BAD_REQUEST : ErrorCode.INTERNAL_ERROR;
        };
        if (code == ErrorCode.INTERNAL_ERROR) {
            log.error("MVC 처리 오류", ex);
        }
        return ResponseEntity.status(statusCode).headers(headers).body(ErrorResponse.of(code));
    }

    private ResponseEntity<ErrorResponse> toResponse(ErrorCode code) {
        HttpStatus status = code.getStatus();
        return ResponseEntity.status(status).body(ErrorResponse.of(code));
    }
}
