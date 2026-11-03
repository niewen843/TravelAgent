package com.niewen.travelagent.utils;

import com.niewen.travelagent.exception.ApiException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.http.HttpStatus;

/** 注册、改密及管理员初始化共用的输入规则，不能让不同入口各自定义不一致的规则。 */
public final class AccountRules {
    private AccountRules() {}

    public static String username(String value) {
        // Locale.ROOT 保证规范化不随服务器语言设置变化，例如避免土耳其语大小写差异。
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9_.-]{3,64}")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_USERNAME", "用户名需为 3–64 位字母、数字、下划线、短横线或点");
        }
        return normalized;
    }

    public static void password(String value) {
        // BCrypt 最多处理 72 个字节。中文等字符占多个 UTF-8 字节，因此不能只检查字符数。
        if (value == null || value.length() < 10 || value.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PASSWORD", "密码至少 10 位，且不能超过 72 个 UTF-8 字节");
        }
    }
}
