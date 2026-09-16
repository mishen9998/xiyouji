package com.xiyouji.exception;

/** No evidence that this command committed or rolled back; never automatically re-execute. */
public class ResultUnknownException extends BusinessException {
    public ResultUnknownException() {
        super("RESULT_UNKNOWN", "操作结果尚未确认，请同步权威状态并查询原命令回执；不要自动重试或更换命令号。幂等保护有时限。", 409);
    }
}
