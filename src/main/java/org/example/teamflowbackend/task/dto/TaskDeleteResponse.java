package org.example.teamflowbackend.task.dto;

import java.util.UUID;

public record TaskDeleteResponse(
        UUID id,
        String title,
        String description
) {}
