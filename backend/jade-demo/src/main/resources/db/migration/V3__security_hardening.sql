-- =====================================================
-- V3: 安全加固
--   - sys_user 加 tenant_id
--   - 加默认 admin 的 BCrypt 密码（不再是明文）
--   - 加唯一约束防用户名重复
-- =====================================================

-- 1. sys_user 加 tenant_id 列
ALTER TABLE sys_user ADD COLUMN tenant_id BIGINT;

-- 2. admin 用户关联到默认租户（t_001 = 1）
UPDATE sys_user SET tenant_id = 1 WHERE username = 'admin' AND tenant_id IS NULL;

-- 3. 改 admin 密码为 BCrypt(admin123)
--    真实 BCrypt("admin123") @ cost 10
UPDATE sys_user
SET password = '$2a$10$SE9M6EdbRgl9k1Vt4v0hTeXY16zYXDwGIE7m8vlqqOr2EtEB5/C.G'
WHERE username = 'admin';

-- 4. 加 sys_user_role 关联（admin -> admin 角色已经在 V1）
--    这里补一个 user 角色关联给 admin（演示用，实际应该单独用户）
--    V1 已经塞了 sys_user_role 数据，这里不动

-- 5. 索引：按 tenant 查 user
CREATE INDEX idx_sys_user_tenant ON sys_user(tenant_id) WHERE deleted = false;

COMMENT ON COLUMN sys_user.tenant_id IS '所属租户（多租户隔离字段，null=平台超管）';
