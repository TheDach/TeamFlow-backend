package org.example.teamflowbackend.task.dto;

import org.example.teamflowbackend.task.TaskPriority;
import org.example.teamflowbackend.task.TaskStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TaskResponse(
        UUID id,
        UUID projectId,
        String title,
        String description,
        TaskStatus status,
        TaskPriority priority,
        UUID assigneeId,
        UUID createdBy,
        LocalDate dueDate,
        Instant createdAt,
        Instant updatedAt
) {}
