# 行笺 / Travel Agent

React + Spring Boot 的旅行规划应用。当前已实现登录、注册、退出、改密、Redis Session、管理员用户查询/密码重置与审计后台；旅行规划模块待开发。

- [产品方案](docs/product-spec.md)
- [系统架构](docs/technical-design.md)
- [账号表设计与 SQL](docs/auth-database.md)
- [鉴权模块、API、运行与测试](docs/auth-module.md)

需要 Java 21、Node.js、Compose 中的 MySQL 8.4 和 Redis。

首次运行先复制 `.env.example` 为 `.env`，填写数据库和 Redis 凭证，启动 Compose。通过 MySQL 客户端选择目标库，手工执行 [建表 SQL](docs/sql/auth-schema.sql)（已有这两张表时跳过）。后端不自动建表。

随后从仓库根目录启动后端，默认 local 配置会读取根目录 `.env` 和 `.env.local`：

```sh
docker compose up -d
./backend/mvnw -f backend/pom.xml spring-boot:run
```

另一个终端运行前端：

```sh
cd frontend
npm ci
npm run dev
```

开发页面默认为 http://localhost:5173，后端默认 8080，前端 `/api` 同源代理到后端。

本地已初始化的管理员用户名为 `admin`，初始凭证位于被 Git 忽略的 `.env.local`。首次登录必须修改密码。新环境初始化步骤见鉴权模块文档；建表 SQL 不包含默认密码。
