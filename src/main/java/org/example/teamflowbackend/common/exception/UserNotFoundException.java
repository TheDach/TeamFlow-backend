package org.example.teamflowbackend.common.exception;

import java.util.UUID;

public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(String message) {
        super(message);
    }

    public UserNotFoundException(UUID id) {
        super("User not found: " + id);
    }
}
