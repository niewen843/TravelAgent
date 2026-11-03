package com.niewen.travelagent;

import com.niewen.travelagent.entity.AppUser;
import com.niewen.travelagent.entity.AuditEvent;
import com.niewen.travelagent.mapper.AppUserMapper;
import com.niewen.travelagent.mapper.AuditEventMapper;
import com.niewen.travelagent.mapper.TestDataMapper;
import com.niewen.travelagent.utils.UserConverter;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/** 通过 Mapper XML 验证手工建表脚本的唯一键、外键和 CHECK；所有样例数据随事务回滚。 */
@SpringBootTest
@Transactional
class IdentitySchemaTests {
    @Autowired private AppUserMapper users;
    @Autowired private AuditEventMapper events;
    @Autowired private TestDataMapper data;

    @Test
    void userDefaultsAndUniqueUsernameAreEnforced() {
        String username = username();
        long id = insertUser(username);
        AppUser user = users.findById(id).orElseThrow();
        assertEquals("USER", user.getRole());
        assertEquals("ACTIVE", user.getStatus());
        assertEquals(1L, user.getAuthVersion());
        assertThrows(DataIntegrityViolationException.class, () -> insertUser(username));
    }

    @Test
    void invalidAccountAttributesAreRejected() {
        long id = insertUser(username());
        assertRejected(3819, () -> data.setUserRole(id, "SUPERUSER"));
        assertRejected(3819, () -> data.setUserStatus(id, "UNKNOWN"));
        assertRejected(3819, () -> data.setUserVersion(id, 0));
        assertRejected(3819, () -> data.setMustChangePassword(id, 2));
        assertRejected(3819, () -> data.setPasswordHash(id, ""));
        assertRejected(3819, () -> insertUser("UPPERCASE_" + UUID.randomUUID()));
        assertRejected(3819, () -> insertUser(username() + " "));
    }

    @Test
    void adminAuditKeepsActorAndTargetSeparateAndProtectsReferencedAccounts() {
        long adminId = insertUser(username());
        long userId = insertUser(username());
        data.setUserRole(adminId, "ADMIN");
        String trace = UUID.randomUUID().toString();
        appendAudit(adminId, userId, "ADMIN_VIEW_TRIP", "SUCCESS", trace);
        var event = data.findAuditByTrace(trace);
        assertEquals(adminId, event.actorUserId());
        assertEquals(userId, event.targetUserId());
        assertThrows(DataIntegrityViolationException.class, () -> data.deleteUser(adminId));
        assertThrows(DataIntegrityViolationException.class, () -> data.deleteUser(userId));
        assertThrows(DataIntegrityViolationException.class, () -> data.setAuditActor(trace, -1));
        assertThrows(DataIntegrityViolationException.class, () -> data.setAuditTarget(trace, -1));
    }

    @Test
    void unauthenticatedAuditIsSupportedAndInvalidAuditValuesAreRejected() {
        String trace = UUID.randomUUID().toString();
        appendAudit(null, null, "LOGIN_FAILED", "FAILED", trace);
        assertRejected(3819, () -> data.setAuditResult(trace, "UNKNOWN"));
        assertRejected(3819, () -> data.setAuditResourceType(trace, "trip"));
        assertRejected(3140, () -> data.setAuditMetadata(trace, "invalid-json"));
    }

    private void assertRejected(int errorCode, org.junit.jupiter.api.function.Executable operation) {
        // MySQL CHECK 的 SQLState 为 HY000，不固定假定 Spring 的异常子类，检查真实 MySQL 错误码。
        DataAccessException exception = assertThrows(DataAccessException.class, operation);
        SQLException cause = assertInstanceOf(SQLException.class, exception.getMostSpecificCause());
        assertEquals(errorCode, cause.getErrorCode());
    }

    private long insertUser(String username) {
        AppUser user = UserConverter.newAccount(username, "数据库约束测试", "{test}not-a-login-credential", "USER", false);
        users.insertAccount(user);
        return user.getId();
    }

    private void appendAudit(Long actor, Long target, String action, String result, String trace) {
        AuditEvent event = new AuditEvent();
        event.setActorUserId(actor);
        event.setTargetUserId(target);
        event.setAction(action);
        event.setResult(result);
        event.setTraceId(trace);
        events.appendEvent(event);
    }

    private String username() { return "schema_test_" + UUID.randomUUID(); }
}
