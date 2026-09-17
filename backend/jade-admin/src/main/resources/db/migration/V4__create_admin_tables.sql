-- =====================================================
-- Jade Admin · RBAC + 系统管理表 (V4)
-- =====================================================
-- 设计：经典 RuoYi/JeecgBoot 风格
-- 多租户：所有表加 tenant_id（系统级数据 tenant_id=NULL）
-- 软删：deleted BOOLEAN DEFAULT FALSE
-- =====================================================
-- 自包含设计: jade-admin 跑在独立 DB (jade_admin)
-- V1 (basic) + V2 (tenant/patient/project) + V3 (tenant_id column) 已经把基础表建好
-- V4 只负责: 升级 sys_role / sys_user_role 到 admin 完整版, 加上 admin 独有的表
-- =====================================================

-- 1. 先 drop V1 留下的占位表（sys_role / sys_user_role 是 V1 的简化版, V4 升级为完整版）
DROP TABLE IF EXISTS sys_user_role CASCADE;
DROP TABLE IF EXISTS sys_role CASCADE;

-- 角色
CREATE TABLE sys_role (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT,
    role_name VARCHAR(50) NOT NULL,
    role_code VARCHAR(50) NOT NULL,
    role_sort INT DEFAULT 0,
    data_scope VARCHAR(20) DEFAULT 'ALL',  -- ALL / DEPT_AND_CHILD / DEPT / SELF
    status SMALLINT DEFAULT 1,             -- 0=停用 1=正常
    remark VARCHAR(500),
    created_by VARCHAR(50),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_by VARCHAR(50),
    updated_at TIMESTAMP DEFAULT NOW(),
    deleted BOOLEAN DEFAULT FALSE,
    UNIQUE (tenant_id, role_code, deleted)
);
COMMENT ON TABLE sys_role IS '角色表';

-- 菜单（目录/菜单/按钮 三合一）
CREATE TABLE sys_menu (
    id BIGSERIAL PRIMARY KEY,
    parent_id BIGINT DEFAULT 0,
    menu_name VARCHAR(50) NOT NULL,
    menu_type VARCHAR(1) NOT NULL,           -- M=目录 C=菜单 F=按钮
    path VARCHAR(200),                    -- 前端路由
    component VARCHAR(200),               -- 前端组件路径
    icon VARCHAR(50),
    perms VARCHAR(100),                   -- 权限标识 system:user:list
    sort_order INT DEFAULT 0,
    visible SMALLINT DEFAULT 1,
    status SMALLINT DEFAULT 1,
    is_cache SMALLINT DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    deleted BOOLEAN DEFAULT FALSE
);
COMMENT ON TABLE sys_menu IS '菜单权限表';

-- 部门
CREATE TABLE sys_dept (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT,
    parent_id BIGINT DEFAULT 0,
    dept_name VARCHAR(50) NOT NULL,
    dept_code VARCHAR(50),
    sort_order INT DEFAULT 0,
    leader_user_id BIGINT,
    phone VARCHAR(20),
    email VARCHAR(100),
    status SMALLINT DEFAULT 1,
    remark VARCHAR(500),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    deleted BOOLEAN DEFAULT FALSE,
    UNIQUE (tenant_id, dept_code, deleted)
);
COMMENT ON TABLE sys_dept IS '部门表';

-- 用户-角色关联
CREATE TABLE sys_user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id)
);
COMMENT ON TABLE sys_user_role IS '用户角色关联';

-- 角色-菜单关联
CREATE TABLE sys_role_menu (
    role_id BIGINT NOT NULL,
    menu_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, menu_id)
);
COMMENT ON TABLE sys_role_menu IS '角色菜单关联';

-- 用户-部门关联（一个用户可属于多部门）
CREATE TABLE sys_user_dept (
    user_id BIGINT NOT NULL,
    dept_id BIGINT NOT NULL,
    is_primary BOOLEAN DEFAULT FALSE,
    PRIMARY KEY (user_id, dept_id)
);
COMMENT ON TABLE sys_user_dept IS '用户部门关联';

-- 字典类型
CREATE TABLE sys_dict_type (
    id BIGSERIAL PRIMARY KEY,
    dict_name VARCHAR(100) NOT NULL,
    dict_type VARCHAR(100) NOT NULL,
    status SMALLINT DEFAULT 1,
    remark VARCHAR(500),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    deleted BOOLEAN DEFAULT FALSE,
    UNIQUE (dict_type, deleted)
);
COMMENT ON TABLE sys_dict_type IS '字典类型';

-- 字典数据
CREATE TABLE sys_dict_data (
    id BIGSERIAL PRIMARY KEY,
    dict_type VARCHAR(100) NOT NULL,
    dict_label VARCHAR(100) NOT NULL,
    dict_value VARCHAR(100) NOT NULL,
    css_class VARCHAR(50),
    sort_order INT DEFAULT 0,
    status SMALLINT DEFAULT 1,
    is_default SMALLINT DEFAULT 0,
    remark VARCHAR(500),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    deleted BOOLEAN DEFAULT FALSE
);
CREATE INDEX idx_dict_data_type ON sys_dict_data (dict_type);
COMMENT ON TABLE sys_dict_data IS '字典数据';

-- 通知公告
CREATE TABLE sys_notice (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT,
    notice_title VARCHAR(200) NOT NULL,
    notice_type SMALLINT DEFAULT 1,         -- 1=通知 2=公告
    notice_content TEXT,
    status SMALLINT DEFAULT 1,
    created_by VARCHAR(50),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    deleted BOOLEAN DEFAULT FALSE
);
COMMENT ON TABLE sys_notice IS '通知公告';

-- 操作日志
CREATE TABLE sys_oper_log (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT,
    title VARCHAR(100),
    business_type SMALLINT DEFAULT 0,        -- 0=其它 1=新增 2=修改 3=删除 4=查询 5=导出
    method VARCHAR(200),                     -- 调用方法/类
    request_method VARCHAR(10),              -- HTTP method
    request_url VARCHAR(500),
    request_param TEXT,
    response_result TEXT,
    error_msg TEXT,
    user_id BIGINT,
    username VARCHAR(50),
    ip VARCHAR(50),
    location VARCHAR(200),
    user_agent VARCHAR(500),
    duration_ms BIGINT,
    oper_time TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_oper_log_user ON sys_oper_log (user_id);
CREATE INDEX idx_oper_log_time ON sys_oper_log (oper_time);
COMMENT ON TABLE sys_oper_log IS '操作日志';

-- 登录日志
CREATE TABLE sys_login_log (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT,
    username VARCHAR(50),
    ip VARCHAR(50),
    location VARCHAR(200),
    browser VARCHAR(100),
    os VARCHAR(100),
    status SMALLINT DEFAULT 1,              -- 1=成功 0=失败
    msg VARCHAR(500),
    login_time TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_login_log_user ON sys_login_log (username);
CREATE INDEX idx_login_log_time ON sys_login_log (login_time);
COMMENT ON TABLE sys_login_log IS '登录日志';

-- 文件存储
CREATE TABLE sys_oss (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT,
    file_name VARCHAR(200),
    original_name VARCHAR(200),
    file_suffix VARCHAR(20),
    file_size BIGINT,
    url VARCHAR(500),
    storage_type VARCHAR(20) DEFAULT 'LOCAL',  -- LOCAL / MINIO / ALIYUN / S3
    storage_path VARCHAR(500),
    bucket VARCHAR(100),
    content_type VARCHAR(100),
    upload_by VARCHAR(50),
    upload_time TIMESTAMP DEFAULT NOW(),
    deleted BOOLEAN DEFAULT FALSE
);
COMMENT ON TABLE sys_oss IS '文件存储';

-- 定时任务
CREATE TABLE sys_task (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT,
    task_name VARCHAR(100) NOT NULL,
    task_group VARCHAR(50) DEFAULT 'DEFAULT',
    invoke_target VARCHAR(500) NOT NULL,    -- bean.method 或 class.method
    cron_expression VARCHAR(100),
    misfire_policy VARCHAR(20) DEFAULT '3', -- 1=立即执行 2=执行一次 3=放弃执行
    concurrent SMALLINT DEFAULT 0,          -- 0=禁止 1=允许
    status SMALLINT DEFAULT 1,              -- 0=暂停 1=运行
    remark VARCHAR(500),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    deleted BOOLEAN DEFAULT FALSE
);
COMMENT ON TABLE sys_task IS '定时任务';

-- 参数配置
CREATE TABLE sys_config (
    id BIGSERIAL PRIMARY KEY,
    config_name VARCHAR(100) NOT NULL,
    config_key VARCHAR(100) NOT NULL,
    config_value TEXT,
    config_type VARCHAR(20) DEFAULT 'system',  -- system / business
    is_builtin SMALLINT DEFAULT 0,              -- 1=系统内置 0=自定义
    remark VARCHAR(500),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    deleted BOOLEAN DEFAULT FALSE,
    UNIQUE (config_key, deleted)
);
COMMENT ON TABLE sys_config IS '参数配置';

