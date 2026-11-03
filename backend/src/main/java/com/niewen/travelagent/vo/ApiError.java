package com.niewen.travelagent.vo;

/**
 * 所有错误使用同一响应结构，便于前端区分会话失效、CSRF 错误和参数校验失败。
 * @param code 稳定的机器可读错误码，前端据此决定是否重试或转到登录页
 * @param message 面向用户的提示文案，不携带内部异常堆栈或数据库详情
 */
public record ApiError(String code, String message) {}
