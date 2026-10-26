package org.example.teamflowbackend.project.dto;

import org.example.teamflowbackend.project.ProjectMemberRole;

import java.util.UUID;

public record ProjectMemberDeleteResponse (
        UUID userId,
        String displayName
) {}
