package org.example.teamflowbackend.common.exception;

public class ProjectNotFoundException extends RuntimeException {
    public ProjectNotFoundException(String message) {
        super(message);
    }

    public ProjectNotFoundException(Long id) {
        super("Project with id " + id + " not found");
    }
}
