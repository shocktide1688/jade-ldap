-- =====================================================
-- Jade Admin · 种子数据 (V5)
-- =====================================================
-- 超级管理员: admin / admin123
-- 测试用户:   user  / user123
-- =====================================================

-- 0. 默认租户 + 默认 admin 用户 (跟 V7 的 hash 一致, 保证 admin123 密码)
--    V1~V3 在 jade-demo 模块也 seed, 这里 IF NOT EXISTS 兼容已存在的 (用 V7 的统一 hash)
INSERT INTO sys_tenant (id, code, name, status) VALUES (1, 't_001', '租户 A', 1)
ON CONFLICT (code) DO NOTHING;
INSERT INTO sys_tenant (id, code, name, status) VALUES (2, 't_002', '租户 B', 1)
ON CONFLICT (code) DO NOTHING;
SELECT setval('sys_tenant_id_seq', GREATEST(2, (SELECT COALESCE(MAX(id), 0) FROM sys_tenant)));

INSERT INTO sys_user (id, username, password, nickname, email, status, tenant_id, deleted)
VALUES (1, 'admin', '$2a$10$Z2XvRhbC0LdTpvdA5M3bVOzIy33PCL6ZdxqyKfGyJO2GMN8j7jqSe', '超级管理员', 'admin@jade.local', 1, 1, false)
ON CONFLICT (username) DO NOTHING;
SELECT setval('sys_user_id_seq', GREATEST(1, (SELECT COALESCE(MAX(id), 0) FROM sys_user)));

-- 1. 角色
INSERT INTO sys_role (id, tenant_id, role_name, role_code, role_sort, data_scope, status, remark, created_by)
VALUES
  (1, NULL, '超级管理员', 'admin',  1, 'ALL', 1, '系统内置, 拥有所有权限',       'system'),
  (2, NULL, '普通用户',   'user',   2, 'SELF', 1, '默认角色, 只能看自己数据',   'system'),
  (3, NULL, '租户管理员', 'tenant', 3, 'DEPT', 1, '租户级管理员, 管自己租户',   'system');

SELECT setval('sys_role_id_seq', 3);

-- 2. 菜单（用经典的 system:module:action 权限标识）
-- 顶级目录
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, perms, sort_order, visible, status, is_cache)
VALUES
  -- 顶级目录
  (1,   0, '系统管理',  'M', 'system',    NULL,          'system',    NULL,                          1, 1, 1, 0),
  (2,   0, '系统监控',  'M', 'monitor',   NULL,          'monitor',   NULL,                          2, 1, 1, 0),
  (3,   0, '系统工具',  'M', 'tool',      NULL,          'tool',      NULL,                          3, 1, 1, 0),
  (4,   0, '平台演示',  'M', 'demo',      NULL,          'demo',      NULL,                          4, 1, 1, 0),
  (5,   0, '我的',      'M', 'profile',   NULL,          'profile',   NULL,                          9, 0, 1, 0),
  -- 系统管理 -> 用户管理
  (10,  1, '用户管理',  'C', 'user',      'system/user/index',    NULL, 'system:user:list',  1, 1, 1, 1),
  (11, 10, '用户查询',  'F', NULL,        NULL,          NULL, 'system:user:query', 1, 1, 1, 0),
  (12, 10, '用户新增',  'F', NULL,        NULL,          NULL, 'system:user:add',   1, 1, 1, 0),
  (13, 10, '用户修改',  'F', NULL,        NULL,          NULL, 'system:user:edit',  1, 1, 1, 0),
  (14, 10, '用户删除',  'F', NULL,        NULL,          NULL, 'system:user:delete',1, 1, 1, 0),
  (15, 10, '重置密码',  'F', NULL,        NULL,          NULL, 'system:user:reset', 1, 1, 1, 0),
  -- 系统管理 -> 角色管理
  (20,  1, '角色管理',  'C', 'role',      'system/role/index',    NULL, 'system:role:list',  2, 1, 1, 1),
  (21, 20, '角色查询',  'F', NULL,        NULL,          NULL, 'system:role:query', 1, 1, 1, 0),
  (22, 20, '角色新增',  'F', NULL,        NULL,          NULL, 'system:role:add',   1, 1, 1, 0),
  (23, 20, '角色修改',  'F', NULL,        NULL,          NULL, 'system:role:edit',  1, 1, 1, 0),
  (24, 20, '角色删除',  'F', NULL,        NULL,          NULL, 'system:role:delete',1, 1, 1, 0),
  (25, 20, '分配权限',  'F', NULL,        NULL,          NULL, 'system:role:assign',1, 1, 1, 0),
  -- 系统管理 -> 菜单管理
  (30,  1, '菜单管理',  'C', 'menu',      'system/menu/index',    NULL, 'system:menu:list',  3, 1, 1, 1),
  (31, 30, '菜单新增',  'F', NULL,        NULL,          NULL, 'system:menu:add',   1, 1, 1, 0),
  (32, 30, '菜单修改',  'F', NULL,        NULL,          NULL, 'system:menu:edit',  1, 1, 1, 0),
  (33, 30, '菜单删除',  'F', NULL,        NULL,          NULL, 'system:menu:delete',1, 1, 1, 0),
  -- 系统管理 -> 部门管理
  (40,  1, '部门管理',  'C', 'dept',      'system/dept/index',    NULL, 'system:dept:list',  4, 1, 1, 1),
  (41, 40, '部门新增',  'F', NULL,        NULL,          NULL, 'system:dept:add',   1, 1, 1, 0),
  (42, 40, '部门修改',  'F', NULL,        NULL,          NULL, 'system:dept:edit',  1, 1, 1, 0),
  (43, 40, '部门删除',  'F', NULL,        NULL,          NULL, 'system:dept:delete',1, 1, 1, 0),
  -- 系统管理 -> 字典管理
  (50,  1, '字典管理',  'C', 'dict',      'system/dict/index',    NULL, 'system:dict:list',  5, 1, 1, 1),
  (51, 50, '字典新增',  'F', NULL,        NULL,          NULL, 'system:dict:add',   1, 1, 1, 0),
  (52, 50, '字典修改',  'F', NULL,        NULL,          NULL, 'system:dict:edit',  1, 1, 1, 0),
  (53, 50, '字典删除',  'F', NULL,        NULL,          NULL, 'system:dict:delete',1, 1, 1, 0),
  -- 系统管理 -> 参数设置
  (60,  1, '参数设置',  'C', 'config',    'system/config/index',  NULL, 'system:config:list', 6, 1, 1, 1),
  -- 系统监控 -> 操作日志
  (100, 2, '操作日志',  'C', 'operlog',   'monitor/operlog/index',NULL, 'monitor:operlog:list', 1, 1, 1, 1),
  -- 系统监控 -> 登录日志
  (101, 2, '登录日志',  'C', 'loginlog',  'monitor/loginlog/index',NULL,'monitor:loginlog:list',2, 1, 1, 1),
  -- 系统工具 -> 代码生成
  (110, 3, '代码生成',  'C', 'codegen',   'tool/codegen/index',   NULL, 'tool:codegen:list', 1, 1, 1, 1),
  -- 系统工具 -> 文件存储
  (111, 3, '文件存储',  'C', 'oss',       'tool/oss/index',       NULL, 'tool:oss:list',     2, 1, 1, 1),
  -- 系统工具 -> 定时任务
  (112, 3, '定时任务',  'C', 'task',      'tool/task/index',      NULL, 'tool:task:list',    3, 1, 1, 1),
  -- 平台演示 -> 分布式锁
  (120, 4, '分布式锁',  'C', 'lock',      'demo/lock/index',      NULL, 'demo:lock:list',    1, 1, 1, 1),
  -- 平台演示 -> 业务指标
  (121, 4, '业务指标',  'C', 'metrics',   'demo/metrics/index',   NULL, 'demo:metrics:list', 2, 1, 1, 1),
  -- 我的
  (130, 5, '个人中心',  'C', 'index',     'profile/index',        NULL, NULL,                 1, 1, 1, 1);

SELECT setval('sys_menu_id_seq', 130);

-- 3. 顶级部门
INSERT INTO sys_dept (id, tenant_id, parent_id, dept_name, dept_code, sort_order, status, remark)
VALUES
  (1, NULL, 0, 'Jade 总部',  'JADE',   1, 1, '根部门'),
  (2, 1,    1, '研发部',     'RD',     1, 1, NULL),
  (3, 1,    1, '产品部',     'PD',     2, 1, NULL),
  (4, 1,    1, '运营部',     'OPS',    3, 1, NULL);

SELECT setval('sys_dept_id_seq', 4);

-- 4. 字典类型 + 数据
INSERT INTO sys_dict_type (id, dict_name, dict_type, status, remark)
VALUES
  (1, '用户性别', 'sys_user_sex', 1, '用户性别列表'),
  (2, '系统状态', 'sys_normal_status', 1, '正常/停用'),
  (3, '系统是否', 'sys_yes_no', 1, '是/否'),
  (4, '操作类型', 'sys_oper_type', 1, '操作日志业务类型'),
  (5, '通知类型', 'sys_notice_type', 1, '通知/公告');

SELECT setval('sys_dict_type_id_seq', 5);

INSERT INTO sys_dict_data (dict_type, dict_label, dict_value, css_class, sort_order, status, is_default)
VALUES
  -- 用户性别
  ('sys_user_sex', '男', '0', 'primary', 1, 1, 1),
  ('sys_user_sex', '女', '1', 'danger',  2, 1, 0),
  ('sys_user_sex', '未知', '2', 'info', 3, 1, 0),
  -- 系统状态
  ('sys_normal_status', '正常', '1', 'primary', 1, 1, 1),
  ('sys_normal_status', '停用', '0', 'danger',  2, 1, 0),
  -- 系统是否
  ('sys_yes_no', '是', 'Y', 'primary', 1, 1, 1),
  ('sys_yes_no', '否', 'N', 'danger',  2, 1, 0),
  -- 操作类型
  ('sys_oper_type', '新增', '1', 'info',    1, 1, 0),
  ('sys_oper_type', '修改', '2', 'info',    2, 1, 0),
  ('sys_oper_type', '删除', '3', 'danger',  3, 1, 0),
  ('sys_oper_type', '查询', '4', 'primary', 4, 1, 0),
  ('sys_oper_type', '导出', '5', 'warning', 5, 1, 0),
  -- 通知类型
  ('sys_notice_type', '通知', '1', 'primary', 1, 1, 1),
  ('sys_notice_type', '公告', '2', 'warning', 2, 1, 0);

-- 5. 角色-菜单关联（admin 拥有所有菜单，user 只有 demo 部分）
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu;  -- 超级管理员 = 全部

-- 普通用户: 只能看演示和个人中心
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
  (2, 4), (2, 5), (2, 120), (2, 121), (2, 130);

-- 租户管理员: 系统管理（部分）+ 监控 + 工具 + 我的
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
  (3, 1), (3, 10), (3, 20), (3, 50), (3, 60),
  (3, 2), (3, 100), (3, 101),
  (3, 3), (3, 110), (3, 111), (3, 112),
  (3, 4), (3, 120), (3, 121),
  (3, 5), (3, 130);

-- 6. 用户-角色关联
-- 现有 admin (id=1) 拥有超级管理员
INSERT INTO sys_user_role (user_id, role_id) VALUES (1, 1);

-- 7. 用户-部门关联
INSERT INTO sys_user_dept (user_id, dept_id, is_primary) VALUES (1, 2, TRUE);

-- 8. 参数配置
INSERT INTO sys_config (config_name, config_key, config_value, config_type, is_builtin, remark)
VALUES
  ('用户管理-账号初始密码', 'sys.user.initPassword', '123456', 'business', 0, '账号初始密码'),
  ('登录-是否开启验证码',  'sys.account.captchaEnabled', 'false', 'system', 1, '是否开启登录验证码'),
  ('登录-失败锁定次数',    'sys.account.passwordRetryCount', '5', 'system', 1, '登录失败锁定阈值'),
  ('登录-失败锁定时长(分钟)', 'sys.account.lockTime', '10', 'system', 1, '账号锁定时长');

-- 9. 通知公告 (示例)
INSERT INTO sys_notice (tenant_id, notice_title, notice_type, notice_content, status, created_by)
VALUES
  (NULL, 'Jade Admin 上线公告', 2, '<p>欢迎使用 Jade 管理后台。本系统基于 Quarkus 3.33 LTS + Vue 3 构建。</p>', 1, 'system'),
  (NULL, '关于多租户的说明', 1, '<p>系统管理员 tenant_id 为空, 可管理所有租户。租户管理员只能管自己租户。</p>', 1, 'system');
