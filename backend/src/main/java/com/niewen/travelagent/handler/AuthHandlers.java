package com.niewen.travelagent.handler;

import com.niewen.travelagent.config.properties.SecurityProperties;
import com.niewen.travelagent.constant.SessionConstants;
import com.niewen.travelagent.entity.AppUser;
import com.niewen.travelagent.security.SessionPrincipal;
import com.niewen.travelagent.service.AuditService;
import com.niewen.travelagent.service.UserService;
import com.niewen.travelagent.utils.JsonResponseUtils;
import com.niewen.travelagent.utils.RequestUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Instant;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

/** Security 的 JSON 回调与审计，配置类只引用回调，不再承载大段认证后业务逻辑。 */
@Component
public class AuthHandlers {
    private final UserService users;
    private final AuditService audit;
    private final JsonResponseUtils responses;
    private final SecurityProperties properties;

    public AuthHandlers(UserService users, AuditService audit, JsonResponseUtils responses, SecurityProperties properties) {
        this.users = users;
        this.audit = audit;
        this.responses = responses;
        this.properties = properties;
    }

    public void loginSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException {
        SessionPrincipal verified = (SessionPrincipal) authentication.getPrincipal();
        AppUser user = users.requireAccount(Long.parseLong(authentication.getName()));
        // 密码比对期间可能发生改密/禁用/撤销角色，不能把旧凭证绑定到新的账号版本。
        boolean stillValid = verified.authVersion() == user.getAuthVersion() && "ACTIVE".equals(user.getStatus())
                && authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_" + user.getRole()));
        if (!stillValid) {
            request.getSession().invalidate();
            SecurityContextHolder.clearContext();
            responses.error(response, 401, "INVALID_CREDENTIALS", "用户名或密码不正确");
            return;
        }
        HttpSession session = request.getSession();
        // Spring 的标准认证过滤器已经轮换 Session ID 并擦除了主体密码，再补充失效检查元数据。
        session.setAttribute(SessionConstants.USER_ID, user.getId());
        session.setAttribute(SessionConstants.AUTH_VERSION, verified.authVersion());
        // Redis 的 24 小时空闲期限之外，再保存不可滑动延长的绝对截止时间。
        session.setAttribute(SessionConstants.EXPIRES_AT,
                Instant.now().plus(properties.getAbsoluteSessionTimeout()).toEpochMilli());
        responses.json(response, users.recordSuccessfulLogin(user.getId(), RequestUtils.traceId(request)));
    }

    public void loginFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException error)
            throws IOException {
        // 失败时不能确认操作者身份；不保存提交的用户名、密码或具体认证异常。
        audit.record(null, null, "LOGIN", "FAILED", RequestUtils.traceId(request));
        responses.error(response, 401, "INVALID_CREDENTIALS", "用户名或密码不正确");
    }

    public void logoutAudit(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        if (authentication != null) {
            long id = Long.parseLong(authentication.getName());
            audit.record(id, id, "LOGOUT", "SUCCESS", RequestUtils.traceId(request));
        }
        // 实际 Session 销毁和认证上下文清理由 Spring 的 LogoutHandler 负责，不在此重复实现。
    }

    public void unauthenticated(HttpServletRequest request, HttpServletResponse response, AuthenticationException error)
            throws IOException {
        // API 不跳转 HTML 登录页面，前端根据 401 跳转自己的登录路由。
        responses.error(response, 401, "UNAUTHENTICATED", "请先登录");
    }

    public void accessDenied(HttpServletRequest request, HttpServletResponse response, AccessDeniedException error)
            throws IOException {
        if (error instanceof CsrfException) {
            // 前端仅对这个明确发生在业务执行之前的错误，刷新 token 后重试一次。
            responses.error(response, 403, "CSRF_INVALID", "页面验证已过期，请重试");
            return;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Long actor = authentication == null || "anonymousUser".equals(authentication.getPrincipal())
                ? null : Long.valueOf(authentication.getName());
        audit.record(actor, null, "ACCESS_DENIED", "DENIED", RequestUtils.traceId(request));
        responses.error(response, 403, "FORBIDDEN", "没有访问权限");
    }
}
