package com.niewen.travelagent.utils;

import com.niewen.travelagent.vo.ApiError;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** 安全过滤器早于 MVC 执行，不能交给 @RestControllerAdvice，使用本工具输出同样的 JSON 格式。 */
@Component
public class JsonResponseUtils {
    private final ObjectMapper mapper;

    public JsonResponseUtils(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public void error(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        json(response, new ApiError(code, message));
    }

    public void json(HttpServletResponse response, Object body) throws IOException {
        // 设置 UTF-8 避免中文错误文案乱码；只传入 VO，不能把用户实体或认证异常直接序列化。
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(mapper.writeValueAsString(body));
    }
}
