-- TeamFlow: начальная схема БД для PostgreSQL

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Функция для автоматического обновления updated_at
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- =========================================================
-- users
-- =========================================================
CREATE TABLE users (
                       id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                       keycloak_id   VARCHAR(100) NOT NULL,
                       email         VARCHAR(255) NOT NULL,
                       display_name  VARCHAR(100) NOT NULL,
                       status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE', -- ACTIVE / BLOCKED
                       created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
                       updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

                       CONSTRAINT uq_users_keycloak_id UNIQUE (keycloak_id),
                       CONSTRAINT uq_users_email       UNIQUE (email),
                       CONSTRAINT chk_users_status     CHECK (status IN ('ACTIVE', 'BLOCKED')),
                       CONSTRAINT chk_users_display_name CHECK (
                           char_length(trim(display_name)) BETWEEN 1 AND 100
                           )
);

-- =========================================================
-- projects
-- =========================================================
CREATE TABLE projects (
                          id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                          name          VARCHAR(150) NOT NULL,
                          description   TEXT,
                          status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE', -- ACTIVE / BLOCKED
                          created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
                          updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

                          CONSTRAINT chk_projects_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
                          CONSTRAINT chk_projects_name CHECK (
                              char_length(trim(name)) BETWEEN 3 AND 150
                              ),
                          CONSTRAINT chk_projects_description CHECK (
                              description IS NULL OR char_length(description) <= 2000
                              )
);

-- =========================================================
-- project_members
-- =========================================================
CREATE TABLE project_members (
                                 id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                 project_id    UUID NOT NULL,
                                 user_id       UUID NOT NULL,
                                 role          VARCHAR(20) NOT NULL, -- OWNER / MEMBER
                                 joined_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),

                                 CONSTRAINT fk_project_members_project
                                     FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,

                                 CONSTRAINT fk_project_members_user
                                     FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,

                                 CONSTRAINT uq_project_members_project_user
                                     UNIQUE (project_id, user_id),

                                 CONSTRAINT chk_project_members_role
                                     CHECK (role IN ('OWNER', 'MEMBER'))
);

-- Не более одного OWNER на проект.
-- Наличие хотя бы одного OWNER обеспечивается сервисной логикой в транзакции.
CREATE UNIQUE INDEX ux_project_members_one_owner
    ON project_members(project_id)
    WHERE role = 'OWNER';

-- =========================================================
-- tasks
-- =========================================================
CREATE TABLE tasks (
                       id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                       project_id    UUID NOT NULL,
                       title         VARCHAR(200) NOT NULL,
                       description   TEXT,
                       status        VARCHAR(30)  NOT NULL DEFAULT 'TODO', -- IN_PROGRESS, IN_REVIEW, DONE, TODO
                       priority      VARCHAR(20)  NOT NULL DEFAULT 'MEDIUM', -- LOW, MEDIUM, HIGH, CRITICAL
                       assignee_id   UUID,
                       created_by    UUID NOT NULL,
                       due_date      DATE,
                       created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
                       updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

                       CONSTRAINT fk_tasks_project
                           FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,

                       CONSTRAINT fk_tasks_assignee
                           FOREIGN KEY (assignee_id) REFERENCES users(id) ON DELETE SET NULL,

                       CONSTRAINT fk_tasks_created_by
                           FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE RESTRICT,

                       CONSTRAINT chk_tasks_status
                           CHECK (status IN ('TODO', 'IN_PROGRESS', 'IN_REVIEW', 'DONE')),

                       CONSTRAINT chk_tasks_priority
                           CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),

                       CONSTRAINT chk_tasks_title
                           CHECK (char_length(trim(title)) BETWEEN 1 AND 200)
);

-- =========================================================
-- comments
-- =========================================================
CREATE TABLE comments (
                          id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                          task_id       UUID NOT NULL,
                          author_id     UUID NOT NULL,
                          content       TEXT NOT NULL,
                          created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                          updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),

                          CONSTRAINT fk_comments_task
                              FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE,

                          CONSTRAINT fk_comments_author
                              FOREIGN KEY (author_id) REFERENCES users(id) ON DELETE RESTRICT,

                          CONSTRAINT chk_comments_content
                              CHECK (char_length(trim(content)) BETWEEN 1 AND 2000)
);

-- =========================================================
-- Индексы
-- =========================================================
CREATE INDEX idx_members_user_id
    ON project_members(user_id);

CREATE INDEX idx_tasks_project_status
    ON tasks(project_id, status);

CREATE INDEX idx_tasks_project_assignee
    ON tasks(project_id, assignee_id);

CREATE INDEX idx_tasks_project_due_date
    ON tasks(project_id, due_date);

CREATE INDEX idx_comments_task_created
    ON comments(task_id, created_at);

-- =========================================================
-- Триггеры updated_at
-- =========================================================
CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_projects_updated_at
    BEFORE UPDATE ON projects
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_tasks_updated_at
    BEFORE UPDATE ON tasks
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_comments_updated_at
    BEFORE UPDATE ON comments
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();