package com.niewen.travelagent.utils;

import com.niewen.travelagent.constant.SessionConstants;
import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;

/** 控制器共用的请求元数据提取，不从客户端请求体读取当前操作者 ID。 */
public final class RequestUtils {
    private RequestUtils() {}

    public static long userId(Principal principal) {
        // Security 的 principal name 是数据库 ID，不是用户提交的登录名；鉴权后才能调用。
        return Long.parseLong(principal.getName());
    }

    public static String traceId(HttpServletRequest request) {
        // 此属性由 RequestMetadataFilter 生成，客户端自带的追踪头不会直接作为可信值使用。
        return (String) request.getAttribute(SessionConstants.TRACE_ID);
    }
}
