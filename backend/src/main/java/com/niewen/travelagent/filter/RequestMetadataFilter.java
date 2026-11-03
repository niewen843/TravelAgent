package com.niewen.travelagent.filter;

import com.niewen.travelagent.constant.SessionConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.web.filter.OncePerRequestFilter;

/** 为包括认证失败在内的响应设置可信追踪 ID 和禁止缓存的策略。 */
public class RequestMetadataFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // 不使用客户端自带 X-Trace-Id，避免攻击者伪造审计关联。
        String traceId = UUID.randomUUID().toString();
        request.setAttribute(SessionConstants.TRACE_ID, traceId);
        response.setHeader("X-Trace-Id", traceId);
        // 浏览器/代理不得缓存私人 API 响应，避免退出后展示旧账号内容。
        response.setHeader("Cache-Control", "no-store");
        chain.doFilter(request, response);
    }
}
