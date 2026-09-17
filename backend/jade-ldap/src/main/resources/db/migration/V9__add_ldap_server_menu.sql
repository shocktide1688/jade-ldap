-- Jade LDAP product navigation. This migration belongs only to the jade-ldap runnable module.
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, perms,
                      sort_order, visible, status, is_cache)
VALUES (200, 0, 'LDAP 目录', 'C', 'ldap', 'ldap/index', 'Connection',
        'ldap:directory:list', 1, 1, 1, 1)
ON CONFLICT (id) DO NOTHING;

INSERT INTO sys_role_menu (role_id, menu_id)
VALUES (1, 200)
ON CONFLICT DO NOTHING;

SELECT setval('sys_menu_id_seq', GREATEST(200, (SELECT COALESCE(MAX(id), 0) FROM sys_menu)));
