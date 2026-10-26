package org.example.teamflowbackend.common.exception;

public class ProjectAccessDeniedException extends RuntimeException {
    public ProjectAccessDeniedException(String message) {
        super(message);
    }

    public ProjectAccessDeniedException(Long projectId, Long userId) {
        super("User " + userId + " has no access to project " + projectId);
    }
}
