package com.niewen.travelagent.vo;

import java.time.Instant;

/** 临时密码只通过本次响应交付，不能缓存、写审计或通过查询接口重新获取。 */
public record ResetPasswordVO(long userId, String temporaryPassword, Instant expiresAt) {
    @Override
    public String toString() { return "ResetPasswordVO[credentials redacted]"; }
}
