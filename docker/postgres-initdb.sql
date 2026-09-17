-- =====================================================
-- PostgreSQL 初始化脚本 (CI 启动时自动跑)
-- =====================================================
-- 创建 jade-admin 跟 jade-demo 各自独立的 DB
-- 跟本地 docker-compose.yml 起的 jade-postgres 容器 init 一致
-- =====================================================

-- 业务库 (demo + admin 各自独立)
CREATE DATABASE jade_demo;
CREATE DATABASE jade_admin;

-- 兼容库 (老 healthcheck / 老 config 用的 'jade' 库, 防止 healthcheck 失败)
-- CI services.postgres 的 healthcheck 还在用 -d jade
CREATE DATABASE jade;
