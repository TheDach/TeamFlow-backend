ALTER TABLE projects
    ADD COLUMN owner_id UUID NOT NULL;

ALTER TABLE projects
    ADD CONSTRAINT fk_projects_owner
        FOREIGN KEY (owner_id)
            REFERENCES users(id);

CREATE INDEX idx_projects_owner ON projects(owner_id);