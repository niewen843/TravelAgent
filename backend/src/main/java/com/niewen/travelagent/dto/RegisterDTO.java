package com.niewen.travelagent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 注册输入，只允许填写登录名、昵称和密码，不接收角色、状态或用户 ID。 */
public record RegisterDTO(
        // 这里检查空值和长度，字符范围及大小写规范化由 UserService 统一处理。
        @NotBlank(message = "请填写用户名")
        @Size(max = 64, message = "用户名不能超过 64 位") String username,
        @NotBlank(message = "请填写昵称")
        @Size(max = 64, message = "昵称不能超过 64 位") String nickname,
        // 字符长度只是第一层限制；服务还会检查 UTF-8 字节长度，防止 BCrypt 截断。
        @NotBlank(message = "请填写密码")
        @Size(max = 72, message = "密码过长") String password) {
    @Override
    public String toString() {
        // record 默认 toString 会输出所有字段，覆盖它以免调试日志泄露明文密码。
        return "RegisterDTO[credentials redacted]";
    }
}
