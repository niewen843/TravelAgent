package com.niewen.travelagent.constant;

/** Session 属性和请求属性名称，集中定义以防登录写入和请求校验使用不同键。 */
public final class SessionConstants {
    /** 数据库用户 ID，而非前端提交的用户名或 userId。 */
    public static final String USER_ID = "travel.userId";
    /** 实际密码验证时的版本，改密后必须与数据库中的版本不一致。 */
    public static final String AUTH_VERSION = "travel.authVersion";
    /** 从登录开始计算的 UTC 毫秒截止时间，不随请求滑动延长。 */
    public static final String EXPIRES_AT = "travel.expiresAt";
    /** 请求内的追踪属性，非登录凭证；每个请求重新生成。 */
    public static final String TRACE_ID = "travel.traceId";

    private SessionConstants() {}
}
