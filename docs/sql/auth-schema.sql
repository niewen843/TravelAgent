-- MySQL 8.4 手工建表脚本。先在客户端选择已创建的目标数据库，再执行。
-- 表已经存在时不要重复执行；本脚本不删表、不改已有数据、不创建管理员密码。
-- 所有 DATETIME 按 UTC 保存，密码哈希由后端 PasswordEncoder 写入。
SET NAMES utf8mb4;
SET time_zone = '+00:00';
CREATE TABLE app_user (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '稳定的用户身份 ID',
    normalized_username VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        COMMENT '规范化小写登录名，唯一',
    nickname VARCHAR(64) NOT NULL COMMENT '展示昵称，不作为认证身份',
    password_hash VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        COMMENT '带算法标识的密码哈希，禁止通过接口返回',
    role VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'USER' COMMENT 'USER 普通用户，ADMIN 管理员',
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE 可登录，DISABLED 禁用',
    auth_version BIGINT NOT NULL DEFAULT 1 COMMENT '密码或授权变化时递增，用于撤销旧会话',
    must_change_password BOOLEAN NOT NULL DEFAULT FALSE COMMENT '初始密码或重置后的临时密码必须修改后才能使用业务接口',
    password_changed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '最近改密时间，UTC',
    last_login_at DATETIME(6) NULL COMMENT '最近成功登录时间，未登录时为空，UTC',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间，UTC',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '最近修改时间，UTC',
    PRIMARY KEY (id),
    UNIQUE KEY uk_app_user_normalized_username (normalized_username),
    CONSTRAINT ck_app_user_username CHECK (
        CHAR_LENGTH(normalized_username) BETWEEN 3 AND 64
        AND normalized_username = LOWER(normalized_username)
        AND OCTET_LENGTH(normalized_username) = OCTET_LENGTH(TRIM(normalized_username))
    ),
    CONSTRAINT ck_app_user_password_hash CHECK (CHAR_LENGTH(password_hash) > 0),
    CONSTRAINT ck_app_user_role CHECK (role IN ('USER', 'ADMIN')),
    CONSTRAINT ck_app_user_status CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT ck_app_user_auth_version CHECK (auth_version >= 1),
    CONSTRAINT ck_app_user_must_change_password CHECK (must_change_password IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '普通用户与管理员共用账号表';

CREATE TABLE audit_event (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '审计事件 ID，用于游标分页',
    actor_user_id BIGINT NULL COMMENT '实际操作者，未登录事件允许为空',
    target_user_id BIGINT NULL COMMENT '被访问数据所属用户，全局操作允许为空',
    action VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '服务端定义的动作，例如 LOGIN、PASSWORD_CHANGED',
    resource_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '可选的业务资源类型，与 resource_id 同时填写',
    resource_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '可选的业务资源标识，不作多态资源外键',
    result VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'SUCCESS 成功，DENIED 权限拒绝，FAILED 失败',
    reason VARCHAR(512) NULL COMMENT '可选的问题排查原因',
    trace_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '服务端生成的请求追踪 ID，用于关联排查',
    metadata_json JSON NULL COMMENT '脱敏扩展信息，禁止保存密码、会话凭证或聊天正文',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间，UTC',
    PRIMARY KEY (id),
    KEY idx_audit_event_actor_created (actor_user_id, created_at, id),
    KEY idx_audit_event_target_created (target_user_id, created_at, id),
    KEY idx_audit_event_trace (trace_id),
    CONSTRAINT fk_audit_event_actor FOREIGN KEY (actor_user_id)
        REFERENCES app_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_audit_event_target FOREIGN KEY (target_user_id)
        REFERENCES app_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_audit_event_action CHECK (CHAR_LENGTH(TRIM(action)) > 0),
    CONSTRAINT ck_audit_event_result CHECK (result IN ('SUCCESS', 'DENIED', 'FAILED')),
    CONSTRAINT ck_audit_event_trace CHECK (CHAR_LENGTH(TRIM(trace_id)) > 0),
    CONSTRAINT ck_audit_event_resource CHECK (
        (resource_type IS NULL AND resource_id IS NULL)
        OR (resource_type IS NOT NULL AND resource_id IS NOT NULL
            AND CHAR_LENGTH(TRIM(resource_type)) > 0 AND CHAR_LENGTH(TRIM(resource_id)) > 0)
    )
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '应用追加写入的审计，区分操作者和目标用户';
