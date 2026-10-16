package org.example.teamflowbackend.common.exception;

public class CurrentUserNotFoundException extends RuntimeException {
    public CurrentUserNotFoundException(String message) {
        super(message);
    }

    public CurrentUserNotFoundException() {
        super("Current authenticated user not found");
    }
}
