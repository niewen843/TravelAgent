package com.niewen.travelagent.vo;

import java.time.LocalDateTime;

/** 管理后台审计视图，省略原始 metadata 等内部内容，只返回定位问题所需字段。 */
public record AuditEventVO(long id, Long actorUserId, Long targetUserId, String action,
                           String result, String traceId, LocalDateTime createdAt) {}
