package com.niewen.travelagent.service.impl;

import com.niewen.travelagent.dto.ResetPasswordDTO;
import com.niewen.travelagent.entity.AppUser;
import com.niewen.travelagent.exception.ApiException;
import com.niewen.travelagent.service.AdminService;
import com.niewen.travelagent.service.AuditService;
import com.niewen.travelagent.service.SessionService;
import com.niewen.travelagent.service.UserService;
import com.niewen.travelagent.utils.TemporaryPasswordUtils;
import com.niewen.travelagent.vo.AuditEventVO;
import com.niewen.travelagent.vo.ResetPasswordVO;
import com.niewen.travelagent.vo.UserVO;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/** 集中管理跨账号查询；方法鉴权覆盖将来的内部调用，不能只依赖 HTTP 路径鉴权。 */
@Service
@PreAuthorize("hasRole('ADMIN')")
public class AdminServiceImpl implements AdminService {
    private final UserService users;
    private final AuditService audit;
    private final PasswordEncoder encoder;
    private final SessionService sessions;

    public AdminServiceImpl(UserService users, AuditService audit, PasswordEncoder encoder, SessionService sessions) {
        this.users = users;
        this.audit = audit;
        this.encoder = encoder;
        this.sessions = sessions;
    }

    @Override
    public ResetPasswordVO resetPassword(long actor, long target, ResetPasswordDTO input, String traceId) {
        UserVO targetUser = users.adminAccount(target);
        AppUser operator = users.requireAccount(actor);
        // 不开启外层事务：错误密码的审计必须独立提交，不能随抛出的业务异常回滚。
        if (input.adminPassword().getBytes(StandardCharsets.UTF_8).length > 72
                || !encoder.matches(input.adminPassword(), operator.getPasswordHash())) {
            audit.record(actor, target, "ADMIN_RESET_PASSWORD", "FAILED", traceId);
            throw new ApiException(HttpStatus.BAD_REQUEST, "ADMIN_PASSWORD_INCORRECT", "管理员当前密码不正确");
        }
        if (!"USER".equals(targetUser.role()) || !"ACTIVE".equals(targetUser.status())) {
            audit.record(actor, target, "ADMIN_RESET_PASSWORD", "DENIED", traceId);
            throw new ApiException(HttpStatus.FORBIDDEN, "RESET_NOT_ALLOWED", "只能重置正常状态的普通用户密码");
        }
        String temporary = TemporaryPasswordUtils.generate();
        Instant deadline = users.resetUserPassword(actor, operator.getAuthVersion(), target, temporary, traceId);
        // 此时 MySQL 已提交；即便 Redis 不可用，版本复核仍会拒绝旧会话。
        // 撤销失败时不返回成功，管理员可在服务恢复后重新重置，不提供找回上一次临时密码的接口。
        sessions.revokeAll(target);
        return new ResetPasswordVO(target, temporary, deadline);
    }

    @Override
    public List<UserVO> users(long actor, long afterId, int limit, String traceId) {
        validatePage(afterId, limit);
        audit.record(actor, null, "ADMIN_LIST_USERS", "SUCCESS", traceId);
        return users.adminList(afterId, limit);
    }

    @Override
    public UserVO user(long actor, long target, String traceId) {
        UserVO user = users.adminAccount(target);
        // 先确认目标存在，再记录成功查看。资源不存在不伪装成成功访问。
        audit.record(actor, target, "ADMIN_VIEW_USER", "SUCCESS", traceId);
        return user;
    }

    @Override
    public List<AuditEventVO> events(long actor, Long target, long beforeId, int limit, String traceId) {
        validatePage(beforeId, limit);
        if (target != null) users.adminAccount(target);
        // 查看审计也是一次管理操作，同样记录，方便追踪谁排查过哪个用户。
        audit.record(actor, target, "ADMIN_VIEW_AUDIT", "SUCCESS", traceId);
        return audit.list(target, beforeId, limit);
    }

    private void validatePage(long cursor, int limit) {
        // 游标分页避免 OFFSET 大量扫描；限制每页大小防止后台查询一次拉取全部数据。
        if (cursor < 0 || limit < 1 || limit > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGE", "分页数量需为 1–100，游标不能为负数");
        }
    }
}
