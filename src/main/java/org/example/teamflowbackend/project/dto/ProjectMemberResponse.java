package org.example.teamflowbackend.project.dto;

import org.example.teamflowbackend.project.ProjectMemberRole;

import java.time.Instant;
import java.util.UUID;

public record ProjectMemberResponse(
        UUID userId,
        String displayName,
        ProjectMemberRole status,
        Instant joinedAt
) {}
