package com.xiyouji.exception;

/**
 * 业务异常基类
 * 所有自定义业务异常都应继承此类
 */
public class BusinessException extends RuntimeException {

    private final String errorCode;
    private final String message;
    private final int httpStatus;

    /**
     * 构造业务异常
     *
     * @param errorCode  错误代码
     * @param message    错误信息
     * @param httpStatus 对应的HTTP状态码
     */
    public BusinessException(String errorCode, String message, int httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    /**
     * 构造业务异常（带原因）
     *
     * @param errorCode  错误代码
     * @param message    错误信息
     * @param httpStatus 对应的HTTP状态码
     * @param cause      原始异常
     */
    public BusinessException(String errorCode, String message, int httpStatus, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public String getErrorCode() {
        return errorCode;
    }

    @Override
    public String getMessage() {
        return message;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
