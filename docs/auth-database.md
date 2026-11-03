# 登录鉴权数据库：首批表

建表 SQL：[auth-schema.sql](sql/auth-schema.sql)。

本次实现 `app_user` 与 `audit_event`，使用 MySQL 8.4、InnoDB。SQL 放在 `docs/sql`，交由你在 MySQL 客户端中手工执行。后端没有 Flyway 依赖，`spring.sql.init.mode=never`，启动与测试都不会创建、迁移或删除表。

## 手工建表与本地运行

1. 复制根目录 `.env.example` 为 `.env`，填写数据库与 Redis 凭证；已有 `.env` 时保留原配置。
2. 执行 `docker compose up -d`。新建 Compose 数据卷时会按 `MYSQL_DATABASE` 创建数据库，已有数据卷不会重新初始化。
3. 用 MySQL 客户端连接 `.env` 对应的主机、端口和数据库，选择该库后执行 `docs/sql/auth-schema.sql`。先建 `app_user`，再建引用它的 `audit_event`；脚本已按此顺序编排。已有这两张表时不用重复执行，后续结构变更单独提供 ALTER 脚本，手工评审和执行。
4. 从仓库根目录启动后端：

```sh
./backend/mvnw -f backend/pom.xml spring-boot:run
```

默认 `local` profile 会通过 `application-local.yaml` 读取根目录 `.env` 和 `.env.local`，无需先 `source`。文件使用 `KEY=VALUE` 格式；变量名称及用途见 `.env.example` 和 [鉴权模块的配置说明](auth-module.md)。生产显式启用 `prod`，由部署系统注入变量。

JDBC 将数据库连接时区设置为 UTC；通过其他客户端写入时间时也使用 `SET time_zone = '+00:00'`。曾使用旧版本时留下的 `flyway_schema_history` 不再参与应用启动；本次不会删除已有表。

测试依赖已手工建立的表。生产 SQL 位于 MyBatis Mapper XML，测试专用写入与清理 SQL 位于测试资源 XML。`IdentitySchemaTests` 验证唯一键、角色/状态检查、审计外键及 JSON 检查，测试数据在事务结束时回滚。建议 CI 使用独立数据库并先手工执行同一建表脚本。

## 账号约束

- 登录名使用 ASCII，小写且无首尾空格，长度 3～64；后续注册服务还需限制允许的字符（建议 `a-z`、`0-9`、`_`、`-`、`.`），并在查询与保存前统一规范化。唯一键处理并发注册。
- 密码字段保存 PasswordEncoder 的编码结果（带算法标识），不能保存明文或作为 API 返回字段。建表约束不能验证密码哈希算法是否正确。
- 普通注册固定 `USER`；客户端不能指定角色。admin 与普通用户同表，admin 不代表数据库 root 账号。
- 修改密码、角色或状态时，应用在同一事务中递增 `auth_version` 并撤销 Redis 会话；后续请求还需核对版本和 ACTIVE 状态。字段本身不会自动完成会话撤销。
- 管理重置普通用户密码使用现有 password_hash、auth_version、must_change_password、password_changed_at；不新增字段，不需要执行 ALTER。USER 的强制改密标记同时表示临时密码状态，默认重置后 30 分钟到期。
- admin 后续由一次性初始化命令创建，密码从部署配置或交互输入取得，并设置 `must_change_password=true`。此建表脚本不包含固定密码或管理员账号。

## 审计与访问控制

- `actor_user_id` 是操作者，`target_user_id` 是被查看数据的归属用户。两者不能混用。未登录的登录失败可使用空 actor；目标用户未确定时 target 也为空。
- 已有审计引用的账号禁止硬删除，账号使用 DISABLED 状态保留。未来如需匿名化/删除个人信息，应另行制定处理策略。
- `resource_type` 与 `resource_id` 同时为空或同时有值。resource 是多态引用，因此不建立业务资源外键，以便业务资源删除后仍保留审计。
- 审计由服务端写入，metadata 采用字段白名单；不存密码、Cookie、Session ID 或完整聊天正文。后台不提供修改或删除审计接口。表结构本身不保证不可篡改，追加写入由服务层及数据库权限落实，生产部署可使用独立审计写入账号，仅授予 SELECT/INSERT。
- 普通业务查询必须绑定认证身份的 `owner_user_id`；管理员跨用户只读查询走专用 `/api/admin/**` 并记录审计。后续旅行子表通过 owner/trip 复合外键保证归属一致。

登录接口、Spring Security、Redis Session 和 admin 初始化现已实现，详见 [鉴权模块说明](./auth-module.md)。旅行私有接口及业务数据隔离将在旅行模块中按既定归属约束实现。
