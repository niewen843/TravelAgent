package com.niewen.travelagent.service.impl;

import com.niewen.travelagent.entity.AuditEvent;
import com.niewen.travelagent.mapper.AuditEventMapper;
import com.niewen.travelagent.service.AuditService;
import com.niewen.travelagent.vo.AuditEventVO;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 将业务审计参数转换为实体；JSON、SQL 和敏感请求内容都不在这里拼接。 */
@Service
public class AuditServiceImpl implements AuditService {
    private final AuditEventMapper events;

    public AuditServiceImpl(AuditEventMapper events) {
        this.events = events;
    }

    @Override
    @Transactional
    public void record(Long actor, Long target, String action, String result, String traceId) {
        AuditEvent event = new AuditEvent();
        // 操作者和目标数据所属用户分开填写，管理员看他人账号时二者不会混淆。
        event.setActorUserId(actor);
        event.setTargetUserId(target);
        event.setAction(action);
        event.setResult(result);
        event.setTraceId(traceId);
        // 默认事务传播 REQUIRED：注册/改密时加入当前事务，其余调用独立提交。
        events.appendEvent(event);
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public List<AuditEventVO> list(Long target, long beforeId, int limit) {
        return events.listEvents(target, beforeId, limit);
    }
}
