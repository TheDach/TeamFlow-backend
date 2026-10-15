package org.example.teamflowbackend.user.dto;

import org.example.teamflowbackend.user.UserStatus;

import java.time.Instant;
import java.util.UUID;

public record UserResponse (
        UUID id,
        String email,
        String displayName,
        UserStatus status,
        Instant createdAt
)
{}
