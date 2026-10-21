package org.example.teamflowbackend.comment.dto;

import java.util.UUID;

public record CommentDeleteResponse(
        UUID id,
        UUID author,
        String content
) {}
