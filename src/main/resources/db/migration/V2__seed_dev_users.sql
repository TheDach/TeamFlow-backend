INSERT INTO users (id, keycloak_id, email, display_name, status, created_at, updated_at)
VALUES
    (gen_random_uuid(), 'dev-user1', 'owner@teamflow.local',     'Alice Owner',    'ACTIVE',  NOW(), NOW()),
    (gen_random_uuid(), 'dev-user2', 'member1@teamflow.local',   'Bob Member',     'ACTIVE',  NOW(), NOW()),
    (gen_random_uuid(), 'dev-user3', 'member2@teamflow.local',   'Charlie Member', 'ACTIVE',  NOW(), NOW()),
    (gen_random_uuid(), 'dev-user4', 'member3@teamflow.local',   'Diana Member',   'ACTIVE',  NOW(), NOW()),
    (gen_random_uuid(), 'dev-user5', 'member4@teamflow.local',   'Eve Member',     'ACTIVE',  NOW(), NOW()),
    (gen_random_uuid(), 'dev-user6', 'member5@teamflow.local',   'Frank Member',   'ACTIVE',  NOW(), NOW()),
    (gen_random_uuid(), 'dev-user7', 'outsider@teamflow.local',  'George Outsider','ACTIVE',  NOW(), NOW()),
    (gen_random_uuid(), 'dev-user8', 'admin@teamflow.local',     'Helen Admin',    'ACTIVE',  NOW(), NOW()),
    (gen_random_uuid(), 'dev-user9', 'blocked@teamflow.local',   'Ivan Blocked',   'BLOCKED', NOW(), NOW()),
    (gen_random_uuid(), 'dev-user10', 'dev@teamflow.local',       'Dev User',       'ACTIVE',  NOW(), NOW());