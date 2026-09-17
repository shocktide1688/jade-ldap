# Changelog

所有 Jade Platform 的变更都记录在这里。

格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，
版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

## [Unreleased]

### Added
- Quarkus 3.15.7 兼容
- jade-common：统一响应 R<T>、PageResult<T>、BizException、ResultCode
- jade-web：TraceIdFilter、JadeOpenApiConfig
- jade-security：JwtUtil、UserContext、@RequiresRoles
- jade-redis：RedisLock（基于 SET NX EX + Lua 释放）
- jade-oss：OssTemplate 接口
- jade-codegen：模块占位
- jade-log：@OperateLog 注解 + Aspect
- jade-demo：演示项目（Auth + User 两个 Controller）
- 前端：Vue 3 + Vite + TS + Element Plus + Pinia + Orval 集成
- docker-compose：PG 16 + Redis 7
- CI：GitHub Actions matrix（paths-filter 智能触发）
- CODEOWNERS
- Maven 多模块 + mvnw wrapper
- Makefile（一键命令）
- Dockerfile（JVM + Native 两版）
- 集成测试示例

### Fixed
- BOM 循环依赖
- Quarkus 3.15 artifact 重命名（quarkus-arc-processor → quarkus-arc-deployment）
- CORS 配置结构（去掉 enabled，新结构用 origins 隐式启用）
- Hibernate time-zone key 重命名（quarkus.hibernate-orm.jdbc.timezone）
- OpenAPI info 走 mp.openapi 标准
- Log category 配置 key 加引号避免点号解析问题
- 404 异常走专门的 NotFoundException 处理
- Panache 动态条件查询参数化

## [1.0.0-SNAPSHOT] - 2026-08-26

### Added
- 项目初始化
