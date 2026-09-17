-- =====================================================
-- V7: 测试用户 + 业务数据
-- =====================================================
-- 密码统一: admin123 / user123 / tenant123
-- 都用 BCrypt 加密 (10 rounds)
-- =====================================================

-- 1. 业务演示租户 (供 tenant 角色用)
INSERT INTO sys_tenant (id, code, name, status) VALUES (3, 'demo', '演示租户', 1)
ON CONFLICT (code) DO NOTHING;
SELECT setval('sys_tenant_id_seq', GREATEST(3, (SELECT MAX(id) FROM sys_tenant)));

-- 2. 测试用户
-- 统一密码 admin123 (BCrypt 10 rounds, $2a$ prefix 兼容 Java at.favre.lib.bcrypt)
-- 哈希由 Python bcrypt.hashpw('admin123', gensalt(10, prefix=b'2a')) 生成
--   三个测试用户都用这个 hash, 密码都是 admin123 (简化测试)
-- 实际生成: $2a$10$Z2XvRhbC0LdTpvdA5M3bVOzIy33PCL6ZdxqyKfGyJO2GMN8j7jqSe

-- 普通用户 user (tenantId=3)
INSERT INTO sys_user (username, password, nickname, email, status, tenant_id, deleted)
VALUES ('user', '$2a$10$Z2XvRhbC0LdTpvdA5M3bVOzIy33PCL6ZdxqyKfGyJO2GMN8j7jqSe', '测试用户', 'user@jade.local', 1, 3, false)
ON CONFLICT (username) DO NOTHING;

-- 租户管理员 tenant (tenantId=3)
INSERT INTO sys_user (username, password, nickname, email, status, tenant_id, deleted)
VALUES ('tenant', '$2a$10$Z2XvRhbC0LdTpvdA5M3bVOzIy33PCL6ZdxqyKfGyJO2GMN8j7jqSe', '租户管理员', 'tenant@jade.local', 1, 3, false)
ON CONFLICT (username) DO NOTHING;

-- 3. 用户-角色关联
-- user → user 角色 (id=2)
INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, 2 FROM sys_user u WHERE u.username = 'user'
ON CONFLICT DO NOTHING;

-- tenant → tenant 角色 (id=3)
INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, 3 FROM sys_user u WHERE u.username = 'tenant'
ON CONFLICT DO NOTHING;

-- 4. 用户-部门关联 (user 在 运营部, tenant 在 运营部)
INSERT INTO sys_user_dept (user_id, dept_id, is_primary)
SELECT u.id, 4, true FROM sys_user u WHERE u.username = 'user'
ON CONFLICT DO NOTHING;

INSERT INTO sys_user_dept (user_id, dept_id, is_primary)
SELECT u.id, 4, true FROM sys_user u WHERE u.username = 'tenant'
ON CONFLICT DO NOTHING;

-- 5. 演示项目
INSERT INTO sys_project (name, description, tenant_id, deleted)
SELECT '演示项目 A', '这是一个测试项目, 演示多租户隔离', 3, false
WHERE NOT EXISTS (SELECT 1 FROM sys_project WHERE name = '演示项目 A' AND tenant_id = 3);

INSERT INTO sys_project (name, description, tenant_id, deleted)
SELECT '演示项目 B', '第二个测试项目, 展示 admin 看到的跨租户视图', 3, false
WHERE NOT EXISTS (SELECT 1 FROM sys_project WHERE name = '演示项目 B' AND tenant_id = 3);

-- 6. 演示患者
-- patient.idCard / phone 在 DB 里是 AES-256-GCM 密文（@Encrypted 触发）
-- 因为直接 SQL INSERT 不走 JPA 转换器, 不会自动加密
-- 所以这里不直接 INSERT, 用户通过 API 创建 (POST /api/v1/patients) 会自动加密
