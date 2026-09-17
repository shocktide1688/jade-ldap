-- =====================================================
-- V6: 给 sys_project 加 deleted 字段
-- =====================================================
-- sys_project 是 V2 (demo 的迁移) 建的, 当时没 deleted 字段
-- SysProject 实体用 deleted 做软删, 必须有这一列
ALTER TABLE sys_project ADD COLUMN IF NOT EXISTS deleted BOOLEAN NOT NULL DEFAULT FALSE;
