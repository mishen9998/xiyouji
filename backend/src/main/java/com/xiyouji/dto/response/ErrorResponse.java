package com.xiyouji.dto.response;

/**
 * 错误响应DTO
 * timestamp使用ISO-8601格式的字符串
 */
public class ErrorResponse {

    private String error;
    private String message;
    private int status;
    private String timestamp;

    public ErrorResponse() {
    }

    public ErrorResponse(String error, String message, int status, String timestamp) {
        this.error = error;
        this.message = message;
        this.status = status;
        this.timestamp = timestamp;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
}
