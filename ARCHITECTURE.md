# Quarkus 全栈架构清单（v2 · PostgreSQL 版）

> 推荐方案：Quarkus 3.x 后端 + Vue 3 前端 + PostgreSQL + Redis，前后端分离，单仓库双模块

---

## 0. 选型速览

| 层 | 选型 |
|---|---|
| 后端框架 | Quarkus 3.x |
| 主数据库 | **PostgreSQL 16**（替换原 MySQL 方案） |
| 缓存 / 锁 / 限流 | Redis 7 |
| ORM | Hibernate ORM with Panache |
| DB 迁移 | Flyway |
| 安全 | SmallRye JWT |
| API 文档 | SmallRye OpenAPI 3 |
| 监控 | Micrometer + Prometheus |
| 工具 | Lombok + MapStruct |
| 前端 | Vue 3 + Vite + TypeScript |
| 前端 UI | Element Plus |
| 前端状态 | Pinia |
| 前端 HTTP | Axios + Orval（OpenAPI 自动生成） |
| 部署 | GraalVM Native Image（推荐）/ JVM |

---

## 1. 顶层仓库结构

```
Quarkus/
├── backend/                    # Quarkus 后端
├── frontend/                   # Vue 3 前端
├── docs/                       # 文档（API 约定、ER 图等）
├── docker-compose.yml          # 本地依赖中间件（PG + Redis）
└── README.md
```

> 也可拆成两个独立仓库，按团队规模选。单体仓库优势是 OpenAPI 同步、版本对齐更方便。

---

## 2. 后端架构（Quarkus 3.x）

### 2.1 核心依赖

| 类别 | 依赖 | 说明 |
|---|---|---|
| 核心 | `quarkus-resteasy-reactive-jackson` | REST API + JSON |
| ORM | `quarkus-hibernate-orm-panache` | 数据库 ORM（active record / repository） |
| 数据库 | `quarkus-jdbc-postgresql` | PostgreSQL JDBC 驱动 |
| 迁移 | `quarkus-flyway` | DB schema 版本化 |
| API 文档 | `quarkus-smallrye-openapi` | OpenAPI 3 |
| 安全 | `quarkus-smallrye-jwt` | JWT 鉴权 |
| 缓存 | `quarkus-redis-client` | Redis 客户端 |
| 配置 | `quarkus-config-yaml` | YAML 配置 |
| 健康检查 | `quarkus-smallrye-health` | `/q/health` |
| 监控 | `quarkus-micrometer-registry-prometheus` | 指标 |
| 工具 | `lombok` + `mapstruct` | DTO/VO/转换 |
| 测试 | `quarkus-junit5` + `rest-assured` | 测试 |

### 2.2 分层架构

```
┌─────────────────────────────────────┐
│  Resource 层（Controller）           │  ← @Path，对外接口
├─────────────────────────────────────┤
│  Service 层（业务逻辑）              │  ← @ApplicationScoped，事务边界
├─────────────────────────────────────┤
│  Repository / DAO 层（数据访问）     │  ← PanacheRepositoryBase
├─────────────────────────────────────┤
│  Entity 层（持久化对象）             │  ← @Entity
├─────────────────────────────────────┤
│  DTO/VO 层（传输对象）              │  ← 入参/出参，与 Entity 解耦
└─────────────────────────────────────┘
```

### 2.3 包结构示例

```
com.example.app
├── api/                    # 外部接口
│   ├── resource/          # @Path Controller
│   └── dto/               # 请求/响应 DTO
├── service/                # 业务逻辑
│   ├── impl/
│   └── mapper/             # MapStruct
├── domain/                 # 领域层
│   ├── entity/             # @Entity
│   ├── repository/         # PanacheRepository
│   └── enums/
├── infra/                  # 基础设施
│   ├── config/             # @ConfigMapping
│   ├── security/           # JWT 过滤器、权限注解
│   ├── exception/          # 全局异常处理
│   └── util/               # 工具类
└── AppApplication.java
```

### 2.4 关键约定

- **统一响应格式**：
  ```json
  { "code": 0, "message": "ok", "data": { ... } }
  ```
- **统一异常处理**：`@Provider ExceptionMapper`，业务异常抛 `BizException`
- **事务边界**：Service 方法上加 `@Transactional`，不要下沉到 Repository
- **日志**：用 `Slf4j` + 结构化字段（MDC 携带 userId / traceId）
- **校验**：`@Valid` + `jakarta.validation.constraints.*`
- **配置**：`application.yml` 放默认，`application-dev.yml` / `application-prod.yml` 分环境

### 2.5 数据库相关配置

`application.yml`：
```yaml
quarkus:
  datasource:
    db-kind: postgresql
    username: postgres
    password: postgres
    jdbc:
      url: jdbc:postgresql://localhost:5432/quarkus
      max-size: 20
  hibernate-orm:
    database:
      generation: validate       # 不让 Hibernate 自动建表，交给 Flyway
    log:
      sql: true                 # dev 环境开
  flyway:
    migrate-at-start: true
    locations: classpath:db/migration
    baseline-on-migrate: true
```

Flyway 迁移文件示例（`V1__init.sql`）：
```sql
CREATE TABLE sys_user (
    id          BIGSERIAL PRIMARY KEY,
    username    VARCHAR(64)  NOT NULL UNIQUE,
    password    VARCHAR(128) NOT NULL,
    email       VARCHAR(128),
    status      SMALLINT     NOT NULL DEFAULT 1,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_sys_user_status ON sys_user(status);

COMMENT ON TABLE  sys_user             IS '用户表';
COMMENT ON COLUMN sys_user.status      IS '1=正常 0=禁用';
```

> PG 推荐：主键用 `BIGSERIAL` 或 `BIGINT GENERATED ALWAYS AS IDENTITY`、时间用 `TIMESTAMPTZ`、布尔用 `BOOLEAN`、金额用 `NUMERIC(18,2)`。

---

## 3. 前端架构（Vue 3 + Vite + TS）

### 3.1 核心依赖

| 类别 | 依赖 | 说明 |
|---|---|---|
| 核心 | `vue` + `typescript` + `vite` | 框架基础 |
| 路由 | `vue-router@4` | 路由 |
| 状态 | `pinia` + `pinia-plugin-persistedstate` | 状态管理 + 持久化 |
| UI | `element-plus` + `@element-plus/icons-vue` | 组件库 |
| HTTP | `axios` | HTTP 客户端 |
| 代码生成 | `orval` | 从 OpenAPI 生成 TS 客户端 + Vue Query hooks |
| 服务器状态 | `@tanstack/vue-query` | 接口缓存/loading/error 状态 |
| 工具 | `unplugin-vue-components` + `unplugin-auto-import` | Element Plus 按需自动导入 |
| 工具 | `dayjs` / `@vueuse/core` | 时间 / 组合式工具 |
| 规范 | `eslint` + `prettier` + `husky` + `lint-staged` | 代码规范 |

### 3.2 目录结构

```
frontend/
├── src/
│   ├── api/               # Orval 生成的客户端（git 提交）
│   │   ├── generated/     # 自动生成，不要手改
│   │   └── custom/        # 自定义封装
│   ├── assets/
│   ├── components/        # 通用业务组件
│   │   ├── common/        # 基础组件
│   │   └── business/      # 业务组件
│   ├── composables/       # 组合式函数
│   ├── layouts/           # 布局
│   ├── pages/             # 页面（views）
│   │   ├── login/
│   │   ├── dashboard/
│   │   └── system/
│   ├── router/            # 路由 + 守卫
│   ├── stores/            # Pinia store
│   │   ├── user.ts        # 当前用户/token
│   │   └── app.ts         # 全局 app 状态
│   ├── utils/             # 工具函数
│   │   ├── request.ts     # Axios 封装
│   │   └── auth.ts        # token 工具
│   ├── types/             # 全局类型
│   ├── App.vue
│   └── main.ts
├── orval.config.ts        # Orval 配置
├── vite.config.ts
├── tsconfig.json
└── package.json
```

### 3.3 关键约定

- **API 调用**：**禁用**手写 `axios.get`，统一通过 Orval 生成的 hooks（`useGetUser` 等）
- **请求封装**：`utils/request.ts` 统一处理 baseURL、token、错误码、401 跳登录
- **权限指令**：`v-permission="['user:create']"`，配合后端 `@RolesAllowed`
- **代码生成**：
  ```bash
  # 后端启动后，前端执行
  npm run gen:api    # orval 自动从 http://localhost:8080/q/openapi 拉取
  ```

---

## 4. 联调关键点

### 4.1 OpenAPI 自动同步

```
Quarkus 启动 → /q/openapi 暴露规范
    ↓
前端 orval.config.ts 指向该地址
    ↓
npm run gen:api 生成 TS 类型 + axios 客户端 + vue-query hooks
```

### 4.2 JWT 鉴权流

```
用户登录
  → 后端验证账号密码 → 签发 JWT（access + refresh）
  → 前端存 Pinia + localStorage
  → Axios 拦截器统一加 Authorization 头
  → 后端 quarkus-smallrye-jwt 验签 → @RolesAllowed 鉴权
  → 401 统一跳登录页
```

### 4.3 CORS

后端 `application.yml`：
```yaml
quarkus:
  http:
    cors:
      origins: http://localhost:5173
      methods: GET,POST,PUT,DELETE,OPTIONS
      headers: accept,authorization,content-type,x-requested-with
```

### 4.4 错误码约定

| code | 含义 |
|---|---|
| 0 | 成功 |
| 1001 | 未登录 / token 失效 |
| 1003 | 无权限 |
| 4xxx | 业务错误 |
| 5xxx | 服务器错误 |

---

## 5. 本地开发

### 5.1 后端

```bash
cd backend
./mvnw quarkus:dev
# 访问 http://localhost:8080
# OpenAPI  http://localhost:8080/q/openapi
# Swagger UI  http://localhost:8080/q/swagger-ui
```

### 5.2 前端

```bash
cd frontend
npm install
npm run dev
# 访问 http://localhost:5173
```

### 5.3 中间件（docker-compose）

```yaml
services:
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: quarkus
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres"]
      interval: 5s
      timeout: 5s
      retries: 5

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    volumes:
      - redisdata:/data

volumes:
  pgdata:
  redisdata:
```

---

## 6. 构建 & 部署

### 6.1 后端 Docker 化（推荐 native）

```bash
./mvnw package -Dnative -Dquarkus.native.container-build=true
# 产物：target/*-runner（几十 MB 镜像）
```

### 6.2 前端构建

```bash
npm run build
# 产物：dist/，可挂 Nginx 或 CDN
```

### 6.3 部署形态

| 后端 | 前端 | 部署方式 |
|---|---|---|
| GraalVM native 镜像 | 静态文件 | k8s / 容器平台 |
| JVM jar | Nginx 托管 | 中小项目 |

---

## 7. 不引入的东西（避免诱惑）

| 不用 | 用啥替代 | 原因 |
|---|---|---|
| MyBatis-Flex | Hibernate Panache | 官方原生，build-time 优化 |
| sa-token | quarkus-smallrye-jwt | 原生安全栈 |
| Knife4j | quarkus-smallrye-openapi | 原生支持 |
| Hutool 全包 | 按需引入 + JDK 替代 | 体积、原生编译友好 |
| Webpack | Vite | 体验、速度差一个时代 |
| MySQL | **PostgreSQL** | 复杂查询、JSON、扩展生态全面占优 |

---

## 8. 后续可加（按需）

- `quarkus-messaging-kafka`（事件总线）
- `quarkus-cache`（方法级缓存）
- `quarkus-scheduler`（定时任务）
- `quarkus-elasticsearch-rest-client`（搜索）
- `quarkus-mailer`（邮件）
- `quarkus-observability` + OpenTelemetry（链路追踪）
- `postgis`（PG 地理信息扩展，地图场景）

---

> 看完后告诉我：
> 1. 哪些模块要 / 不要
> 2. 还有要调整的吗
> 3. 确认后我直接给你**一键搭出可跑的工程骨架**（PG + Redis + Quarkus + Vue3，含 Flyway 初始化脚本、登录页 Demo、Orval 配置）
