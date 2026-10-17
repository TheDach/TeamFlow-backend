package org.example.teamflowbackend.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProjectUpdateRequest(
        @Size(min = 3, max = 150)
        String name,

        @Size(max = 2000)
        String description
) {}
