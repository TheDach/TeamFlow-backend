package org.example.teamflowbackend.task.dto;
import java.util.UUID;
public record TaskAssigneeUpdateRequest(
        UUID assigneeId
) {}
