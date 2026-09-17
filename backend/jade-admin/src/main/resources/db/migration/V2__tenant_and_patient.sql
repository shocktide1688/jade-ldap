-- =====================================================
-- V2: 多租户 + 病人信息（字段加密演示）
-- =====================================================

-- 租户表
CREATE TABLE sys_tenant (
    id          BIGSERIAL    PRIMARY KEY,
    code        VARCHAR(64)  NOT NULL UNIQUE,
    name        VARCHAR(128) NOT NULL,
    status      SMALLINT     NOT NULL DEFAULT 1,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_sys_tenant_status ON sys_tenant(status);

COMMENT ON TABLE sys_tenant IS '租户表';

-- 项目表（演示多租户隔离）
CREATE TABLE sys_project (
    id          BIGSERIAL    PRIMARY KEY,
    tenant_id   BIGINT       NOT NULL,
    name        VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_sys_project_tenant ON sys_project(tenant_id);
CREATE INDEX idx_sys_project_created ON sys_project(created_at);

COMMENT ON COLUMN sys_project.tenant_id IS '所属租户（多租户隔离字段）';

-- 病人表（演示字段加密）
CREATE TABLE sys_patient (
    id          BIGSERIAL    PRIMARY KEY,
    name        VARCHAR(64)  NOT NULL,
    id_card     VARCHAR(512),
    phone       VARCHAR(512),
    diagnosis   VARCHAR(256),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

COMMENT ON COLUMN sys_patient.id_card IS '身份证（AES-256-GCM 加密存储）';
COMMENT ON COLUMN sys_patient.phone   IS '手机号（AES-256-GCM 加密存储）';

-- 初始数据
INSERT INTO sys_tenant (code, name) VALUES
    ('t_001', '租户 A'),
    ('t_002', '租户 B');

INSERT INTO sys_project (tenant_id, name, description) VALUES
    (1, 'A 的项目 1', '这是 A 的项目'),
    (2, 'B 的项目 1', '这是 B 的项目');
