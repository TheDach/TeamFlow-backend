package org.example.teamflowbackend.common.exception;

import java.util.UUID;

public class CommentNotFoundException extends RuntimeException {
    public CommentNotFoundException(UUID id) { super("Comment not found id: " + id); }
}
