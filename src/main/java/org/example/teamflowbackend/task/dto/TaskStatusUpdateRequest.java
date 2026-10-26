package org.example.teamflowbackend.task.dto;

import jakarta.validation.constraints.NotNull;
import org.example.teamflowbackend.task.TaskStatus;

public record TaskStatusUpdateRequest(
        @NotNull TaskStatus status
) {
}
