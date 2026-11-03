package com.niewen.travelagent.filter;

import com.niewen.travelagent.config.properties.SecurityProperties;
import com.niewen.travelagent.constant.SessionConstants;
import com.niewen.travelagent.entity.AppUser;
import com.niewen.travelagent.service.UserService;
import com.niewen.travelagent.utils.JsonResponseUtils;
import com.niewen.travelagent.utils.TemporaryPasswordUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Instant;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Redis 中有 Session 不等于账号仍有权限；在恢复身份后、资源授权前重新检查数据库事实。 */
public class SessionGuardFilter extends OncePerRequestFilter {
    private static final Set<String> PASSWORD_CHANGE_ROUTES = Set.of(
            "/api/auth/me", "/api/auth/csrf", "/api/auth/password", "/api/auth/logout");
    private final UserService users;
    private final JsonResponseUtils responses;
    private final SecurityProperties properties;

    public SessionGuardFilter(UserService users, JsonResponseUtils responses, SecurityProperties properties) {
        this.users = users;
        this.responses = responses;
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            // 未登录请求不在此处决定 401/403，后续 CSRF 和路径授权统一处理。
            chain.doFilter(request, response);
            return;
        }
        HttpSession session = request.getSession(false);
        Long userId = session == null ? null : (Long) session.getAttribute(SessionConstants.USER_ID);
        Long version = session == null ? null : (Long) session.getAttribute(SessionConstants.AUTH_VERSION);
        Long expiresAt = session == null ? null : (Long) session.getAttribute(SessionConstants.EXPIRES_AT);
        AppUser user = userId == null ? null : users.findAccountById(userId).orElse(null);

        // 同时检查主体 ID、账号状态、版本、角色与绝对期限。
        // 版本处理改密；角色复核还可发现直接 SQL 修改角色但忘记递增版本的情况。
        boolean valid = user != null && "ACTIVE".equals(user.getStatus())
                && version != null && version.equals(user.getAuthVersion())
                && expiresAt != null && Instant.now().toEpochMilli() < expiresAt
                && authentication.getName().equals(Long.toString(user.getId()))
                && authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_" + user.getRole()));
        // 临时密码会话即使仍在 Redis 中，也不能在临时密码到期后继续改密。
        boolean temporaryExpired = user != null
                && TemporaryPasswordUtils.isExpired(user, properties.getTemporaryPasswordLifetime());
        if (!valid || temporaryExpired) {
            // 先销毁 Session 并清理当前线程身份，不能只返回错误而保留认证上下文。
            if (session != null) session.invalidate();
            SecurityContextHolder.clearContext();
            responses.error(response, 401, temporaryExpired ? "TEMPORARY_PASSWORD_EXPIRED" : "SESSION_EXPIRED",
                    temporaryExpired ? "临时密码已过期，请联系管理员重新重置" : "登录已失效，请重新登录");
            return;
        }
        if (Boolean.TRUE.equals(user.getMustChangePassword()) && !PASSWORD_CHANGE_ROUTES.contains(request.getRequestURI())) {
            // 所有待改密用户只允许查看身份、获取 CSRF、改密或退出；绝不开放管理查询。
            responses.error(response, 403, "PASSWORD_CHANGE_REQUIRED", "请先修改密码");
            return;
        }
        chain.doFilter(request, response);
    }
}
