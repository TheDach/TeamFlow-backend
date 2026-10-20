package org.example.teamflowbackend.common.exception;

import java.util.UUID;

public class ProjectNotFoundException extends RuntimeException {
    public ProjectNotFoundException(String message) {
        super(message);
    }

    public ProjectNotFoundException(UUID id) {
        super("Project with id " + id + " not found");
    }
}
