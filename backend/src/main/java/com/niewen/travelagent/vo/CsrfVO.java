package com.niewen.travelagent.vo;

/** headerName 是写请求要携带的请求头名；token 是当前 Session 对应的掩码 CSRF 令牌。 */
public record CsrfVO(String headerName, String token) {}
