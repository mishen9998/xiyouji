package com.xiyouji.exception;

import com.xiyouji.dto.response.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.bind.MissingServletRequestParameterException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

/**
 * 全局异常处理器
 * 统一处理各类异常并返回标准化的ErrorResponse
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @ExceptionHandler(com.xiyouji.exception.StorageUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleStorageUnavailable(com.xiyouji.exception.StorageUnavailableException ex) {
        log.error("共享存储不可用", ex);
        ErrorResponse response = new ErrorResponse(
                ex.getErrorCode(), ex.getMessage(), ex.getHttpStatus(),
                LocalDateTime.now().format(TIMESTAMP_FORMATTER));
        return ResponseEntity.status(ex.getHttpStatus()).body(response);
    }

    /**
     * 处理业务异常
     * 根据异常中携带的HTTP状态码返回对应响应
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException ex) {
        log.warn("业务异常: errorCode={}, message={}", ex.getErrorCode(), ex.getMessage());
        ErrorResponse response = new ErrorResponse(
                ex.getErrorCode(),
                ex.getMessage(),
                ex.getHttpStatus(),
                LocalDateTime.now().format(TIMESTAMP_FORMATTER)
        );
        response.setDetails(ex.getDetails());
        return ResponseEntity.status(ex.getHttpStatus()).body(response);
    }

    /**
     * 处理参数校验异常（@RequestBody @Valid 触发）
     * 返回400 Bad Request及校验错误详情
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
        String errorMessage = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", errorMessage);

        ErrorResponse response = new ErrorResponse(
                "VALIDATION_FAILED",
                errorMessage,
                HttpStatus.BAD_REQUEST.value(),
                LocalDateTime.now().format(TIMESTAMP_FORMATTER)
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * 处理约束违反异常（@RequestParam @Validated 触发）
     * 返回400 Bad Request及约束违反详情
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolationException(ConstraintViolationException ex) {
        String errorMessage = ex.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .collect(Collectors.joining("; "));
        log.warn("约束违反: {}", errorMessage);

        ErrorResponse response = new ErrorResponse(
                "CONSTRAINT_VIOLATION",
                errorMessage,
                HttpStatus.BAD_REQUEST.value(),
                LocalDateTime.now().format(TIMESTAMP_FORMATTER)
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * 处理请求体不可读异常（JSON格式错误/缺失请求体）
     * 返回400 Bad Request
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex) {
        log.warn("请求体解析失败: {}", ex.getMessage());
        ErrorResponse response = new ErrorResponse(
                "MALFORMED_REQUEST_BODY",
                "请求体格式错误或为空",
                HttpStatus.BAD_REQUEST.value(),
                LocalDateTime.now().format(TIMESTAMP_FORMATTER)
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * 处理必填参数缺失异常（缺少 @RequestParam）
     * 返回400 Bad Request
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingServletRequestParameterException(MissingServletRequestParameterException ex) {
        log.warn("缺少必填参数: {}", ex.getParameterName());
        ErrorResponse response = new ErrorResponse(
                "MISSING_PARAMETER",
                "缺少必填参数: " + ex.getParameterName(),
                HttpStatus.BAD_REQUEST.value(),
                LocalDateTime.now().format(TIMESTAMP_FORMATTER)
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * 处理参数类型不匹配异常（如 String 转 Long 失败）
     * 返回400 Bad Request
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException ex) {
        log.warn("参数类型不匹配: {} 期望 {}", ex.getName(), ex.getRequiredType());
        ErrorResponse response = new ErrorResponse(
                "TYPE_MISMATCH",
                "参数 '" + ex.getName() + "' 类型不匹配",
                HttpStatus.BAD_REQUEST.value(),
                LocalDateTime.now().format(TIMESTAMP_FORMATTER)
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * 处理访问被拒绝异常（权限不足）
     * 返回403 Forbidden
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException ex) {
        log.warn("访问被拒绝: {}", ex.getMessage());
        ErrorResponse response = new ErrorResponse(
                "ACCESS_DENIED",
                "权限不足，拒绝访问",
                HttpStatus.FORBIDDEN.value(),
                LocalDateTime.now().format(TIMESTAMP_FORMATTER)
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    /**
     * 处理请求路径不存在异常（404）
     * 返回404 Not Found
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoHandlerFoundException(NoHandlerFoundException ex) {
        log.warn("请求路径不存在: {}", ex.getRequestURL());
        ErrorResponse response = new ErrorResponse(
                "RESOURCE_NOT_FOUND",
                "请求的资源不存在",
                HttpStatus.NOT_FOUND.value(),
                LocalDateTime.now().format(TIMESTAMP_FORMATTER)
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    /**
     * 处理运行时异常
     * 返回500 Internal Server Error
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntimeException(RuntimeException ex) {
        log.error("运行时异常: ", ex);
        ErrorResponse response = new ErrorResponse(
                "INTERNAL_SERVER_ERROR",
                "服务器内部错误，请稍后重试",
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                LocalDateTime.now().format(TIMESTAMP_FORMATTER)
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    /**
     * 处理所有其他未捕获异常
     * 返回500 Internal Server Error
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception ex) {
        log.error("未知异常: ", ex);
        ErrorResponse response = new ErrorResponse(
                "INTERNAL_SERVER_ERROR",
                "服务器内部错误，请稍后重试",
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                LocalDateTime.now().format(TIMESTAMP_FORMATTER)
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
