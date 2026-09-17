-- =====================================================
-- V9 · 登录验证码配置 + 参数配置菜单
-- =====================================================

-- 0. 清理脚手架遗留的死配置（无任何代码引用，且 builtin 不可编辑，避免与下方新开关混淆）
DELETE FROM sys_config WHERE config_key = 'sys.account.captchaEnabled';

-- 1. 验证码参数（后台「参数配置」页可改，即时生效）
INSERT INTO sys_config (config_name, config_key, config_value, config_type, is_builtin, remark)
VALUES
  ('验证码开关', 'sys.captcha.enabled', 'false', 'system', 0,
   '登录验证码总开关（true/false），改动即时生效'),
  ('验证码触发阈值', 'sys.captcha.threshold', '3', 'system', 0,
   '同一用户名连续登录失败达到该次数后，登录必须输入验证码（最小 1）')
ON CONFLICT (config_key, deleted) DO NOTHING;

-- 2. 系统管理 -> 参数配置 菜单
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, perms, sort_order, visible, status, is_cache)
VALUES (140, 1, '参数配置', 'C', 'config', 'system/config/index', NULL, 'system:config:list', 7, 1, 1, 1)
ON CONFLICT (id) DO NOTHING;

SELECT setval('sys_menu_id_seq', GREATEST(140, (SELECT COALESCE(MAX(id), 0) FROM sys_menu)));
