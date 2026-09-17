-- =====================================================
-- V14 · 邮箱服务配置（忘记密码 · 邮箱验证码重置）
-- =====================================================

-- 邮箱服务参数（后台「参数配置」页可改，实时生效）
INSERT INTO sys_config (config_name, config_key, config_value, config_type, is_builtin, remark)
VALUES
  ('邮箱服务开关',   'sys.mail.enabled',  'false',            'system', 0, '开启后登录页支持邮箱验证码找回密码'),
  ('SMTP 服务器',    'sys.mail.host',     'smtp.example.com', 'system', 0, 'SMTP 服务器地址'),
  ('SMTP 端口',      'sys.mail.port',     '465',              'system', 0, 'SSL 常用 465，STARTTLS 常用 587'),
  ('SMTP 账号',      'sys.mail.username', '',                 'system', 0, '发件邮箱账号'),
  ('SMTP 密码/授权码','sys.mail.password', '',                'system', 0, '发件邮箱密码或授权码'),
  ('发件人地址',     'sys.mail.from',     '',                 'system', 0, '通常与 SMTP 账号一致'),
  ('SMTP SSL',       'sys.mail.ssl',      'true',             'system', 0, '465 端口为 true，587 端口为 false')
ON CONFLICT (config_key, deleted) DO NOTHING;
