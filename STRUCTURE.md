# Jade 平台 · 完整目录结构

> 目标：Jade 是一个**底座**，本身不部署。业务项目从它派生。
> 单仓库承载：核心库 + 脚手架 + 前端模板 + 示例 + 文档。

---

## 1. 顶层总览

```
Jade/                                 ← 仓库根（Jade Platform）
├── platform/                         # 核心库：可被业务项目依赖的 starter
├── frontend/                         # 前端模板：Vue 3 + Vite 脚手架
├── scaffold/                         # 脚手架：一键生成新项目的工具
├── examples/                         # 示例项目：演示各种用法
├── docs/                             # 文档站点（VitePress / Docusaurus）
├── scripts/                          # 仓库级脚本（发布、版本同步等）
├── docker-compose.yml                # 本地依赖：PG + Redis
├── .github/                          # CI：构建、测试、发布
├── .editorconfig
├── .gitignore
├── LICENSE
├── README.md
└── CHANGELOG.md
```

---

## 2. platform/ —— 核心底座（Maven 多模块）

```
platform/
├── pom.xml                           # 父 POM：统一版本、依赖、插件管理
│
├── jade-dependencies/                # BOM（依赖清单，统一版本号）
│   └── pom.xml                       # 业务项目 import 这个就行
│
├── jade-common/                      # 通用工具
│   ├── src/main/java/com/jade/common/
│   │   ├── api/                      # R<T> 统一响应、PageResult<T> 分页
│   │   ├── exception/                # BizException、GlobalExceptionMapper
│   │   ├── constant/                 # 全局常量、错误码枚举
│   │   └── util/                     # 工具类
│   └── pom.xml
│
├── jade-web/                         # Web 通用能力
│   ├── src/main/java/com/jade/web/
│   │   ├── filter/                   # TraceId 过滤器、CORS
│   │   ├── config/                   # OpenAPI、Jackson 配置
│   │   └── advice/                   # ControllerAdvice
│   └── pom.xml
│
├── jade-security/                    # 安全 / 鉴权
│   ├── src/main/java/com/jade/security/
│   │   ├── jwt/                      # JWT 签发 / 解析
│   │   ├── context/                  # UserContext（当前登录用户）
│   │   ├── annotation/               # @RequiresRoles、@RequiresPermissions
│   │   └── interceptor/              # 鉴权拦截器
│   └── pom.xml
│
├── jade-log/                         # 日志 / 审计
│   ├── src/main/java/com/jade/log/
│   │   ├── annotation/               # @OperateLog
│   │   ├── aspect/                   # AOP 切面
│   │   ├── mdc/                      # TraceId / UserId 透传
│   │   └── store/                    # 审计日志入库
│   └── pom.xml
│
├── jade-redis/                       # Redis 封装
│   ├── src/main/java/com/jade/redis/
│   │   ├── lock/                     # 分布式锁
│   │   ├── limiter/                  # 限流（滑动窗口 / 令牌桶）
│   │   └── cache/                    # 二级缓存抽象
│   └── pom.xml
│
├── jade-oss/                         # 对象存储抽象
│   ├── src/main/java/com/jade/oss/
│   │   ├── core/                     # OssTemplate 接口
│   │   ├── local/                    # 本地实现
│   │   ├── s3/                       # 兼容 S3（MinIO / 阿里 OSS）
│   │   └── config/
│   └── pom.xml
│
├── jade-codegen/                     # 代码生成器（生成 Entity / DTO / Controller）
│   ├── src/main/java/com/jade/codegen/
│   │   ├── engine/                   # Freemarker / Velocity 模板引擎
│   │   ├── generator/                # 各层代码生成器
│   │   ├── template/                 # 模板文件
│   │   └── ui/                       # （可选）Web 配置界面
│   └── pom.xml
│
├── jade-idempotent/                  # 接口幂等性
├── jade-sensitive/                   # 字段加密（手机号 / 身份证）
├── jade-tenant/                      # 多租户
│
└── jade-demo/                        # 演示项目（活文档）
    ├── src/main/java/com/jade/demo/
    │   ├── Application.java
    │   ├── controller/               # DemoController
    │   ├── service/
    │   ├── repository/
    │   └── entity/
    ├── src/main/resources/
    │   ├── application.yml
    │   ├── application-dev.yml
    │   ├── application-prod.yml
    │   └── db/migration/             # Flyway 迁移
    │       ├── V1__init.sql
    │       └── V2__seed.sql
    └── pom.xml
```

> 业务项目用 Jade 时的 `pom.xml`：
> ```xml
> <parent>
>     <groupId>com.jade</groupId>
>     <artifactId>jade-dependencies</artifactId>
>     <version>1.0.0</version>
> </parent>
> ```

---

## 3. frontend/ —— 前端模板（Vue 3）

```
frontend/
├── jade-template/                    # 模板工程（Archetype 用）
│   ├── package.json
│   ├── vite.config.ts
│   ├── tsconfig.json
│   ├── index.html
│   ├── orval.config.ts               # 从后端 OpenAPI 生成
│   ├── .env.development
│   ├── .env.production
│   └── src/
│       ├── api/
│       │   ├── generated/            # Orval 生成（git 提交）
│       │   └── index.ts
│       ├── assets/
│       ├── components/
│       │   ├── common/               # 通用基础组件
│       │   └── business/             # 业务组件
│       ├── composables/              # useXxx 组合式函数
│       ├── layouts/
│       │   ├── DefaultLayout.vue
│       │   └── BlankLayout.vue
│       ├── pages/
│       │   ├── login/
│       │   │   └── index.vue
│       │   ├── dashboard/
│       │   │   └── index.vue
│       │   ├── system/
│       │   │   ├── user/
│       │   │   └── role/
│       │   └── error/
│       │       ├── 404.vue
│       │       └── 403.vue
│       ├── router/
│       │   ├── index.ts
│       │   └── guards.ts             # 路由守卫
│       ├── stores/
│       │   ├── user.ts               # 当前用户 / token
│       │   ├── app.ts                # 全局 UI 状态
│       │   └── permission.ts         # 动态路由 / 按钮权限
│       ├── utils/
│       │   ├── request.ts            # Axios 封装
│       │   ├── auth.ts               # token 工具
│       │   └── permission.ts         # v-permission 指令
│       ├── directives/               # 自定义指令
│       │   └── permission.ts
│       ├── types/                    # 全局类型
│       ├── styles/                   # 全局样式、变量
│       ├── App.vue
│       └── main.ts
│   └── README.md
│
└── jade-admin/                       # 默认中后台实现（基于 template 扩展）
    ├── （结构同 template，叠加 jade 业务模块）
    └── ...
```

---

## 4. scaffold/ —— 脚手架工具

```
scaffold/
├── archetype-jade/                   # Maven Archetype
│   ├── src/main/resources/
│   │   ├── archetype-resources/      # 模板文件
│   │   └── META-INF/maven/
│   │       └── archetype-metadata.xml
│   └── pom.xml
│
├── cli/                              # 可选：Jade CLI 工具
│   ├── package.json                  # 或 Java 实现
│   ├── src/
│   │   ├── commands/
│   │   │   ├── new.ts                # jade new my-app
│   │   │   ├── add.ts                # jade add module-security
│   │   │   └── gen.ts                # jade gen api / entity
│   │   └── index.ts
│   └── README.md
│
└── templates/                        # 其他模板
    ├── springboot-archetype/         # 备选（如有 Spring 项目需求）
    └── docker-template/              # Dockerfile 模板
```

**业务项目一行命令创建**：
```bash
mvn archetype:generate \
    -DarchetypeGroupId=com.jade \
    -DarchetypeArtifactId=archetype-jade \
    -DarchetypeVersion=1.0.0 \
    -DgroupId=com.example \
    -DartifactId=my-app
```

---

## 5. examples/ —— 示例项目

```
examples/
├── example-basic/                    # 基础 CRUD 示例
├── example-rbac/                     # 完整 RBAC 示例
├── example-multi-tenant/             # 多租户示例
├── example-microservice/             # 微服务调用示例
└── example-graphql/                  # （可选）GraphQL 端点
```

---

## 6. docs/ —— 文档站点

```
docs/
├── .vitepress/                       # VitePress 配置
│   └── config.ts
├── index.md                          # 首页
├── guide/
│   ├── getting-started.md            # 5 分钟上手
│   ├── architecture.md
│   └── upgrade.md                    # 版本升级
├── modules/
│   ├── common.md
│   ├── web.md
│   ├── security.md
│   ├── log.md
│   ├── redis.md
│   └── oss.md
├── best-practices/
│   ├── error-handling.md
│   ├── api-design.md
│   └── testing.md
└── changelog.md
```

> 部署：`docs/` 跑 VitePress 静态站点，挂 GitHub Pages / Vercel / 内网 Nginx。

---

## 7. scripts/ & .github/

```
scripts/
├── release.sh                        # 版本发布脚本
├── sync-version.sh                   # 多模块版本号同步
└── gen-frontend.sh                   # 触发前端 Orval 生成

.github/
├── workflows/
│   ├── ci.yml                        # PR / push 触发：构建、测试
│   ├── release.yml                   # 打 tag 触发：发布到 Maven 仓库
│   └── docs.yml                      # 自动部署文档站点
└── ISSUE_TEMPLATE/
```

---

## 8. 关键约定（补充）

### 8.1 业务项目标准目录（生成后）

```
my-app/                              ← 新建项目
├── pom.xml                           # parent = jade-dependencies
├── src/main/java/com/example/myapp/
│   ├── controller/
│   ├── service/
│   ├── repository/
│   ├── entity/
│   ├── dto/
│   └── config/
├── src/main/resources/
│   ├── application.yml
│   └── db/migration/
└── frontend/                         # 前端目录（从 jade-template 拉）
```

### 8.2 命名规范

| 类别 | 规范 |
|---|---|
| Maven groupId | `com.jade`（平台） / `com.example`（业务） |
| artifactId | `jade-*`（平台） / 业务名（业务） |
| 包名 | `com.jade.<模块>` / `com.example.<业务>.<层>` |
| 数据库表 | `t_<业务>_<表>`（如 `t_sys_user`） |
| 接口路径 | `/api/v1/<资源>` |

### 8.3 版本演进

```
v0.x   → 内部试用、单团队
v1.0   → 跨团队稳定、冻结公共 API
v1.x   → 兼容迭代、新增 starter
v2.0   → 允许 break change
```

---

## 9. 部署 vs 开发：哪里是产品

| 目录 | 是不是产品 | 谁消费它 |
|---|---|---|
| `platform/jade-*` | ✅ 库 | 被业务项目依赖 |
| `platform/jade-demo` | ❌ 示例 | 开发者跑起来参考 |
| `frontend/jade-template` | ✅ 模板 | 业务前端基线 |
| `frontend/jade-admin` | ✅ 应用 | 直接部署的中后台 |
| `scaffold/` | ✅ 工具 | 开发者用 |
| `examples/` | ❌ 示例 | 学习用 |
| `docs/` | ✅ 站点 | 所有人查阅 |

---

> 看完告诉我：
> 1. 结构上要删 / 加什么（比如要不要 GraphQL、要不要 CLI）
> 2. 确认后我**按这个结构把骨架一次性搭出来**（30+ 文件，含所有 pom、application.yml、Flyway 脚本、前端 vite 模板、Jade CLI 雏形、README）
