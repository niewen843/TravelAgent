package com.niewen.travelagent.service;

import com.niewen.travelagent.vo.AuditEventVO;
import java.util.List;

/** 审计服务只提供追加与读取，不向业务暴露删除/修改能力。 */
public interface AuditService {
    void record(Long actor, Long target, String action, String result, String traceId);
    List<AuditEventVO> list(Long target, long beforeId, int limit);
}
