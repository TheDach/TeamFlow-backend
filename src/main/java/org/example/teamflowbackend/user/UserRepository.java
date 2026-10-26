package org.example.teamflowbackend.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByKeycloakId(String keycloak);
    Optional<User> findByEmailIgnoreCase(String email);

    Page<User> findAllByStatus(UserStatus status, Pageable pageable);
    Page<User> findAllByStatusAndDisplayNameContainingIgnoreCase(UserStatus status, String displayName, Pageable pageable);
}
