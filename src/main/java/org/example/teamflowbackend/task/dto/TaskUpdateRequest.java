package org.example.teamflowbackend.task.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.example.teamflowbackend.task.TaskPriority;

import java.time.LocalDate;

public record TaskUpdateRequest(
        @Size(min = 1, max = 200)
        String title,

        @Size(max = 5000)
        String description,

        TaskPriority priority,
        LocalDate dueDate
) {}
