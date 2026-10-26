package org.example.teamflowbackend.user.dto;

import java.util.UUID;

public record UserSearchItem(
        UUID id,
        String displayName,
        String email
) {}
