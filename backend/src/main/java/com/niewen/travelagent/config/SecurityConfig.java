package com.niewen.travelagent.config;

import com.niewen.travelagent.config.properties.SecurityProperties;
import com.niewen.travelagent.filter.AuthRateLimitFilter;
import com.niewen.travelagent.filter.RequestMetadataFilter;
import com.niewen.travelagent.filter.SessionGuardFilter;
import com.niewen.travelagent.handler.AuthHandlers;
import com.niewen.travelagent.service.UserService;
import com.niewen.travelagent.utils.JsonResponseUtils;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.csrf.CsrfFilter;

/** 安全规则配置：定义授权边界、标准认证机制以及过滤器顺序，具体业务回调位于 handler。 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    public PasswordEncoder passwordEncoder(SecurityProperties properties) {
        // {bcrypt} 算法前缀便于将来升级编码器，现有密码无需在数据库中保存算法之外的明文信息。
        return new DelegatingPasswordEncoder("bcrypt", Map.of("bcrypt", new BCryptPasswordEncoder(properties.getBcryptStrength())));
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, UserService users, AuthHandlers handlers,
            StringRedisTemplate redis, JsonResponseUtils responses, SecurityProperties properties) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                // permitAll 只免身份检查，不免 POST 的 CSRF 检查。
                .requestMatchers("/api/auth/csrf", "/api/auth/register", "/api/auth/login").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/**").authenticated()
                .anyRequest().denyAll());
        // SPA 自己维护返回路径，不把失败请求缓存为浏览器 HTML 重定向，也不开启 Basic 认证。
        http.requestCache(AbstractHttpConfigurer::disable);
        http.httpBasic(AbstractHttpConfigurer::disable);
        http.securityContext(context -> context.requireExplicitSave(true));
        // 标准表单认证过滤器负责保存上下文；成功时轮换 ID，防止匿名 Session 被固定后接管账号。
        http.sessionManagement(session -> session.sessionFixation(fixation -> fixation.changeSessionId()));
        http.csrf(csrf -> {}); // 保留 Session 令牌仓库及默认掩码处理，注册、登录和退出都受保护。
        http.formLogin(form -> form.loginProcessingUrl("/api/auth/login")
                .successHandler(handlers::loginSuccess)
                .failureHandler(handlers::loginFailure));
        http.logout(logout -> logout.logoutUrl("/api/auth/logout")
                .addLogoutHandler(handlers::logoutAudit)
                .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)));
        http.exceptionHandling(errors -> errors
                .authenticationEntryPoint(handlers::unauthenticated)
                .accessDeniedHandler(handlers::accessDenied));

        // 顺序：追踪/禁止缓存 -> 恢复 SecurityContext -> 检查账号版本 -> CSRF -> 限流 -> 认证/资源授权。
        // 手动实例化这些过滤器，不加 @Component，避免又被 Servlet 容器注册而执行两遍。
        http.addFilterBefore(new RequestMetadataFilter(), SecurityContextHolderFilter.class);
        http.addFilterAfter(new SessionGuardFilter(users, responses, properties), SecurityContextHolderFilter.class);
        http.addFilterAfter(new AuthRateLimitFilter(redis, responses, properties), CsrfFilter.class);
        return http.build();
    }
}
