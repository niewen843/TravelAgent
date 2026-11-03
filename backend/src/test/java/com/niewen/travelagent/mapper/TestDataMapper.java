package com.niewen.travelagent.mapper;

import com.niewen.travelagent.vo.AuditEventVO;
import org.apache.ibatis.annotations.Param;

/** 仅测试 classpath 中存在：创建反例、验证外键/检查约束，并清理测试自己的记录。 */
public interface TestDataMapper {
    int deleteUser(@Param("id") long id);
    int removeAuditForUser(@Param("id") long id);
    int removeAuditByTrace(@Param("trace") String trace);
    int countAdminUserViews(@Param("actor") long actor, @Param("target") long target);
    int setUserRole(@Param("id") long id, @Param("value") String value);
    int setUserStatus(@Param("id") long id, @Param("value") String value);
    int setUserVersion(@Param("id") long id, @Param("value") long value);
    int setMustChangePassword(@Param("id") long id, @Param("value") int value);
    int expireTemporaryPassword(@Param("id") long id);
    int setPasswordHash(@Param("id") long id, @Param("value") String value);
    int setAuditActor(@Param("trace") String trace, @Param("id") long id);
    int setAuditTarget(@Param("trace") String trace, @Param("id") long id);
    int setAuditResult(@Param("trace") String trace, @Param("value") String value);
    int setAuditResourceType(@Param("trace") String trace, @Param("value") String value);
    int setAuditMetadata(@Param("trace") String trace, @Param("value") String value);
    AuditEventVO findAuditByTrace(@Param("trace") String trace);
}
