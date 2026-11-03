package com.niewen.travelagent.service.impl;

import com.niewen.travelagent.config.properties.SecurityProperties;
import com.niewen.travelagent.dto.ChangePasswordDTO;
import com.niewen.travelagent.dto.RegisterDTO;
import com.niewen.travelagent.entity.AppUser;
import com.niewen.travelagent.exception.ApiException;
import com.niewen.travelagent.mapper.AppUserMapper;
import com.niewen.travelagent.service.AuditService;
import com.niewen.travelagent.service.UserService;
import com.niewen.travelagent.utils.AccountRules;
import com.niewen.travelagent.utils.TemporaryPasswordUtils;
import com.niewen.travelagent.utils.UserConverter;
import com.niewen.travelagent.vo.UserVO;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 账号业务实现：校验和事务留在服务层，SQL 全部通过 AppUserMapper 的 XML 执行。 */
@Service
public class UserServiceImpl implements UserService {
    private final AppUserMapper users;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final SecurityProperties properties;

    public UserServiceImpl(AppUserMapper users, PasswordEncoder encoder, AuditService audit, SecurityProperties properties) {
        this.users = users;
        this.encoder = encoder;
        this.audit = audit;
        this.properties = properties;
    }

    @Override
    @Transactional
    public UserVO register(RegisterDTO input, String traceId) {
        // 所有入口共用规范化规则，避免大写登录名注册后无法按小写查找。
        String normalized = AccountRules.username(input.username());
        AccountRules.password(input.password());
        String nickname = input.nickname() == null ? "" : input.nickname().trim();
        if (nickname.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_NICKNAME", "请填写昵称");
        }
        // 只在完成规则检查后进行开销较大的哈希；角色固定 USER，不能从请求体获取。
        AppUser user = UserConverter.newAccount(normalized, nickname, encoder.encode(input.password()), "USER", false);
        try {
            users.insertAccount(user);
        } catch (DuplicateKeyException duplicate) {
            // 不能仅靠先查询再插入判断重名，并发注册仍可能冲突，唯一键才是最终保障。
            throw new ApiException(HttpStatus.CONFLICT, "USERNAME_TAKEN", "用户名已被使用，请换一个");
        }
        // 审计加入本事务；注册失败不会留下成功审计，审计失败也不提交账号。
        audit.record(user.getId(), user.getId(), "REGISTER", "SUCCESS", traceId);
        // 回读数据库生成的状态、版本和时间字段，再转成不含认证信息的 VO。
        return currentUser(user.getId());
    }

    @Override
    public Optional<AppUser> findAccountByUsername(String username) {
        return users.findByUsername(username);
    }

    @Override
    public Optional<AppUser> findAccountById(long id) {
        return users.findById(id);
    }

    @Override
    public AppUser requireAccount(long id) {
        // /me 和改密使用登录主体的 ID，不接受客户端指定另一个账号 ID。
        return users.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.UNAUTHORIZED, "SESSION_EXPIRED", "登录已失效，请重新登录"));
    }

    @Override
    public UserVO currentUser(long id) {
        AppUser user = requireAccount(id);
        // 待改密会话只获得路由/改密必需的身份信息，不披露昵称、登录名和历史登录时间。
        if (Boolean.TRUE.equals(user.getMustChangePassword())) {
            return new UserVO(user.getId(), "", "待改密用户", user.getRole(), user.getStatus(), true, null, null);
        }
        return UserConverter.toView(user);
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public List<UserVO> adminList(long afterId, int limit) {
        // XML 只查公开字段，即使管理员也不会通过列表接口取得密码哈希。
        return users.listUserViews(afterId, limit);
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public UserVO adminAccount(long id) {
        // 管理端不存在的目标返回 404，和当前身份失效的 401 含义不同。
        return users.findById(id).map(UserConverter::toView).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "用户不存在"));
    }

    @Override
    @Transactional
    public void changePassword(long id, ChangePasswordDTO input, String traceId) {
        AccountRules.password(input.newPassword());
        AppUser user = requireAccount(id);
        if (TemporaryPasswordUtils.isExpired(user, properties.getTemporaryPasswordLifetime())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "TEMPORARY_PASSWORD_EXPIRED", "临时密码已过期，请联系管理员重新重置");
        }
        // 当前密码必须通过同一个 PasswordEncoder 验证，不能比较明文或自行做摘要。
        if (!encoder.matches(input.currentPassword(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "WRONG_PASSWORD", "当前密码不正确");
        }
        if (encoder.matches(input.newPassword(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_UNCHANGED", "新密码不能与当前密码相同");
        }
        // WHERE auth_version 实现并发比较更新。另一个改密先提交时，本次返回冲突而不是覆盖。
        int changed = users.updatePasswordIfVersion(id, user.getAuthVersion(), encoder.encode(input.newPassword()));
        if (changed != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "ACCOUNT_CHANGED", "账号状态已变化，请重新登录");
        }
        audit.record(id, id, "PASSWORD_CHANGED", "SUCCESS", traceId);
        // 本方法结束后 MySQL 事务提交，Controller 才删除 Redis 会话。
        // Redis 删除失败时，新版本检查仍能阻止旧会话继续访问。
    }

    @Override
    @Transactional
    public UserVO recordSuccessfulLogin(long id, String traceId) {
        users.updateLastLogin(id);
        audit.record(id, id, "LOGIN", "SUCCESS", traceId);
        return currentUser(id);
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public Instant resetUserPassword(long actor, long actorVersion, long target, String temporaryPassword, String traceId) {
        // 管理员刚验证过密码，但验证后仍可能发生并发改密/禁用；锁行并复核实际验证时的版本。
        AppUser operator = users.findByIdForUpdate(actor).orElseThrow(() ->
                new ApiException(HttpStatus.UNAUTHORIZED, "SESSION_EXPIRED", "登录已失效，请重新登录"));
        if (!"ADMIN".equals(operator.getRole()) || !"ACTIVE".equals(operator.getStatus())
                || Boolean.TRUE.equals(operator.getMustChangePassword()) || operator.getAuthVersion() != actorVersion) {
            throw new ApiException(HttpStatus.CONFLICT, "ACCOUNT_CHANGED", "管理员身份已变化，请重新登录后重试");
        }
        AppUser user = requireAccount(target);
        // 临时密码仅在此处哈希，XML 不接触明文；CAS 条件同时限制角色、状态和目标版本。
        int changed = users.resetPasswordIfVersion(target, user.getAuthVersion(), encoder.encode(temporaryPassword));
        if (changed != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "ACCOUNT_CHANGED", "目标账号已变化，请刷新后重试");
        }
        // 审计失败则整个重置回滚。记录只含参与者和结果，不保存任何密码内容。
        audit.record(actor, target, "ADMIN_RESET_PASSWORD", "SUCCESS", traceId);
        return TemporaryPasswordUtils.expiresAt(requireAccount(target), properties.getTemporaryPasswordLifetime());
    }

    @Override
    @Transactional
    public void initializeAdmin(String username, String password, String traceId) {
        String normalized = AccountRules.username(username);
        AccountRules.password(password);
        AppUser existing = users.findByUsername(normalized).orElse(null);
        if (existing != null) {
            if (!"ADMIN".equals(existing.getRole())) {
                // 同名普通账号不能被启动参数静默提升为管理员。
                throw new IllegalStateException("管理员初始化用户名已被普通用户占用，请更换 ADMIN_USERNAME");
            }
            // 幂等启动：不重置已有管理员密码，也不把已完成的首次改密标记设回 true。
            return;
        }
        AppUser admin = UserConverter.newAccount(normalized, "管理员", encoder.encode(password), "ADMIN", true);
        users.insertAccount(admin);
        audit.record(admin.getId(), admin.getId(), "ADMIN_BOOTSTRAP", "SUCCESS", traceId);
    }
}
