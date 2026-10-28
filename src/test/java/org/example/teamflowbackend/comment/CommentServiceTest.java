package org.example.teamflowbackend.comment;

import org.example.teamflowbackend.comment.dto.CommentCreateRequest;
import org.example.teamflowbackend.comment.dto.CommentDeleteResponse;
import org.example.teamflowbackend.comment.dto.CommentResponse;
import org.example.teamflowbackend.common.exception.BusinessConflictException;
import org.example.teamflowbackend.common.exception.ProjectAccessDeniedException;
import org.example.teamflowbackend.common.exception.TaskNotFoundException;
import org.example.teamflowbackend.project.*;
import org.example.teamflowbackend.task.Task;
import org.example.teamflowbackend.task.TaskPriority;
import org.example.teamflowbackend.task.TaskRepository;
import org.example.teamflowbackend.task.TaskStatus;
import org.example.teamflowbackend.user.User;
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
class CommentServiceTest {

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private ProjectMemberRepository projectMemberRepository;

    @InjectMocks
    private CommentService commentService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test // MEMBER создаёт комментарий
    void createComment_whenUserIsProjectMember_shouldCreateComment() {

        UUID projectId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();

        authenticateAs("keycloak-member");

        User memberUser = createUser(
                userId,
                "keycloak-member"
        );

        Project project = createProject(
                projectId,
                ProjectStatus.ACTIVE
        );

        Task task = createTask(
                taskId,
                project,
                memberUser
        );

        ProjectMember member = createProjectMember(
                project,
                memberUser,
                ProjectMemberRole.MEMBER
        );

        when(taskRepository.findById(taskId))
                .thenReturn(Optional.of(task));

        when(
                projectMemberRepository
                        .existsByProject_IdAndUser_KeycloakId(
                                projectId,
                                "keycloak-member"
                        )
        ).thenReturn(true);

        when(
                projectMemberRepository
                        .findByProject_IdAndUser_KeycloakId(
                                projectId,
                                "keycloak-member"
                        )
        ).thenReturn(Optional.of(member));

        when(commentRepository.save(any(Comment.class)))
                .thenAnswer(invocation -> {

                    Comment comment =
                            invocation.getArgument(0);

                    comment.setId(commentId);

                    return comment;
                });

        CommentResponse response =
                commentService.createComment(
                        taskId,
                        new CommentCreateRequest(
                                "  Контроллер готов  "
                        )
                );

        assertThat(response.id())
                .isEqualTo(commentId);

        assertThat(response.taskId())
                .isEqualTo(taskId);

        assertThat(response.authorId())
                .isEqualTo(userId);

        assertThat(response.content())
                .isEqualTo("Контроллер готов");

        ArgumentCaptor<Comment> captor =
                ArgumentCaptor.forClass(Comment.class);

        verify(commentRepository)
                .save(captor.capture());

        Comment savedComment =
                captor.getValue();

        assertThat(savedComment.getTask())
                .isSameAs(task);

        assertThat(savedComment.getAuthor())
                .isSameAs(memberUser);

        assertThat(savedComment.getContent())
                .isEqualTo("Контроллер готов");

        assertThat(savedComment.getCreatedAt())
                .isNotNull();

        assertThat(savedComment.getUpdatedAt())
                .isNotNull();
    }

    @Test // Посторонний не может создать комментарий
    void createComment_whenUserIsOutsider_shouldThrowTaskNotFound() {

        UUID projectId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        authenticateAs("keycloak-outsider");

        User creator = createUser(
                UUID.randomUUID(),
                "keycloak-owner"
        );

        Project project = createProject(
                projectId,
                ProjectStatus.ACTIVE
        );

        Task task = createTask(
                taskId,
                project,
                creator
        );

        when(taskRepository.findById(taskId))
                .thenReturn(Optional.of(task));

        when(
                projectMemberRepository
                        .existsByProject_IdAndUser_KeycloakId(
                                projectId,
                                "keycloak-outsider"
                        )
        ).thenReturn(false);

        assertThatThrownBy(() ->
                commentService.createComment(
                        taskId,
                        new CommentCreateRequest("Comment")
                )
        )
                .isInstanceOf(
                        TaskNotFoundException.class
                );

        verify(commentRepository, never())
                .save(any());
    }

    @Test // В архивном проекте нельзя комментировать
    void createComment_whenProjectArchived_shouldThrowConflict() {

        UUID projectId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        authenticateAs("keycloak-member");

        User memberUser = createUser(
                UUID.randomUUID(),
                "keycloak-member"
        );

        Project project = createProject(
                projectId,
                ProjectStatus.ARCHIVED
        );

        Task task = createTask(
                taskId,
                project,
                memberUser
        );

        when(taskRepository.findById(taskId))
                .thenReturn(Optional.of(task));

        when(
                projectMemberRepository
                        .existsByProject_IdAndUser_KeycloakId(
                                projectId,
                                "keycloak-member"
                        )
        ).thenReturn(true);

        assertThatThrownBy(() ->
                commentService.createComment(
                        taskId,
                        new CommentCreateRequest("Comment")
                )
        )
                .isInstanceOf(
                        BusinessConflictException.class
                )
                .hasMessageContaining(
                        "Archived project"
                );

        verify(commentRepository, never())
                .save(any());
    }

    @Test // Автор удаляет свой комментарий
    void deleteComment_whenCurrentUserIsAuthor_shouldDeleteComment() {

        UUID projectId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();

        authenticateAs("keycloak-member");

        User author = createUser(
                authorId,
                "keycloak-member"
        );

        Project project = createProject(
                projectId,
                ProjectStatus.ACTIVE
        );

        Task task = createTask(
                taskId,
                project,
                author
        );

        ProjectMember member = createProjectMember(
                project,
                author,
                ProjectMemberRole.MEMBER
        );

        Comment comment = createComment(
                commentId,
                task,
                author,
                "My comment"
        );

        when(commentRepository.findById(commentId))
                .thenReturn(Optional.of(comment));

        when(
                projectMemberRepository
                        .findByProject_IdAndUser_KeycloakId(
                                projectId,
                                "keycloak-member"
                        )
        ).thenReturn(Optional.of(member));

        CommentDeleteResponse response =
                commentService.deleteComment(commentId);

        verify(commentRepository)
                .delete(comment);

        assertThat(response.id())
                .isEqualTo(commentId);

        assertThat(response.author())
                .isEqualTo(authorId);

        assertThat(response.content())
                .isEqualTo("My comment");
    }

    @Test // OWNER может удалить чужой комментарий
    void deleteComment_whenCurrentUserIsOwner_shouldDeleteOtherUsersComment() {

        UUID projectId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();

        authenticateAs("keycloak-owner");

        User owner = createUser(
                UUID.randomUUID(),
                "keycloak-owner"
        );

        User author = createUser(
                UUID.randomUUID(),
                "keycloak-member"
        );

        Project project = createProject(
                projectId,
                ProjectStatus.ACTIVE
        );

        Task task = createTask(
                taskId,
                project,
                author
        );

        ProjectMember ownerMember =
                createProjectMember(
                        project,
                        owner,
                        ProjectMemberRole.OWNER
                );

        Comment comment = createComment(
                commentId,
                task,
                author,
                "Member comment"
        );

        when(commentRepository.findById(commentId))
                .thenReturn(Optional.of(comment));

        when(
                projectMemberRepository
                        .findByProject_IdAndUser_KeycloakId(
                                projectId,
                                "keycloak-owner"
                        )
        ).thenReturn(Optional.of(ownerMember));

        commentService.deleteComment(commentId);

        verify(commentRepository)
                .delete(comment);
    }

    @Test // MEMBER не может удалить чужой комментарий
    void deleteComment_whenMemberDeletesOtherUsersComment_shouldThrowAccessDenied() {

        UUID projectId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();

        authenticateAs("keycloak-member");

        User currentUser = createUser(
                UUID.randomUUID(),
                "keycloak-member"
        );

        User author = createUser(
                UUID.randomUUID(),
                "keycloak-author"
        );

        Project project = createProject(
                projectId,
                ProjectStatus.ACTIVE
        );

        Task task = createTask(
                taskId,
                project,
                author
        );

        ProjectMember currentMember =
                createProjectMember(
                        project,
                        currentUser,
                        ProjectMemberRole.MEMBER
                );

        Comment comment = createComment(
                commentId,
                task,
                author,
                "Other user's comment"
        );

        when(commentRepository.findById(commentId))
                .thenReturn(Optional.of(comment));

        when(
                projectMemberRepository
                        .findByProject_IdAndUser_KeycloakId(
                                projectId,
                                "keycloak-member"
                        )
        ).thenReturn(Optional.of(currentMember));

        assertThatThrownBy(() ->
                commentService.deleteComment(commentId)
        )
                .isInstanceOf(
                        ProjectAccessDeniedException.class
                )
                .hasMessageContaining(
                        "author"
                );

        verify(commentRepository, never())
                .delete(any());
    }

    @Test // Архивный проект запрещает даже удаление комментария
    void deleteComment_whenProjectArchived_shouldThrowConflict() {

        UUID projectId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();

        authenticateAs("keycloak-owner");

        User owner = createUser(
                UUID.randomUUID(),
                "keycloak-owner"
        );

        Project project = createProject(
                projectId,
                ProjectStatus.ARCHIVED
        );

        Task task = createTask(
                taskId,
                project,
                owner
        );

        ProjectMember ownerMember =
                createProjectMember(
                        project,
                        owner,
                        ProjectMemberRole.OWNER
                );

        Comment comment = createComment(
                commentId,
                task,
                owner,
                "Comment"
        );

        when(commentRepository.findById(commentId))
                .thenReturn(Optional.of(comment));

        when(
                projectMemberRepository
                        .findByProject_IdAndUser_KeycloakId(
                                projectId,
                                "keycloak-owner"
                        )
        ).thenReturn(Optional.of(ownerMember));

        assertThatThrownBy(() ->
                commentService.deleteComment(commentId)
        )
                .isInstanceOf(
                        BusinessConflictException.class
                );

        verify(commentRepository, never())
                .delete(any());
    }



    private void authenticateAs(String keycloakId) {

        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(keycloakId)
                .claim(
                        "email",
                        keycloakId + "@teamflow.local"
                )
                .issuedAt(Instant.now())
                .expiresAt(
                        Instant.now().plusSeconds(3600)
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
                SecurityContextHolder.createEmptyContext();

        context.setAuthentication(authentication);

        SecurityContextHolder.setContext(context);
    }
    private User createUser(
            UUID id,
            String keycloakId
    ) {

        User user = new User();

        user.setId(id);
        user.setKeycloakId(keycloakId);
        user.setEmail(
                keycloakId + "@teamflow.local"
        );
        user.setDisplayName(keycloakId);
        user.setStatus(UserStatus.ACTIVE);
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());

        return user;
    }
    private Project createProject(
            UUID id,
            ProjectStatus status
    ) {

        Project project = new Project();

        project.setId(id);
        project.setName("Test project");
        project.setDescription("Description");
        project.setStatus(status);
        project.setCreatedAt(Instant.now());
        project.setUpdatedAt(Instant.now());

        return project;
    }
    private ProjectMember createProjectMember(
            Project project,
            User user,
            ProjectMemberRole role
    ) {

        ProjectMember member = new ProjectMember();

        member.setId(UUID.randomUUID());
        member.setProject(project);
        member.setUser(user);
        member.setRole(role);
        member.setJoinedAt(Instant.now());

        return member;
    }
    private Task createTask(
            UUID id,
            Project project,
            User creator
    ) {

        Task task = new Task();

        task.setId(id);
        task.setProject(project);
        task.setTitle("Test task");
        task.setDescription("Description");
        task.setStatus(TaskStatus.TODO);
        task.setPriority(TaskPriority.MEDIUM);
        task.setCreatedBy(creator);
        task.setCreatedAt(Instant.now());
        task.setUpdatedAt(Instant.now());

        return task;
    }
    private Comment createComment(
            UUID id,
            Task task,
            User author,
            String content
    ) {

        Comment comment = new Comment();

        comment.setId(id);
        comment.setTask(task);
        comment.setAuthor(author);
        comment.setContent(content);
        comment.setCreatedAt(Instant.now());
        comment.setUpdatedAt(Instant.now());

        return comment;
    }
}