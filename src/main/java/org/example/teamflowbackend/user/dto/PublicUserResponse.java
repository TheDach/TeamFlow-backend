package org.example.teamflowbackend.user.dto;

import java.util.UUID;

public record PublicUserResponse (
        UUID id,
        String displayName
)
{}
