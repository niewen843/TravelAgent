package com.niewen.travelagent.security;

import com.niewen.travelagent.entity.AppUser;
import java.io.Serial;
import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

/** Spring Security 的用户主体：认证完成后继承的 CredentialsContainer 会擦除密码哈希。 */
public class SessionPrincipal extends User {
    @Serial
    private static final long serialVersionUID = 1L;
    /** 记录实际校验密码那一刻的版本，不能在校验结束后用新的账号版本代替。 */
    private final long authVersion;

    public SessionPrincipal(AppUser user) {
        // principal name 用稳定 ID。输入登录名仅用于查找，不作为数据库资源 owner 的来源。
        // enabled 由 ACTIVE 状态决定；其他账户到期/锁定字段首版没有单独业务状态。
        super(Long.toString(user.getId()), user.getPasswordHash(), "ACTIVE".equals(user.getStatus()),
                true, true, true, List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole())));
        authVersion = user.getAuthVersion();
    }

    public long authVersion() {
        return authVersion;
    }
}
