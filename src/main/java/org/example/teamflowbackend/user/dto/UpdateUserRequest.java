package org.example.teamflowbackend.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest (

        @NotBlank
        @Size(min = 1, max = 100)
        String displayName
)
{}
