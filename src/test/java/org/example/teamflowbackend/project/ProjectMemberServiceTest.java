package org.example.teamflowbackend.project;

import org.example.teamflowbackend.common.exception.BusinessConflictException;
import org.example.teamflowbackend.common.exception.OwnerException;
import org.example.teamflowbackend.common.exception.ProjectAccessDeniedException;
import org.example.teamflowbackend.common.exception.UserExistsProjectException;
import org.example.teamflowbackend.project.dto.ProjectMemberAddRequest;
import org.example.teamflowbackend.project.dto.ProjectMemberDeleteResponse;
import org.example.teamflowbackend.project.dto.ProjectMemberResponse;
import org.example.teamflowbackend.task.TaskRepository;
import org.example.teamflowbackend.user.User;
import org.example.teamflowbackend.user.UserRepository;
import org.example.teamflowbackend.user.UserStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ProjectMemberServiceTest {

    @Mock
    private ProjectMemberRepository projectMemberRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private ProjectRepository projectRepository;

    @InjectMocks
    private ProjectMemberService projectMemberService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
        // OWNER добавляет участника
    void addMember_whenCurrentUserIsOwner_shouldCreateMember() {

        UUID projectId = UUID.randomUUID();
        UUID newUserId = UUID.randomUUID();

        authenticateAs("keycloak-owner");

        User owner = createUser(
                UUID.randomUUID(),
                "keycloak-owner",
                UserStatus.ACTIVE
        );

        User newUser = createUser(
                newUserId,
                "keycloak-member",
                UserStatus.ACTIVE
        );

        newUser.setDisplayName("Member");

        Project project = createProject(
                projectId,
                ProjectStatus.ACTIVE
        );

        ProjectMember ownerMember =
                createProjectMember(
                        project,
                        owner,
                        ProjectMemberRole.OWNER
                );

        when(
                projectMemberRepository
                        .findByProject_IdAndUser_KeycloakId(
                                projectId,
                                "keycloak-owner"
                        )
        ).thenReturn(Optional.of(ownerMember));

        when(projectRepository.findById(projectId))
                .thenReturn(Optional.of(project));

        when(userRepository.findById(newUserId))
                .thenReturn(Optional.of(newUser));

        when(
                projectMemberRepository
                        .existsByProject_IdAndUser_Id(
                                projectId,
                                newUserId
                        )
        ).thenReturn(false);

        when(
                projectMemberRepository
                        .save(any(ProjectMember.class))
        ).thenAnswer(invocation ->
                invocation.getArgument(0)
        );

        ProjectMemberResponse response =
                projectMemberService.addMemberIntoProject(
                        projectId,
                        new ProjectMemberAddRequest(newUserId)
                );

        assertThat(response.userId())
                .isEqualTo(newUserId);

        assertThat(response.displayName())
                .isEqualTo("Member");

        assertThat(response.status())
                .isEqualTo(ProjectMemberRole.MEMBER);

        assertThat(response.joinedAt())
                .isNotNull();

        ArgumentCaptor<ProjectMember> captor =
                ArgumentCaptor.forClass(ProjectMember.class);

        verify(projectMemberRepository)
                .save(captor.capture());

        ProjectMember savedMember =
                captor.getValue();

        assertThat(savedMember.getProject())
                .isSameAs(project);

        assertThat(savedMember.getUser())
                .isSameAs(newUser);

        assertThat(savedMember.getRole())
                .isEqualTo(ProjectMemberRole.MEMBER);
    }

    @Test
        //MEMBER не может добавить участника
    void addMember_whenCurrentUserIsMember_shouldThrowAccessDenied() {

        UUID projectId = UUID.randomUUID();

        authenticateAs("keycloak-member");

        User currentUser = createUser(
                UUID.randomUUID(),
                "keycloak-member",
                UserStatus.ACTIVE
        );

        Project project =
                createProject(
                        projectId,
                        ProjectStatus.ACTIVE
                );

        ProjectMember currentMember =
                createProjectMember(
                        project,
                        currentUser,
                        ProjectMemberRole.MEMBER
                );

        when(
                projectMemberRepository
                        .findByProject_IdAndUser_KeycloakId(
                                projectId,
                                "keycloak-member"
                        )
        ).thenReturn(Optional.of(currentMember));

        assertThatThrownBy(() ->
                projectMemberService.addMemberIntoProject(
                        projectId,
                        new ProjectMemberAddRequest(
                                UUID.randomUUID()
                        )
                )
        )
                .isInstanceOf(
                        ProjectAccessDeniedException.class
                );

        verifyNoInteractions(userRepository);
        verify(projectMemberRepository, never())
                .save(any());
    }

    @Test
        // Нельзя добавить участника второй раз
    void addMember_whenUserAlreadyMember_shouldThrowConflict() {

        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        authenticateAs("keycloak-owner");

        User owner = createUser(
                UUID.randomUUID(),
                "keycloak-owner",
                UserStatus.ACTIVE
        );

        User existingUser = createUser(
                userId,
                "keycloak-member",
                UserStatus.ACTIVE
        );

        Project project =
                createProject(
                        projectId,
                        ProjectStatus.ACTIVE
                );

        ProjectMember ownerMember =
                createProjectMember(
                        project,
                        owner,
                        ProjectMemberRole.OWNER
                );

        when(projectRepository.findById(projectId))
                .thenReturn(Optional.of(project));

        when(
                projectMemberRepository
                        .findByProject_IdAndUser_KeycloakId(
                                projectId,
                                "keycloak-owner"
                        )
        ).thenReturn(Optional.of(ownerMember));

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(existingUser));

        when(
                projectMemberRepository
                        .existsByProject_IdAndUser_Id(
                                projectId,
                                userId
                        )
        ).thenReturn(true);

        assertThatThrownBy(() ->
                projectMemberService.addMemberIntoProject(
                        projectId,
                        new ProjectMemberAddRequest(userId)
                )
        )
                .isInstanceOf(
                        UserExistsProjectException.class
                );

        verify(projectMemberRepository, never())
                .save(any());
    }

    @Test
        // BLOCKED пользователя добавлять нельзя
    void addMember_whenUserIsBlocked_shouldThrowAccessDenied() {

        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        authenticateAs("keycloak-owner");

        User owner = createUser(
                UUID.randomUUID(),
                "keycloak-owner",
                UserStatus.ACTIVE
        );

        User blockedUser = createUser(
                userId,
                "keycloak-blocked",
                UserStatus.BLOCKED
        );

        Project project =
                createProject(
                        projectId,
                        ProjectStatus.ACTIVE
                );

        ProjectMember ownerMember =
                createProjectMember(
                        project,
                        owner,
                        ProjectMemberRole.OWNER
                );

        when(projectRepository.findById(projectId))
                .thenReturn(Optional.of(project));

        when(
                projectMemberRepository
                        .findByProject_IdAndUser_KeycloakId(
                                projectId,
                                "keycloak-owner"
                        )
        ).thenReturn(Optional.of(ownerMember));

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(blockedUser));

        when(
                projectMemberRepository
                        .existsByProject_IdAndUser_Id(
                                projectId,
                                userId
                        )
        ).thenReturn(false);

        assertThatThrownBy(() ->
                projectMemberService.addMemberIntoProject(
                        projectId,
                        new ProjectMemberAddRequest(userId)
                )
        )
                .isInstanceOf(
                        ProjectAccessDeniedException.class
                );

        verify(projectMemberRepository, never())
                .save(any());
    }

    @Test
        // В архивный проект нельзя добавлять участников
    void addMember_whenProjectArchived_shouldThrowConflict() {

        UUID projectId = UUID.randomUUID();

        authenticateAs("keycloak-owner");

        User owner = createUser(
                UUID.randomUUID(),
                "keycloak-owner",
                UserStatus.ACTIVE
        );

        Project project =
                createProject(
                        projectId,
                        ProjectStatus.ARCHIVED
                );

        ProjectMember ownerMember =
                createProjectMember(
                        project,
                        owner,
                        ProjectMemberRole.OWNER
                );

        when(
                projectMemberRepository
                        .findByProject_IdAndUser_KeycloakId(
                                projectId,
                                "keycloak-owner"
                        )
        ).thenReturn(Optional.of(ownerMember));

        assertThatThrownBy(() ->
                projectMemberService.addMemberIntoProject(
                        projectId,
                        new ProjectMemberAddRequest(
                                UUID.randomUUID()
                        )
                )
        )
                .isInstanceOf(
                        BusinessConflictException.class
                )
                .hasMessageContaining(
                        "Archived project"
                );

        verifyNoInteractions(userRepository);
        verify(projectMemberRepository, never())
                .save(any());
    }

    @Test
        // удаление MEMBER
    void deleteMember_whenCurrentUserIsOwner_shouldClearAssigneeAndDeleteMember() {

        UUID projectId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();

        authenticateAs("keycloak-owner");

        User owner = createUser(
                UUID.randomUUID(),
                "keycloak-owner",
                UserStatus.ACTIVE
        );

        User memberUser = createUser(
                memberId,
                "keycloak-member",
                UserStatus.ACTIVE
        );

        memberUser.setDisplayName("Member");

        Project project = createProject(
                projectId,
                ProjectStatus.ACTIVE
        );

        ProjectMember ownerMember = createProjectMember(
                project,
                owner,
                ProjectMemberRole.OWNER
        );

        ProjectMember memberToDelete = createProjectMember(
                project,
                memberUser,
                ProjectMemberRole.MEMBER
        );

        when(
                projectMemberRepository
                        .findByProject_IdAndUser_KeycloakId(
                                projectId,
                                "keycloak-owner"
                        )
        ).thenReturn(Optional.of(ownerMember));

        when(
                projectMemberRepository
                        .findByProject_IdAndUser_Id(
                                projectId,
                                memberId
                        )
        ).thenReturn(Optional.of(memberToDelete));

        ProjectMemberDeleteResponse response =
                projectMemberService.deleteMemberProject(
                        projectId,
                        memberId
                );

        assertThat(response.userId())
                .isEqualTo(memberId);

        assertThat(response.displayName())
                .isEqualTo("Member");

        InOrder inOrder = inOrder(
                taskRepository,
                projectMemberRepository
        );

        inOrder.verify(taskRepository)
                .clearAssignee(
                        projectId,
                        memberId
                );

        inOrder.verify(projectMemberRepository)
                .delete(memberToDelete);
    }

    @Test // OWNER нельзя удалить
    void deleteMember_whenTargetIsOwner_shouldThrowOwnerException() {

        UUID projectId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        authenticateAs("keycloak-owner");

        User owner = createUser(
                ownerId,
                "keycloak-owner",
                UserStatus.ACTIVE
        );

        Project project =
                createProject(
                        projectId,
                        ProjectStatus.ACTIVE
                );

        ProjectMember ownerMember =
                createProjectMember(
                        project,
                        owner,
                        ProjectMemberRole.OWNER
                );

        when(
                projectMemberRepository
                        .findByProject_IdAndUser_KeycloakId(
                                projectId,
                                "keycloak-owner"
                        )
        ).thenReturn(Optional.of(ownerMember));

        when(
                projectMemberRepository
                        .findByProject_IdAndUser_Id(
                                projectId,
                                ownerId
                        )
        ).thenReturn(Optional.of(ownerMember));

        assertThatThrownBy(() ->
                projectMemberService.deleteMemberProject(
                        projectId,
                        ownerId
                )
        )
                .isInstanceOf(
                        OwnerException.class
                );

        verifyNoInteractions(taskRepository);

        verify(projectMemberRepository, never())
                .delete(any());
    }


    private void authenticateAs(
            String keycloakId
    ) {

        Jwt jwt = Jwt
                .withTokenValue("test-token")
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

        JwtAuthenticationToken authentication = new JwtAuthenticationToken(
                jwt,
                List.of(
                        new SimpleGrantedAuthority(
                                "ROLE_USER"
                        )
                )
        );

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
    }

    private User createUser(
            UUID id,
            String keycloakId,
            UserStatus status
    ) {

        User user = new User();

        user.setId(id);
        user.setKeycloakId(keycloakId);
        user.setEmail(
                keycloakId + "@teamflow.local"
        );
        user.setDisplayName(keycloakId);
        user.setStatus(status);
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());

        return user;
    }

    private Project createProject(
            UUID id,
            ProjectStatus status
    ) {

        Instant now = Instant.now();

        Project project = new Project();

        project.setId(id);
        project.setName("Test project");
        project.setDescription(
                "Test description"
        );
        project.setStatus(status);
        project.setCreatedAt(now);
        project.setUpdatedAt(now);

        return project;
    }

    private ProjectMember createProjectMember(
            Project project,
            User user,
            ProjectMemberRole role
    ) {

        ProjectMember member =
                new ProjectMember();

        member.setId(UUID.randomUUID());
        member.setProject(project);
        member.setUser(user);
        member.setRole(role);
        member.setJoinedAt(Instant.now());

        return member;
    }

}
