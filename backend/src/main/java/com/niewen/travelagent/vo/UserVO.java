package com.niewen.travelagent.vo;

import java.time.LocalDateTime;

/**
 * 账号公开视图，保持既有前端字段不变。
 * 不包含 passwordHash、authVersion，避免内部认证信息被意外序列化。
 * @param id 用户稳定 ID
 * @param username 规范化登录名
 * @param nickname 展示昵称
 * @param role 角色，供前端展示；真正授权仍由后端执行
 * @param status 账号状态
 * @param mustChangePassword 是否必须先修改初始密码或临时密码
 * @param createdAt UTC 创建时间
 * @param lastLoginAt UTC 最近登录时间，没有登录过时为空
 */
public record UserVO(long id, String username, String nickname, String role, String status,
                     boolean mustChangePassword, LocalDateTime createdAt, LocalDateTime lastLoginAt) {}
