package com.niewen.travelagent.exception;

import org.springframework.http.HttpStatus;

/** 可预期的业务失败，由 ApiExceptionHandler 转换为 HTTP 状态和稳定的错误码。 */
public class ApiException extends RuntimeException {
    /** 区分参数错误、未登录、资源不存在或并发冲突，不将所有业务失败包装成 500。 */
    private final HttpStatus status;
    /** 前端按错误码决定交互行为，避免依赖可能修改的中文文案。 */
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        // message 直接用于公开响应，调用者不得传入 SQL、密码或其他内部诊断信息。
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }
}
