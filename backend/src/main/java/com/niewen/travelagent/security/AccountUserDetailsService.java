package com.niewen.travelagent.security;

import com.niewen.travelagent.config.properties.SecurityProperties;
import com.niewen.travelagent.entity.AppUser;
import com.niewen.travelagent.exception.ApiException;
import com.niewen.travelagent.service.UserService;
import com.niewen.travelagent.utils.AccountRules;
import com.niewen.travelagent.utils.TemporaryPasswordUtils;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** 将业务账号适配为 Spring Security UserDetails，不自行实现密码比对。 */
@Service
public class AccountUserDetailsService implements UserDetailsService {
    private final UserService users;
    private final SecurityProperties properties;

    public AccountUserDetailsService(UserService users, SecurityProperties properties) {
        this.users = users;
        this.properties = properties;
    }

    @Override
    public UserDetails loadUserByUsername(String suppliedUsername) {
        String normalized;
        try {
            normalized = AccountRules.username(suppliedUsername);
        } catch (ApiException invalid) {
            // 输入非法和用户不存在都转为标准认证失败，前端收到相同错误，避免暴露账号是否存在。
            throw new UsernameNotFoundException("Invalid credentials");
        }
        AppUser user = users.findAccountByUsername(normalized)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
        // 已过期的临时密码不能再建立会话；保持统一错误，避免泄露账号当前状态。
        if (TemporaryPasswordUtils.isExpired(user, properties.getTemporaryPasswordLifetime())) {
            throw new UsernameNotFoundException("Invalid credentials");
        }
        return new SessionPrincipal(user);
    }
}
