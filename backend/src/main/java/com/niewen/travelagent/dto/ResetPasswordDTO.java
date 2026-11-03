package com.niewen.travelagent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 仅接收管理员本人的当前密码；目标账号来自路径，临时密码由服务端生成。 */
public record ResetPasswordDTO(
        @NotBlank(message = "请填写管理员当前密码")
        @Size(max = 72, message = "管理员密码过长") String adminPassword) {
    @Override
    public String toString() { return "ResetPasswordDTO[credentials redacted]"; }
}
