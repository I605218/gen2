package com.codeassistant.backend.exception;

import com.codeassistant.backend.dto.common.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationException(MethodArgumentNotValidException exception,
                                                                      HttpServletRequest request) {
        String message = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("；"));

        return ResponseEntity.badRequest().body(buildResponse(
                HttpStatus.BAD_REQUEST,
                message,
                request.getRequestURI(),
                null
        ));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolationException(ConstraintViolationException exception,
                                                                               HttpServletRequest request) {
        return ResponseEntity.badRequest().body(buildResponse(
                HttpStatus.BAD_REQUEST,
                exception.getMessage(),
                request.getRequestURI(),
                null
        ));
    }

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthException(AuthException exception,
                                                                HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(buildResponse(
                HttpStatus.UNAUTHORIZED,
                exception.getMessage(),
                request.getRequestURI(),
                null
        ));
    }

    @ExceptionHandler(AiBusyException.class)
    public ResponseEntity<ApiErrorResponse> handleAiBusyException(AiBusyException exception,
                                                                  HttpServletRequest request) {
        int retryAfterSeconds = 3;
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(buildResponse(
                HttpStatus.TOO_MANY_REQUESTS,
                exception.getMessage(),
                request.getRequestURI(),
                retryAfterSeconds
        ));
    }

    @ExceptionHandler({AiClientException.class, RestClientException.class, IllegalStateException.class})
    public ResponseEntity<ApiErrorResponse> handleAiException(Exception exception,
                                                              HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(buildResponse(
                HttpStatus.BAD_GATEWAY,
                exception.getMessage(),
                request.getRequestURI(),
                null
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleException(Exception exception,
                                                            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "服务器内部错误，请稍后重试",
                request.getRequestURI(),
                null
        ));
    }

    private ApiErrorResponse buildResponse(HttpStatus status, String message, String path, Integer retryAfterSeconds) {
        return new ApiErrorResponse(
                LocalDateTime.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                path,
                retryAfterSeconds
        );
    }
}
