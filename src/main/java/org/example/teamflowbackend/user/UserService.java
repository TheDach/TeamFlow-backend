package org.example.teamflowbackend.user;

import jakarta.persistence.EntityNotFoundException;
import org.example.teamflowbackend.security.CurrentUser;
import org.example.teamflowbackend.user.dto.PublicUserResponse;
import org.example.teamflowbackend.user.dto.UpdateUserRequest;
import org.example.teamflowbackend.user.dto.UserResponse;
import org.example.teamflowbackend.user.dto.UserSearchItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }


    // ============ GET /users/me ============
    @Transactional(readOnly = true)
    public UserResponse getCurrentUser() {
        String keycloakId = CurrentUser.keycloakId();
        User user = userRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return mapToUserResponse(user);
    }

    // ============ PATCH /users/me ============
    @Transactional
    public UserResponse updateCurrentUser(UpdateUserRequest request) {
        String keycloak = CurrentUser.keycloakId();
        User updateUser = userRepository.findByKeycloakId(keycloak)
                .orElseThrow(() -> new RuntimeException("User not found"));

        updateUser.setDisplayName(request.displayName());
        updateUser.setUpdatedAt(Instant.now());
        userRepository.save(updateUser);

        return mapToUserResponse(updateUser);
    }

    // ============ GET /users ============
    @Transactional(readOnly = true)
    public Page<UserSearchItem> searchActiveUsers(String displayName, Pageable pageable) {

        Page<User> page;
        if (displayName == null || displayName.isBlank()) {
            page = userRepository.findAllByStatus(UserStatus.ACTIVE, pageable);
        } else {
            page = userRepository.findAllByStatusAndDisplayNameContainingIgnoreCase(UserStatus.ACTIVE, displayName, pageable);
        }
        return page.map(this::mapToUserSearchItem);
    }

    // ============ GET /users/{id} ============
    @Transactional(readOnly = true)
    public PublicUserResponse getPublicUser(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return mapToPublicUserResponse(user);
    }




    private UserResponse mapToUserResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getStatus(),
                user.getCreatedAt()
        );
    }

    private UserSearchItem mapToUserSearchItem(User user) {
        return new UserSearchItem(
                user.getId(),
                user.getDisplayName(),
                user.getEmail()
        );
    }

    private PublicUserResponse mapToPublicUserResponse(User user) {
        return new PublicUserResponse(
                user.getId(),
                user.getDisplayName()
        );
    }
}
