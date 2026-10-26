package org.example.teamflowbackend.task.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.example.teamflowbackend.task.TaskPriority;

import java.time.LocalDate;
import java.util.UUID;

public record TaskCreateRequest(
        @Size(min = 1, max = 200)
        @NotBlank
        String title,
        @Size(max = 2000)
        String description,
        TaskPriority priority,
        UUID assigneeId,
        LocalDate dueDate
) {}
