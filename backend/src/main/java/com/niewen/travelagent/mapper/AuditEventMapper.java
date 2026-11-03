package com.niewen.travelagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.niewen.travelagent.entity.AuditEvent;
import com.niewen.travelagent.vo.AuditEventVO;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** 审计持久化接口；业务代码使用追加和只读查询方法，对应 SQL 在同名 XML 中。 */
public interface AuditEventMapper extends BaseMapper<AuditEvent> {
    /** 追加一条审计，actor/target 可为空，数据库约束保证有效的用户引用。 */
    int appendEvent(AuditEvent entity);
    /** target 为空时查看全部，否则按数据所属用户过滤；beforeId 用于降序游标分页。 */
    List<AuditEventVO> listEvents(@Param("target") Long target, @Param("beforeId") long beforeId,
                                  @Param("limit") int limit);
}
