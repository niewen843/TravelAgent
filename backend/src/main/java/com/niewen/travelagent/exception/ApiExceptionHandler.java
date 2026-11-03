package com.niewen.travelagent.exception;

import com.niewen.travelagent.vo.ApiError;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** MVC 层异常转换为稳定的 {code,message}，不把堆栈、SQL 或输入密码返回客户端。 */
@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handle(ApiException error) {
        // 业务异常明确指定 HTTP 状态，重名用 409、无权限用 403，不能统一成 200。
        return ResponseEntity.status(error.status()).body(new ApiError(error.code(), error.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validation(MethodArgumentNotValidException error) {
        // 只输出注解中的校验文案，不能输出 BindingResult（里面包含用户的原始密码输入）。
        String message = error.getBindingResult().getFieldErrors().stream()
                .map(field -> field.getDefaultMessage()).findFirst().orElse("请检查输入内容");
        return ResponseEntity.badRequest().body(new ApiError("VALIDATION_FAILED", message));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiError> malformedInput() {
        // JSON 格式错误或 id/limit 等参数无法转换时，也保持可供前端展示的 JSON 格式。
        return ResponseEntity.badRequest().body(new ApiError("INVALID_REQUEST", "请求内容格式不正确"));
    }
}
