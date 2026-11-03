# 登录鉴权模块实现说明

本模块建立浏览器账号闭环：注册、登录、身份恢复、退出、改密、管理员首次改密、管理员重置普通用户密码、账号与审计只读查询。旅行模块尚未实现，因此管理后台目前展示账号与审计记录，不展示尚不存在的旅行数据。

## 模块结构与 review 入口

```text
backend/.../travelagent/
  controller/           HTTP 接口、参数和登录主体提取
  dto/                  注册、改密等输入对象与格式校验
  vo/                   响应投影，禁止包含密码哈希
  entity/               与数据库表对应的实体
  mapper/               MyBatis-Plus BaseMapper 接口与 XML 方法声明
  service/              账号、审计、管理、会话服务接口
  service/impl/         业务规则、权限与事务实现
  config/               Security、Mapper 扫描、配置属性注册
  config/properties/    安全策略与管理员初始化的类型化配置
  filter/               请求追踪、会话有效性、认证限流
  handler/              认证成功/失败、退出与拒绝访问回调
  security/             UserDetailsService 与 SessionPrincipal
  runner/               可选的一次性管理员初始化入口
  utils/                账号规则、实体投影、请求与 JSON 工具
  constant/             Session 键名等共享常量
  exception/            业务异常和统一错误映射
backend/src/main/resources/
  mapper/               AppUserMapper.xml、AuditEventMapper.xml
  application*.yaml     带中文注释的通用、本地、生产配置
frontend/src/
  app/                  路由与访问守卫
  features/auth/        账号请求、身份上下文、登录注册页面与主题样式
  features/account/     账号页、修改密码页
  features/admin/       管理后台及其请求与查询缓存
  shared/api/           Cookie/CSRF 请求层
  shared/ui/            品牌、布局、密码输入框
```

前端使用 React Router 和 TanStack Query。缓存 key 包含当前账号 ID；身份变化和退出时清空私有缓存，不使用 localStorage/sessionStorage 保存账号、密码、会话或聊天。身份恢复使用请求序号，旧请求不能覆盖新身份。BroadcastChannel 通知其他标签页重新确认身份，窗口重新获得焦点时也核对登录状态。

后端调用链为 `Controller → Service 接口 → ServiceImpl → Mapper 接口 → XML → MySQL`。Controller 处理 HTTP 参数和认证主体；ServiceImpl 执行业务规则、权限与事务；实体只表示表字段，DTO/VO 分离输入和输出。Mapper 继承 MyBatis-Plus `BaseMapper`，本模块的业务查询和更新全部由 XML 定义，使用 `#{}` 参数绑定；Java 中没有 SQL 注解或 JDBC SQL 字符串。

AdminService 通过 UserService、AuditService 查询，不绕过它们访问 Mapper。管理服务和账号服务的跨账号查询均使用方法级 ADMIN 鉴权，HTTP 路径也单独限制 ADMIN。注册、改密和初始化的审计加入同一 MySQL 事务；Redis 会话撤销在改密事务提交后执行。代码注释解释角色来源、并发更新、会话版本、过滤器顺序和密码脱敏等关键步骤。

## API 契约

统一前缀 `/api`。除 CSRF、登录和注册外，所有 `/api/**` 要求登录；管理接口还要求 ADMIN 且已修改初始密码。

| 方法 | 路径 | 请求 | 成功结果 |
|---|---|---|---|
| GET | `/auth/csrf` | 无 | `{headerName, token}`，创建/复用匿名 Session |
| POST | `/auth/register` | JSON `{username,nickname,password}` | 201，账号公开信息，不自动登录 |
| POST | `/auth/login` | form-urlencoded `username,password` | 200，账号公开信息，轮换 Session ID |
| GET | `/auth/me` | 无 | 200，当前账号公开信息 |
| POST | `/auth/logout` | 无 | 204，撤销当前会话 |
| POST | `/auth/password` | JSON `{currentPassword,newPassword}` | 204，所有设备会话失效 |
| GET | `/admin/users` | `afterId=0&limit=30` | ID 升序的账号公开信息数组 |
| POST | `/admin/users/{id}/password-reset` | JSON `{adminPassword}` | `{userId,temporaryPassword,expiresAt}`，仅本次返回临时密码 |
| GET | `/admin/users/{id}` | 用户 ID | 账号公开信息，并记录目标用户审计 |
| GET | `/admin/audit-events` | 可选 `targetUserId,beforeId,limit` | ID 降序审计数组 |

所有 POST 必须携带 `/auth/csrf` 返回的 header 和 token，登录、注册、退出也不例外。token 由默认 Session CSRF repository 保存，响应返回掩码 token。登录/退出后重新取 token。前端每次写请求先取得当前 token，仅对明确的 `CSRF_INVALID` 拒绝重试一次；不自动重放其他失败写请求。

错误格式为 `{code,message}`。未登录/失效返回 JSON 401；角色权限不足返回 JSON 403；初始密码未改返回 `PASSWORD_CHANGE_REQUIRED`；注册重名返回 409；限流返回 429 及 `Retry-After: 900`。密码错误和不存在的用户名使用相同登录错误文案。账号投影不含密码哈希或 auth_version。

用户列表以最大 ID 作为下一页 `afterId`，审计以最小 ID 作为下一页 `beforeId`。limit 为 1–100，默认 30。请求追踪 ID 由后端生成，响应带 `X-Trace-Id`，不信任客户端提交的追踪 ID。

## Session、密码与权限

- 密码使用带 `{bcrypt}` 前缀的 BCrypt，当前成本 12；上线前应在实际机器测量登录耗时。密码至少 10 位，最多 72 UTF-8 字节，禁止 BCrypt 静默截断。
- 登录用户名统一 trim、小写，允许 3–64 位 ASCII 字母、数字、`_`、`-`、`.`。普通注册固定 USER，客户端提交的 role 不用于创建账号。
- 使用 Spring Session 的 Redis indexed repository，principal name 为稳定用户 ID。Spring 的 User 会在认证成功后擦除密码哈希，SessionPrincipal 只增加实际验证时的账号版本。
- Redis 空闲过期为 24 小时；绝对截止为登录后 7 天。每次认证请求校验截止时间、ACTIVE 状态、auth_version 和实际角色。Redis 活动不能延长绝对期限。
- 改密先事务提交新哈希、版本递增和审计，再撤销 Redis 中所有该用户会话。即使 Redis 删除失败，版本检查仍会拒绝旧会话。状态/角色的后续管理操作也必须递增 auth_version 并撤销会话；现有守卫还会发现直接 SQL 修改状态或角色造成的不一致。
- must_change_password=true 时，仅允许身份查询、CSRF、改密和退出，不能使用管理查询。
- 登录按账号 10 次/15 分钟、来源 IP 30 次/15 分钟；注册按 IP 5 次/15 分钟，统计包含成功尝试。Redis Lua 原子执行计数和首次到期设置，多实例共享限额；限流服务不可用时拒绝认证尝试。
- 本地 Cookie 为 travel-session，HttpOnly、SameSite=Lax、Path=/。prod profile 使用 Secure 的 __Host-travel-session，需 HTTPS 同源入口。
- 限流使用容器收到的来源地址。生产反代应在可信代理边界配置原始客户端 IP，否则同一代理后的用户会共享 IP 限额；不能直接信任任意客户端提交的 X-Forwarded-For。
- API 响应 no-store。生产同源代理，开发 Vite 将 `/api` 代理至后端，不开放通配 CORS。

管理员访问记录区分 actor_user_id 和 target_user_id；登录失败没有可确认身份时允许二者为空。审计不保存密码、Cookie 或原始请求体。应用不提供审计修改/删除接口；生产数据库权限应进一步约束审计的追加写入。

## 管理员重置密码

用户列表与详情展示稳定用户 ID、用户名和昵称；可以直接输入 ID 定位，也可以点击审计中的操作者/目标用户 ID 定位。审计列表不展示用户名。

管理员点击普通用户的“重置密码”，输入管理员本人的当前密码。请求同时接受 Session、ADMIN 权限、CSRF 及按管理员 ID 的限流校验，默认每 15 分钟最多提交 5 次，切换目标账号也共享额度。只允许操作 ACTIVE 的 USER，不能重置自己、其他管理员或已禁用用户。

验证失败写入 `ADMIN_RESET_PASSWORD / FAILED` 审计，不修改目标账号。角色/状态不允许时写入 DENIED。验证通过后，事务内锁定管理员行、复核验证时的账号版本和权限，目标账号使用版本比较更新，避免并发改密被静默覆盖。新密码哈希、版本递增、强制改密标记和 SUCCESS 审计一起提交；审计写入失败则整体回滚。事务提交后撤销目标用户所有 Redis 会话，即使撤销失败，版本检查仍阻止旧会话访问。

临时密码使用 SecureRandom 生成 24 个随机字节，编码为 32 位 ASCII；数据库仅保存 BCrypt 哈希。明文仅通过该次无缓存响应提供，不能从用户查询或审计中找回；前端面板关闭、切换用户、退出或临时密码过期后清除展示，不写查询缓存、浏览器存储或日志。响应丢失或撤销失败时，不自动重试业务请求；核对审计后重新验证管理员身份并重置，会使上一次临时密码失效。

默认有效期为 30 分钟，由 `travel.security.temporary-password-lifetime` 控制。没有新增表字段：对 USER 且 must_change_password=true 的账号，使用现有 UTC `password_changed_at + 有效期` 判断临时密码到期。重置 SQL 原子更新该时间；登录、已登录会话守卫及改密服务都会检查，到期后管理员须重新重置。初始管理员不使用这项临时密码时限。后续新增其他设置 USER 强制改密标记的流程时，必须遵守该语义或另设凭证类型字段。

临时密码认证成功只进入待改密状态：身份响应隐藏登录名、昵称及历史时间信息，仅保留改密所需身份字段；只能访问 me、csrf、password、logout，不能访问业务或管理接口。完成改密后再撤销所有会话，并重新登录。必须通过已核实用户身份的渠道交付临时密码；强制改密不能阻止拿到临时密码的人抢先设置新密码。

## 本地启动与 admin

先按 [数据库说明](auth-database.md)手工建表。数据库凭证位于已有 `.env`，本地初始 admin 凭证位于 `.env.local`（权限 0600，已被 Git 忽略）。本次已建立用户名 `admin` 的账号，首次登录要求改密；密码不写入源码、文档或日志。

首次部署创建管理员，先将 `ADMIN_USERNAME`、`ADMIN_PASSWORD` 提供给进程，仅在初始化运行设置 `ADMIN_BOOTSTRAP_ENABLED=true`。默认关闭初始化。重跑不会重置已有管理员密码；若名称属于 USER 则报错，绝不自动提升权限。

仓库根目录启动后端：

```sh
./backend/mvnw -f backend/pom.xml spring-boot:run
```

首次部署初始化时还需加载管理员凭证并启用一次性初始化：

```sh
ADMIN_BOOTSTRAP_ENABLED=true ./backend/mvnw -f backend/pom.xml spring-boot:run
```

另一个终端启动前端：

```sh
cd frontend
npm ci
npm run dev
```

### 配置来源与覆盖

`${MYSQL_PASSWORD:}` 并不会创建环境变量：它从 Spring 配置源查找同名配置，冒号后为空表示未设置时使用空值。没有密码配置时，受密码保护的数据库/Redis 连接会失败。`${MYSQL_PORT:3306}` 则表示没有配置时使用 3306。配置文件逐项写有用途及默认值说明。

| 配置来源 | 用途与读取方式 |
|---|---|
| `.env.example` | 可提交的变量模板；复制为 `.env` 后填写本地凭证 |
| `.env` | Compose 读取数据库初始化/Redis 密码；后端 local profile 也显式导入同一文件 |
| `.env.local` | 本机私有覆盖与 `ADMIN_USERNAME`、`ADMIN_PASSWORD`，Git 忽略；格式模板见 `.env.local.example` |
| 系统环境变量 | IDE Run Configuration、终端、Docker/部署系统注入；优先于文件配置 |
| `application.yaml` | 公共默认值、MyBatis XML 路径、Session/限流策略；不保存真实凭证 |
| `application-local.yaml` | 导入 `./` 与 `../` 的私有文件，兼容仓库根目录或 backend 为工作目录 |
| `application-prod.yaml` | HTTPS Cookie 配置；显式启用 prod 后不导入本地私有文件 |

本地文件均使用 `KEY=VALUE`，不写 `export`；Spring 按 properties 格式读取，值不加 shell 引号。`.env.local` 后导入，可覆盖 `.env` 的同名键。管理员初始化默认关闭：只在首次启动时设置 `ADMIN_BOOTSTRAP_ENABLED=true`，创建后恢复为 false；已有管理员不会被重置。`MYSQL_ROOT_PASSWORD` 只供 Compose 初始化 MySQL，后端使用 `MYSQL_USER/MYSQL_PASSWORD`，两者职责不同。

生产启用 `SPRING_PROFILES_ACTIVE=prod`；HTTPS 代理同时提供前端静态页面和 `/api`，SPA 路由回退到 index.html。部署 Redis 需要会话索引支持的 keyspace 通知配置和服务端网络隔离，不能公开暴露 Redis。

## 验证与当前边界

```sh
# 仓库根目录，先启动 MySQL/Redis 并手工建表；local 自动读取 .env
./backend/mvnw -f backend/pom.xml test
npm --prefix frontend run test
npm --prefix frontend run build
npm --prefix frontend run lint
```

后端测试连接真实 MySQL/Redis，用真实 HTTP Cookie 验证 CSRF、Session ID 轮换、退出 Cookie 重用失败、密码哈希不会写入 Session、跨账号管理权限、管理员首次改密、所有设备撤销、绝对过期、账号禁用及角色撤销，以及管理员重置的身份复核、CSRF、限流、审计回滚、临时密码到期和强制改密。集成测试使用独立 Redis 命名空间，只清理自身创建的账号与审计数据；CI 应配置独立测试数据库。原有数据库表和业务记录不被清理。

前端测试使用 jsdom 模拟 DOM，覆盖登录错误/成功、退出、密码显隐、注册确认、改密跳转提示、会话失效清理、路由守卫、CSRF 请求与安全重试。生产构建与 lint 已验证。浏览器工具拒绝本地访问，因此未完成截图、真实浏览器及手机视觉验收。

目前不提供自动密码找回；页面引导内测用户联系管理员进行身份核验。公开注册面向公众上线前，仍须实现已验证联系方式和一次性找回凭证。旅行私有接口尚未实现，后续所有业务查询必须使用认证用户 ID 作为 owner 条件，子表按既定 owner/trip 复合外键关联；管理员跨用户查看只能通过专用管理接口并记录审计。未来 SSE 长连接还须在连接期间定期重新验证会话。
