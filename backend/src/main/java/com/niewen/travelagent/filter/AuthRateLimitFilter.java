package com.niewen.travelagent.filter;

import com.niewen.travelagent.config.properties.SecurityProperties;
import com.niewen.travelagent.utils.JsonResponseUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** 登录/注册及管理员重置的共享限流，位于 CSRF 之后、密码认证之前，减少暴力尝试和哈希计算开销。 */
public class AuthRateLimitFilter extends OncePerRequestFilter {
    // Lua 不是 SQL；INCR 与首次设置到期时间在 Redis 中原子执行，避免留下永不过期的计数。
    private static final DefaultRedisScript<Long> INCREMENT = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end
            return count
            """, Long.class);
    private final StringRedisTemplate redis;
    private final JsonResponseUtils responses;
    private final SecurityProperties properties;

    public AuthRateLimitFilter(StringRedisTemplate redis, JsonResponseUtils responses, SecurityProperties properties) {
        this.redis = redis;
        this.responses = responses;
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        // 不能只匹配纯数字 ID：Spring 也可解析 +42 或编码后的路径，限流必须覆盖这些等价写法。
        boolean reset = path.startsWith("/api/admin/users/") && path.endsWith("/password-reset");
        if (!"POST".equals(request.getMethod()) || !(path.equals("/api/auth/login") || path.equals("/api/auth/register") || reset)) {
            chain.doFilter(request, response);
            return;
        }
        if (reset) {
            checkResetLimit(request, response, chain);
            return;
        }
        try {
            // 不信任任意 X-Forwarded-For。生产反代须在可信代理边界配置原始客户端地址。
            int ipLimit = path.endsWith("register") ? properties.getRegisterIpLimit() : properties.getLoginIpLimit();
            boolean allowed = allow(path + ":ip:" + hash(request.getRemoteAddr()), ipLimit);
            if (path.endsWith("login")) {
                String username = request.getParameter("username");
                String password = request.getParameter("password");
                if (username == null || username.length() > 64 || password == null
                        || password.getBytes(StandardCharsets.UTF_8).length > 72 || password.isEmpty()) {
                    // 在 BCrypt 之前拒绝过长输入，尤其防止 UTF-8 多字节密码被截断后仍通过认证。
                    responses.error(response, 400, "INVALID_LOGIN", "请检查用户名和密码");
                    return;
                }
                // 登录名先规范化再取摘要，换大小写不能绕过账号限额，也不在 Redis key 中保存原登录名。
                allowed &= allow("account:" + hash(username.trim().toLowerCase(Locale.ROOT)), properties.getLoginAccountLimit());
            }
            if (!allowed) {
                String waitSeconds = Integer.toString(properties.getRateLimitWindowSeconds());
                response.setHeader("Retry-After", waitSeconds);
                responses.error(response, 429, "RATE_LIMITED", "尝试次数过多，请稍后再试");
                return;
            }
        } catch (DataAccessException error) {
            // 限流存储不可用时拒绝本次尝试，不能降级为无限次数登录。
            responses.error(response, 503, "AUTH_UNAVAILABLE", "登录服务暂时不可用，请稍后再试");
            return;
        }
        chain.doFilter(request, response);
    }

    private void checkResetLimit(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        // 只包围 Redis 访问，不吞掉后续业务的数据库异常；非管理员仍由路径授权统一拒绝。
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()))) {
            try {
                // 每位管理员共享额度，切换目标用户不能绕过；密码不写入 key，也不在过滤器读取请求体。
                if (!allow("admin-reset:" + authentication.getName(), properties.getAdminResetLimit())) {
                    response.setHeader("Retry-After", Integer.toString(properties.getRateLimitWindowSeconds()));
                    responses.error(response, 429, "RATE_LIMITED", "重置尝试次数过多，请稍后再试");
                    return;
                }
            } catch (DataAccessException error) {
                responses.error(response, 503, "AUTH_UNAVAILABLE", "身份验证服务暂时不可用，请稍后再试");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private boolean allow(String key, int maximum) {
        Long count = redis.execute(INCREMENT, List.of(properties.getRateLimitNamespace() + ":" + key),
                Integer.toString(properties.getRateLimitWindowSeconds()));
        return count != null && count <= maximum;
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            // SHA-256 是 JDK 必备算法，缺失属于运行环境故障而不是用户输入错误。
            throw new IllegalStateException(impossible);
        }
    }
}
