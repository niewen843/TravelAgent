package com.niewen.travelagent.config.properties;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** 类型化安全配置，对应 application.yaml 的 travel.security，避免散落的 @Value 字符串。 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "travel.security")
public class SecurityProperties {
    /** 从登录时刻开始计算的绝对有效期，不会因 Redis 滑动 TTL 而延长。 */
    private Duration absoluteSessionTimeout = Duration.ofDays(7);
    /** BCrypt 工作因子，越大越慢；生产应先测量目标机器的认证耗时。 */
    @Min(4) @Max(31)
    private int bcryptStrength = 12;
    /** Redis 限流键前缀，测试使用独立前缀避免污染本地登录额度。 */
    @NotBlank
    private String rateLimitNamespace = "travel:auth-limit";
    /** 固定计数窗口，单位秒；同时用于 Redis 过期时间和 HTTP Retry-After。 */
    @Min(1)
    private int rateLimitWindowSeconds = 900;
    /** 一个规范化登录名在窗口内的尝试上限，来源 IP 不同也合并计数。 */
    @Min(1)
    private int loginAccountLimit = 10;
    /** 同一来源地址在窗口内的登录上限，防止批量尝试不同账号。 */
    @Min(1)
    private int loginIpLimit = 30;
    /** 同一来源地址在窗口内的注册上限，不与登录额度混用。 */
    @Min(1)
    private int registerIpLimit = 5;

    /** 管理员随机临时密码的有效期；从数据库记录的重置时间起算，登录不会延长。 */
    private Duration temporaryPasswordLifetime = Duration.ofMinutes(30);
    /** 每个管理员在一个限流窗口内最多提交的重置次数，包含密码验证失败的尝试。 */
    @Min(1)
    private int adminResetLimit = 5;

    @AssertTrue(message = "temporary-password-lifetime 必须大于零")
    public boolean isTemporaryLifetimePositive() {
        return temporaryPasswordLifetime != null && !temporaryPasswordLifetime.isNegative()
                && !temporaryPasswordLifetime.isZero();
    }

    @AssertTrue(message = "absolute-session-timeout 必须大于零")
    public boolean isAbsoluteTimeoutPositive() {
        // 在应用启动阶段校验，防止负数配置让所有用户刚登录就立即过期。
        return absoluteSessionTimeout != null && !absoluteSessionTimeout.isNegative() && !absoluteSessionTimeout.isZero();
    }
}
