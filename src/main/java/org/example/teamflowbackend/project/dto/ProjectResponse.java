package org.example.teamflowbackend.project.dto;

import org.example.teamflowbackend.project.ProjectStatus;

import java.time.Instant;
import java.util.UUID;

public record ProjectResponse (
        UUID id,
        String name,
        String description,
        ProjectStatus status,
        UUID ownerId,
        Instant createdAt
) {}
