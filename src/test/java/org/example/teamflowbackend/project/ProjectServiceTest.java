package org.example.teamflowbackend.project;


import org.example.teamflowbackend.common.exception.BusinessConflictException;
import org.example.teamflowbackend.common.exception.ProjectAccessDeniedException;
import org.example.teamflowbackend.common.exception.ProjectNotFoundException;
import org.example.teamflowbackend.project.dto.ProjectCreateRequest;
import org.example.teamflowbackend.project.dto.ProjectResponse;
import org.example.teamflowbackend.project.dto.ProjectUpdateRequest;
import org.example.teamflowbackend.user.User;
import org.example.teamflowbackend.user.UserRepository;
import org.example.teamflowbackend.user.UserStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
public class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProjectMemberRepository projectMemberRepository;

    @InjectMocks
    private ProjectService projectService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createProject_shouldCreateProjectAndMakeCurrentUserOwner() {

        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        authenticateAs("keycloak-owner");

        User owner = createUser(
                userId,
                "keycloak-owner",
                UserStatus.ACTIVE
        );

        when(
                userRepository.findByKeycloakId(
                        "keycloak-owner"
                )
        ).thenReturn(Optional.of(owner));

        when(projectRepository.save(any(Project.class)))
                .thenAnswer(invocation -> {

                    Project project =
                            invocation.getArgument(0);

                    project.setId(projectId);

                    return project;
                });

        ProjectMember ownerMemberForResponse = new ProjectMember();
        ownerMemberForResponse.setUser(owner);
        ownerMemberForResponse.setRole(ProjectMemberRole.OWNER);

        when(projectMemberRepository
                .findByProject_IdAndRole(
                        projectId,
                        ProjectMemberRole.OWNER
                )
        )
                .thenReturn(
                        Optional.of(ownerMemberForResponse)
                );

        ProjectCreateRequest request =
                new ProjectCreateRequest(
                        "  TeamFlow Backend  ",
                        "Task management system"
                );

        ProjectResponse response = projectService.createProject(request);

        assertThat(response.id())
                .isEqualTo(projectId);

        assertThat(response.name())
                .isEqualTo("TeamFlow Backend");

        assertThat(response.description())
                .isEqualTo("Task management system");

        assertThat(response.status())
                .isEqualTo(ProjectStatus.ACTIVE);

        assertThat(response.ownerId())
                .isEqualTo(userId);

        ArgumentCaptor<Project> projectCaptor =
                ArgumentCaptor.forClass(Project.class);

        verify(projectRepository)
                .save(projectCaptor.capture());

        Project savedProject =
                projectCaptor.getValue();

        assertThat(savedProject.getName())
                .isEqualTo("TeamFlow Backend");

        assertThat(savedProject.getStatus())
                .isEqualTo(ProjectStatus.ACTIVE);

        ArgumentCaptor<ProjectMember> memberCaptor =
                ArgumentCaptor.forClass(
                        ProjectMember.class
                );

        verify(projectMemberRepository)
                .save(memberCaptor.capture());

        ProjectMember ownerMember =
                memberCaptor.getValue();

        assertThat(ownerMember.getProject())
                .isSameAs(savedProject);

        assertThat(ownerMember.getUser())
                .isSameAs(owner);

        assertThat(ownerMember.getRole())
                .isEqualTo(ProjectMemberRole.OWNER);

        assertThat(ownerMember.getJoinedAt())
                .isNotNull();
    }

    @Test
    void getProjectById_whenUserIsOutsider_shouldThrowNotFound() {

        UUID projectId = UUID.randomUUID();

        authenticateAs("keycloak-outsider");

        when(
                projectMemberRepository
                        .findByProject_IdAndUser_KeycloakId(
                                projectId,
                                "keycloak-outsider"
                        )
        ).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                projectService.getProjectById(projectId)
        )
                .isInstanceOf(
                        ProjectNotFoundException.class
                );

        verify(projectRepository, never())
                .findById(any());
    }

    @Test
    void updateProject_whenCurrentUserIsMember_shouldThrowAccessDenied() {

        UUID projectId = UUID.randomUUID();

        authenticateAs("keycloak-member");

        User memberUser =
                createUser(
                        UUID.randomUUID(),
                        "keycloak-member",
                        UserStatus.ACTIVE
                );

        Project project = createProject(
                projectId,
                ProjectStatus.ACTIVE
        );

        ProjectMember member = createProjectMember(
                project,
                memberUser,
                ProjectMemberRole.MEMBER
        );

        when(
                projectMemberRepository
                        .findByProject_IdAndUser_KeycloakId(
                                projectId,
                                "keycloak-member"
                        )
        ).thenReturn(Optional.of(member));

        ProjectUpdateRequest request =
                new ProjectUpdateRequest(
                        "New project name",
                        "New description"
                );

        assertThatThrownBy(() ->
                projectService.updateProject(
                        projectId,
                        request
                )
        )
                .isInstanceOf(
                        ProjectAccessDeniedException.class
                );

        assertThat(project.getName())
                .isEqualTo("Test project");
    }

    @Test
    void updateProject_whenProjectIsArchived_shouldThrowConflict() {

        UUID projectId = UUID.randomUUID();

        authenticateAs("keycloak-owner");

        User owner = createUser(
                UUID.randomUUID(),
                "keycloak-owner",
                UserStatus.ACTIVE
        );

        Project project = createProject(
                projectId,
                ProjectStatus.ARCHIVED
        );

        ProjectMember ownerMember = createProjectMember(
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

        ProjectUpdateRequest request =
                new ProjectUpdateRequest(
                        "New project name",
                        null
                );

        assertThatThrownBy(() ->
                projectService.updateProject(
                        projectId,
                        request
                )
        )
                .isInstanceOf(
                        BusinessConflictException.class
                )
                .hasMessageContaining(
                        "Archived project"
                );

        assertThat(project.getStatus())
                .isEqualTo(ProjectStatus.ARCHIVED);

        assertThat(project.getName())
                .isEqualTo("Test project");
    }

    @Test
    void archiveProject_whenCurrentUserIsOwner_shouldArchiveProject() {

        UUID projectId = UUID.randomUUID();

        authenticateAs("keycloak-owner");

        User owner = createUser(
                UUID.randomUUID(),
                "keycloak-owner",
                UserStatus.ACTIVE
        );

        Project project = createProject(
                projectId,
                ProjectStatus.ACTIVE
        );

        ProjectMember ownerMember = createProjectMember(
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
                        .findByProject_IdAndRole(
                                projectId,
                                ProjectMemberRole.OWNER
                        )
        ).thenReturn(Optional.of(ownerMember));

        ProjectResponse response = projectService.archiveProject(projectId);

        assertThat(response.status())
                .isEqualTo(ProjectStatus.ARCHIVED);

        assertThat(project.getStatus())
                .isEqualTo(ProjectStatus.ARCHIVED);

        assertThat(project.getUpdatedAt())
                .isNotNull();
    }

    @Test
    void restoreProject_whenCurrentUserIsOwner_shouldRestoreProject() {

        UUID projectId = UUID.randomUUID();

        authenticateAs("keycloak-owner");

        User owner = createUser(
                UUID.randomUUID(),
                "keycloak-owner",
                UserStatus.ACTIVE
        );

        Project project = createProject(
                projectId,
                ProjectStatus.ARCHIVED
        );

        ProjectMember ownerMember = createProjectMember(
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
                        .findByProject_IdAndRole(
                                projectId,
                                ProjectMemberRole.OWNER
                        )
        ).thenReturn(Optional.of(ownerMember));

        ProjectResponse response =
                projectService.restoreProject(projectId);

        assertThat(response.status())
                .isEqualTo(ProjectStatus.ACTIVE);

        assertThat(project.getStatus())
                .isEqualTo(ProjectStatus.ACTIVE);
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
