package org.example.teamflowbackend.common.exception;

import java.util.UUID;

public class TaskNotFoundException extends RuntimeException {
    public TaskNotFoundException(UUID id) { super("Task not found id: " + id); }
    public TaskNotFoundException(String message) { super(message); }
}
