package org.example.teamflowbackend.common.exception;

public class UserExistsProjectException extends RuntimeException {
    public UserExistsProjectException(String message) {
        super(message);
    }
}
