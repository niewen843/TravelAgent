package com.niewen.travelagent.utils;

import com.niewen.travelagent.entity.AppUser;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

/** 随机临时密码和到期规则；不保存明文，不依赖进程内缓存，因此重启后有效期仍然成立。 */
public final class TemporaryPasswordUtils {
    private static final SecureRandom RANDOM = new SecureRandom();
    private TemporaryPasswordUtils() {}

    public static String generate() {
        // 24 个随机字节提供 192 bit 随机性；URL 安全编码后为 32 位 ASCII，符合 BCrypt 长度限制。
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static boolean isTemporary(AppUser user) {
        // USER 只能由管理重置流程设置强制改密。初始 ADMIN 的改密不适用临时密码时限。
        return "USER".equals(user.getRole()) && Boolean.TRUE.equals(user.getMustChangePassword());
    }

    public static Instant expiresAt(AppUser user, Duration lifetime) {
        // 数据库 DATETIME 使用 UTC；重置操作必须同步更新 password_changed_at。
        return user.getPasswordChangedAt().toInstant(ZoneOffset.UTC).plus(lifetime);
    }

    public static boolean isExpired(AppUser user, Duration lifetime) {
        return isTemporary(user) && (user.getPasswordChangedAt() == null
                || !Instant.now().isBefore(expiresAt(user, lifetime)));
    }
}
