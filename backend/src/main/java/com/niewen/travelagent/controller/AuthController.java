package com.niewen.travelagent.controller;

import com.niewen.travelagent.dto.ChangePasswordDTO;
import com.niewen.travelagent.dto.RegisterDTO;
import com.niewen.travelagent.service.SessionService;
import com.niewen.travelagent.service.UserService;
import com.niewen.travelagent.utils.RequestUtils;
import com.niewen.travelagent.vo.CsrfVO;
import com.niewen.travelagent.vo.UserVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

/** HTTP 账号入口；登录和退出由 Security 标准过滤器处理，避免自行实现密码认证流程。 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserService users;
    private final SessionService sessions;

    public AuthController(UserService users, SessionService sessions) {
        this.users = users;
        this.sessions = sessions;
    }

    @GetMapping("/csrf")
    public CsrfVO csrf(CsrfToken token) {
        // Spring 延迟生成 token；getToken() 会触发生成并保存到 Session。
        // 返回的掩码 token 由前端保存在内存中，下一次写请求放到 headerName 指定的请求头。
        return new CsrfVO(token.getHeaderName(), token.getToken());
    }

    @GetMapping("/me")
    public UserVO me(Principal principal) {
        // 当前用户只能来自已经认证的 Principal，接口没有可指定其他用户的 ID 参数。
        return users.currentUser(RequestUtils.userId(principal));
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserVO register(@Valid @RequestBody RegisterDTO input, HttpServletRequest request) {
        // @Valid 先做字段校验，服务再做规范化/密码字节长度检查及事务写入。
        return users.register(input, RequestUtils.traceId(request));
    }

    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void password(@Valid @RequestBody ChangePasswordDTO input, Principal principal, HttpServletRequest request) {
        long userId = RequestUtils.userId(principal);
        // 先提交 MySQL 的新哈希和 authVersion，之后才撤销 Redis 会话。
        users.changePassword(userId, input, RequestUtils.traceId(request));
        sessions.revokeAll(userId);
        // 请求自身的 Session 包装对象仍可能存在，显式销毁以免请求结束时重新写回。
        if (request.getSession(false) != null) request.getSession(false).invalidate();
        SecurityContextHolder.clearContext();
    }
}
