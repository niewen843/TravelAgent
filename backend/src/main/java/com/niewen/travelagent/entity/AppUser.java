package com.niewen.travelagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** app_user 表实体，仅在服务和持久化层内部传递；HTTP 响应使用 UserVO。 */
@Getter
@Setter
@TableName("app_user")
public class AppUser {
    /** 数据库自增 ID，同时是 Session 中稳定的用户身份。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 规范化登录名：去除首尾空格、统一小写，数据库有唯一约束。 */
    private String normalizedUsername;
    /** 展示昵称，不参与登录或资源归属判断。 */
    private String nickname;
    /** 带算法前缀的密码哈希；禁止返回给客户端或写入日志。 */
    @JsonIgnore
    private String passwordHash;
    /** USER 或 ADMIN。实体不直接接收注册请求，角色由服务端指定。 */
    private String role;
    /** ACTIVE 或 DISABLED。禁用后旧 Session 也不能继续访问。 */
    private String status;
    /** 改密或权限变化时递增，和 Session 中的认证版本对比实现主动失效。 */
    private Long authVersion;
    /** 初始管理员和被重置密码的普通用户为 true：只允许先改密，不能访问业务数据。 */
    private Boolean mustChangePassword;
    /** 以下 DATETIME 字段按 UTC 保存；XML 使用 LocalDateTime 类型映射避免时区偏移。 */
    private LocalDateTime passwordChangedAt;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    // 不使用 @Data/@ToString，避免自动生成的 toString 把 passwordHash 带入日志。
}
