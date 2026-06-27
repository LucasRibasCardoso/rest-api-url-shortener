INSERT INTO roles (id, name, is_default, created_at, updated_at)
VALUES ('11111111-1111-1111-1111-111111111111', 'USER', true, now(), now()),
       ('22222222-2222-2222-2222-222222222222', 'ADMIN', false, now(), now())
ON CONFLICT DO NOTHING;

INSERT INTO permissions (id, name, description, created_at, updated_at)
VALUES ('10000000-0000-0000-0000-000000000001', 'url:create', 'Create shortened URLs', now(), now()),
       ('10000000-0000-0000-0000-000000000002', 'url:read:own', 'Read own shortened URL details', now(), now()),
       ('10000000-0000-0000-0000-000000000003', 'url:list:own', 'List own shortened URLs', now(), now()),
       ('10000000-0000-0000-0000-000000000004', 'url:delete:own', 'Delete own shortened URLs', now(), now()),
       ('10000000-0000-0000-0000-000000000005', 'url:ranking:own', 'View ranking of own shortened URLs', now(), now()),

       ('10000000-0000-0000-0000-000000000006', 'url:read:any', 'Read any shortened URL details', now(), now()),
       ('10000000-0000-0000-0000-000000000007', 'url:list:any', 'List all shortened URLs', now(), now()),
       ('10000000-0000-0000-0000-000000000008', 'url:delete:any', 'Delete any shortened URL', now(), now())
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
         JOIN permissions p ON p.name IN (
                                          'url:create',
                                          'url:read:own',
                                          'url:list:own',
                                          'url:delete:own',
                                          'url:ranking:own'
    )
WHERE r.name = 'USER'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
         JOIN permissions p ON TRUE
WHERE r.name = 'ADMIN'
ON CONFLICT DO NOTHING;