package com.niewen.travelagent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 改密必须同时验证旧密码和新密码规则；用户 ID 来自登录身份，不在请求体中传递。 */
public record ChangePasswordDTO(
        @NotBlank(message = "请填写当前密码")
        @Size(max = 72, message = "当前密码过长") String currentPassword,
        @NotBlank(message = "请填写新密码")
        @Size(max = 72, message = "新密码过长") String newPassword) {
    @Override
    public String toString() {
        return "ChangePasswordDTO[credentials redacted]";
    }
}
