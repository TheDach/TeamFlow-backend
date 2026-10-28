package org.example.teamflowbackend.user;

import org.example.teamflowbackend.common.exception.CurrentUserNotFoundException;
import org.example.teamflowbackend.common.exception.UserNotFoundException;
import org.example.teamflowbackend.user.dto.PublicUserResponse;
import org.example.teamflowbackend.user.dto.UpdateUserRequest;
import org.example.teamflowbackend.user.dto.UserResponse;
import org.example.teamflowbackend.user.dto.UserSearchItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getCurrentUser_shouldReturnAuthenticatedUser() {

        UUID userId = UUID.randomUUID();

        authenticateAs("keycloak-user");

        User user = createUser(
                userId,
                "keycloak-user",
                "user@teamflow.local",
                "Test User",
                UserStatus.ACTIVE
        );

        when(
                userRepository.findByKeycloakId(
                        "keycloak-user"
                )
        ).thenReturn(Optional.of(user));

        UserResponse response =
                userService.getCurrentUser();

        assertThat(response.id())
                .isEqualTo(userId);

        assertThat(response.email())
                .isEqualTo("user@teamflow.local");

        assertThat(response.displayName())
                .isEqualTo("Test User");

        assertThat(response.status())
                .isEqualTo(UserStatus.ACTIVE);

        verify(userRepository)
                .findByKeycloakId(
                        "keycloak-user"
                );
    }

    @Test
    void getCurrentUser_whenLocalUserDoesNotExist_shouldThrow() {

        authenticateAs("missing-user");

        when(
                userRepository.findByKeycloakId(
                        "missing-user"
                )
        ).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                userService.getCurrentUser()
        )
                .isInstanceOf(
                        CurrentUserNotFoundException.class
                );
    }

    @Test
    void updateCurrentUser_shouldTrimAndUpdateDisplayName() {

        UUID userId = UUID.randomUUID();

        authenticateAs("keycloak-user");

        User user = createUser(
                userId,
                "keycloak-user",
                "user@teamflow.local",
                "Old Name",
                UserStatus.ACTIVE
        );

        Instant oldUpdatedAt =
                Instant.parse(
                        "2026-10-01T10:00:00Z"
                );

        user.setUpdatedAt(oldUpdatedAt);

        when(
                userRepository.findByKeycloakId(
                        "keycloak-user"
                )
        ).thenReturn(Optional.of(user));

        UpdateUserRequest request =
                new UpdateUserRequest(
                        "   New Name   "
                );

        UserResponse response =
                userService.updateCurrentUser(
                        request
                );

        assertThat(response.displayName())
                .isEqualTo("New Name");

        assertThat(user.getDisplayName())
                .isEqualTo("New Name");

        assertThat(user.getUpdatedAt())
                .isAfter(oldUpdatedAt);

        verify(userRepository)
                .save(user);
    }

    @Test
    void searchActiveUsers_withoutName_shouldReturnAllActiveUsers() {

        Pageable pageable =
                PageRequest.of(0, 20);

        User first = createUser(
                UUID.randomUUID(),
                "keycloak-first",
                "first@teamflow.local",
                "Alice",
                UserStatus.ACTIVE
        );

        User second = createUser(
                UUID.randomUUID(),
                "keycloak-second",
                "second@teamflow.local",
                "Bob",
                UserStatus.ACTIVE
        );

        Page<User> users =
                new PageImpl<>(
                        List.of(first, second),
                        pageable,
                        2
                );

        when(
                userRepository.findAllByStatus(
                        UserStatus.ACTIVE,
                        pageable
                )
        ).thenReturn(users);

        Page<UserSearchItem> response =
                userService.searchActiveUsers(
                        null,
                        pageable
                );

        assertThat(response.getContent())
                .hasSize(2);

        assertThat(
                response.getContent()
                        .get(0)
                        .displayName()
        ).isEqualTo("Alice");

        verify(userRepository)
                .findAllByStatus(
                        UserStatus.ACTIVE,
                        pageable
                );

        verify(
                userRepository,
                never()
        ).findAllByStatusAndDisplayNameContainingIgnoreCase(
                any(),
                anyString(),
                any()
        );
    }

    @Test
    void searchActiveUsers_withName_shouldTrimSearchValue() {

        Pageable pageable =
                PageRequest.of(0, 20);

        User user = createUser(
                UUID.randomUUID(),
                "keycloak-alice",
                "alice@teamflow.local",
                "Alice",
                UserStatus.ACTIVE
        );

        Page<User> users =
                new PageImpl<>(
                        List.of(user),
                        pageable,
                        1
                );

        when(
                userRepository
                        .findAllByStatusAndDisplayNameContainingIgnoreCase(
                                UserStatus.ACTIVE,
                                "Ali",
                                pageable
                        )
        ).thenReturn(users);

        Page<UserSearchItem> response =
                userService.searchActiveUsers(
                        "   Ali   ",
                        pageable
                );

        assertThat(response.getTotalElements())
                .isEqualTo(1);

        assertThat(
                response.getContent()
                        .get(0)
                        .displayName()
        ).isEqualTo("Alice");

        verify(userRepository)
                .findAllByStatusAndDisplayNameContainingIgnoreCase(
                        UserStatus.ACTIVE,
                        "Ali",
                        pageable
                );
    }

    @Test
    void getPublicUser_whenUserExists_shouldReturnPublicDataOnly() {

        UUID userId = UUID.randomUUID();

        User user = createUser(
                userId,
                "keycloak-user",
                "secret@teamflow.local",
                "Public Name",
                UserStatus.ACTIVE
        );

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        PublicUserResponse response =
                userService.getPublicUser(userId);

        assertThat(response.id())
                .isEqualTo(userId);

        assertThat(response.displayName())
                .isEqualTo("Public Name");
    }

    @Test
    void getPublicUser_whenUserDoesNotExist_shouldThrowNotFound() {

        UUID userId = UUID.randomUUID();

        when(userRepository.findById(userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                userService.getPublicUser(userId)
        )
                .isInstanceOf(
                        UserNotFoundException.class
                )
                .hasMessageContaining(
                        userId.toString()
                );
    }

    private void authenticateAs(
            String keycloakId
    ) {

        Jwt jwt = Jwt.withTokenValue(
                        "test-token"
                )
                .header("alg", "none")
                .subject(keycloakId)
                .claim(
                        "email",
                        keycloakId + "@teamflow.local"
                )
                .issuedAt(Instant.now())
                .expiresAt(
                        Instant.now()
                                .plusSeconds(3600)
                )
                .build();

        JwtAuthenticationToken authentication =
                new JwtAuthenticationToken(
                        jwt,
                        List.of(
                                new SimpleGrantedAuthority(
                                        "ROLE_USER"
                                )
                        )
                );

        SecurityContext context =
                SecurityContextHolder
                        .createEmptyContext();

        context.setAuthentication(
                authentication
        );

        SecurityContextHolder.setContext(
                context
        );
    }
    private User createUser(
            UUID id,
            String keycloakId,
            String email,
            String displayName,
            UserStatus status
    ) {

        Instant now = Instant.now();

        User user = new User();

        user.setId(id);
        user.setKeycloakId(keycloakId);
        user.setEmail(email);
        user.setDisplayName(displayName);
        user.setStatus(status);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);

        return user;
    }
}