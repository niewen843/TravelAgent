package com.niewen.travelagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** audit_event 表实体。业务只追加审计，不提供更新或删除审计的服务。 */
@Getter
@Setter
@TableName("audit_event")
public class AuditEvent {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 实际操作者。未登录的失败事件没有可靠用户身份，允许为空。 */
    private Long actorUserId;
    /** 被访问内容所属的用户，与操作者不同；用户列表等全局查询允许为空。 */
    private Long targetUserId;
    /** 服务端固定的操作代码，例如 LOGIN、ADMIN_VIEW_USER。 */
    private String action;
    /** SUCCESS、DENIED 或 FAILED。 */
    private String result;
    /** 资源类型和 ID 必须同时为空或同时填写，保留被删除资源的历史引用。 */
    private String resourceType;
    private String resourceId;
    /** 可选的排查原因，不能放入密码、Cookie 或聊天正文。 */
    private String reason;
    /** 由请求过滤器生成的追踪 ID，用于关联响应和审计。 */
    private String traceId;
    /** 可选 JSON 扩展字段，未来使用时需在服务层实施脱敏字段白名单。 */
    private String metadataJson;
    /** UTC 时间；新记录由 MySQL 默认值生成。 */
    private LocalDateTime createdAt;
}
