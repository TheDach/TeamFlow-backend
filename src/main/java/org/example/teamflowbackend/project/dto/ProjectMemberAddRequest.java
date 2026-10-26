package org.example.teamflowbackend.project.dto;


import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ProjectMemberAddRequest(
        @NotNull
        UUID userId
) {}
