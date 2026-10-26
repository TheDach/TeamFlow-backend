package org.example.teamflowbackend.security;

import org.example.teamflowbackend.user.User;
import org.example.teamflowbackend.user.UserRepository;
import org.example.teamflowbackend.user.UserStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class LocalUserIdentityService {

    private final UserRepository userRepository;

    public LocalUserIdentityService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }


    @Transactional
    public User sync(Jwt jwt) {
        String keycloakId = jwt.getSubject();
        String email = jwt.getClaimAsString("email");

        if (keycloakId == null || keycloakId.isBlank()) {
            throw new IllegalStateException("JWT does not contain sub");
        }

        if (email == null || email.isBlank()) {
            throw new IllegalStateException("JWT does not contain email");
        }

        String displayName = extractDisplayName(jwt);

        return userRepository.findByKeycloakId(keycloakId)
                .map(user -> updateExistingUser(
                        user,
                        email,
                        displayName
                ))
                .orElseGet(() ->
                        createUser(
                                keycloakId,
                                email,
                                displayName
                        )
                );
    }


    private User createUser(
            String keycloakId,
            String email,
            String displayName
    ) {

        User user = new User();


        Instant created = Instant.now();
        user.setKeycloakId(keycloakId);
        user.setEmail(email);
        user.setDisplayName(displayName);
        user.setStatus(UserStatus.ACTIVE);
        user.setCreatedAt(created);
        user.setUpdatedAt(created);

        return userRepository.save(user);
    }

    private User updateExistingUser(
            User user,
            String email,
            String displayName
    ) {

        user.setEmail(email);
        user.setDisplayName(displayName);

        return user;
    }

    private String extractDisplayName(Jwt jwt) {

        String name = jwt.getClaimAsString("name");

        if (name != null && !name.isBlank()) {
            return name;
        }

        String username =
                jwt.getClaimAsString("preferred_username");

        if (username != null && !username.isBlank()) {
            return username;
        }

        return jwt.getClaimAsString("email");
    }
}
