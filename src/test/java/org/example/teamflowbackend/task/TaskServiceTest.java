package org.example.teamflowbackend.task;

import org.example.teamflowbackend.common.exception.BusinessConflictException;
import org.example.teamflowbackend.common.exception.ProjectAccessDeniedException;
import org.example.teamflowbackend.common.exception.TaskNotFoundException;
import org.example.teamflowbackend.project.*;
import org.example.teamflowbackend.task.dto.TaskCreateRequest;
import org.example.teamflowbackend.task.dto.TaskDeleteResponse;
import org.example.teamflowbackend.task.dto.TaskResponse;
import org.example.teamflowbackend.task.dto.TaskStatusUpdateRequest;
import org.example.teamflowbackend.user.User;
import org.example.teamflowbackend.user.UserRepository;
import org.example.teamflowbackend.user.UserStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProjectMemberRepository projectMemberRepository;

    @InjectMocks
    private TaskService taskService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String keycloakId) {

        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(keycloakId)
                .claim("email", "test@teamflow.local")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        JwtAuthenticationToken authentication = new JwtAuthenticationToken(
                jwt,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        SecurityContext context = SecurityContextHolder.createEmptyContext();

        context.setAuthentication(authentication);

        SecurityContextHolder.setContext(context);
    }


    @Test // Создание задачи
    void createTask_withoutAssignee_shouldUseDefaults() {
        UUID projectId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        authenticateAs("keycloak-owner");

        User owner = createUser(
                "keycloak-owner",
                UserStatus.ACTIVE
        );

        Project project = createProject(
                projectId,
                ProjectStatus.ACTIVE
        );

        ProjectMember currentMember = createProjectMember(
                project,
                owner,
                ProjectMemberRole.OWNER
        );

        when(projectMemberRepository
                .findByProject_IdAndUser_KeycloakId(
                        projectId,
                        "keycloak-owner"
                )
        ).thenReturn(Optional.of(currentMember));

        when(taskRepository.save(any(Task.class)))
                .thenAnswer(invocation -> {

                    Task task = invocation.getArgument(0);
                    task.setId(taskId);

                    return task;
                });

        TaskCreateRequest request = new TaskCreateRequest(
                "Написать тесты",
                "Unit tests for TaskService",
                null,
                null,
                LocalDate.now().plusDays(3)
        );

        TaskResponse response = taskService.createTask(projectId, request);

        assertThat(response.id())
                .isEqualTo(taskId);

        assertThat(response.status())
                .isEqualTo(TaskStatus.TODO);

        assertThat(response.priority())
                .isEqualTo(TaskPriority.MEDIUM);

        assertThat(response.assigneeId())
                .isNull();

        assertThat(response.createdBy())
                .isEqualTo(owner.getId());


        ArgumentCaptor<Task> taskCaptor = ArgumentCaptor.forClass(Task.class);

        verify(taskRepository)
                .save(taskCaptor.capture());

        Task savedTask = taskCaptor.getValue();

        assertThat(savedTask.getTitle())
                .isEqualTo("Написать тесты");

        assertThat(savedTask.getStatus())
                .isEqualTo(TaskStatus.TODO);

        assertThat(savedTask.getPriority())
                .isEqualTo(TaskPriority.MEDIUM);

        assertThat(savedTask.getAssignee())
                .isNull();

        assertThat(savedTask.getCreatedBy())
                .isEqualTo(owner);
    }

    @Test // Разрешённый переход статуса
    void changeStatus_todoToInProgress_shouldChangeStatus() {

//        Given:
//        Task = TODO
//
//        When:
//        changeStatus(IN_PROGRESS)
//
//        Then:
//        Task = IN_PROGRESS

        UUID taskId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        authenticateAs("keycloak-owner");

        User owner = new User();
        owner.setId(ownerId);
        owner.setKeycloakId("keycloak-owner");

        Project project = new Project();
        project.setId(projectId);
        project.setStatus(ProjectStatus.ACTIVE);

        Task task = new Task();
        task.setId(taskId);
        task.setProject(project);
        task.setTitle("Test task");
        task.setStatus(TaskStatus.TODO);
        task.setPriority(TaskPriority.MEDIUM);
        task.setCreatedBy(owner);
        task.setCreatedAt(Instant.now());
        task.setUpdatedAt(Instant.now());

        when(taskRepository.findById(taskId))
                .thenReturn(Optional.of(task));

        when(
                projectMemberRepository
                        .existsByProject_IdAndUser_KeycloakId(
                                projectId,
                                "keycloak-owner"
                        )
        ).thenReturn(true);

        TaskResponse response =
                taskService.changeStatus(
                        taskId,
                        new TaskStatusUpdateRequest(
                                TaskStatus.IN_PROGRESS
                        )
                );

        assertThat(response.status())
                .isEqualTo(TaskStatus.IN_PROGRESS);

        assertThat(task.getStatus())
                .isEqualTo(TaskStatus.IN_PROGRESS);
    }

    @Test // Запрещённый переход
    void changeStatus_todoToDone_shouldThrowConflict() {

        UUID taskId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        authenticateAs("keycloak-owner");

        User owner = new User();
        owner.setId(UUID.randomUUID());

        Project project = new Project();
        project.setId(projectId);
        project.setStatus(ProjectStatus.ACTIVE);

        Task task = new Task();
        task.setId(taskId);
        task.setProject(project);
        task.setTitle("Test task");
        task.setStatus(TaskStatus.TODO);
        task.setPriority(TaskPriority.MEDIUM);
        task.setCreatedBy(owner);

        when(taskRepository.findById(taskId))
                .thenReturn(Optional.of(task));

        when(
                projectMemberRepository
                        .existsByProject_IdAndUser_KeycloakId(
                                projectId,
                                "keycloak-owner"
                        )
        ).thenReturn(true);

        assertThatThrownBy(() ->
                taskService.changeStatus(
                        taskId,
                        new TaskStatusUpdateRequest(
                                TaskStatus.DONE
                        )
                )
        )
                .isInstanceOf(BusinessConflictException.class)
                .hasMessageContaining(
                        "Status transition"
                );

        assertThat(task.getStatus())
                .isEqualTo(TaskStatus.TODO);
    }

    @Test // Чужой пользователь не видит задачу
    void getTask_whenUserIsNotProjectMember_shouldReturnNotFound() {

        UUID taskId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        authenticateAs("keycloak-outsider");

        Project project = new Project();
        project.setId(projectId);

        Task task = new Task();
        task.setId(taskId);
        task.setProject(project);

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
                taskService.getTask(taskId)
        )
                .isInstanceOf(TaskNotFoundException.class);
    }


    @ParameterizedTest // Все разрешённые переходы статусов
    @MethodSource("allowedStatusTransitions")
    void changeStatus_allowedTransition_shouldChangeStatus(
            TaskStatus from,
            TaskStatus to
    ) {
        UUID taskId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        authenticateAs("keycloak-owner");

        User creator = createUser(
                "keycloak-owner",
                UserStatus.ACTIVE
        );

        Project project = createProject(
                projectId,
                ProjectStatus.ACTIVE
        );

        Task task = createTask(
                taskId,
                project,
                creator,
                from
        );

        when(taskRepository.findById(taskId))
                .thenReturn(Optional.of(task));

        when(
                projectMemberRepository
                        .existsByProject_IdAndUser_KeycloakId(
                                projectId,
                                "keycloak-owner"
                        )
        ).thenReturn(true);

        TaskResponse response =
                taskService.changeStatus(
                        taskId,
                        new TaskStatusUpdateRequest(to)
                );

        assertThat(response.status())
                .isEqualTo(to);

        assertThat(task.getStatus())
                .isEqualTo(to);
    }
    static Stream<Arguments> allowedStatusTransitions() {
        return Stream.of(
                Arguments.of(
                        TaskStatus.TODO,
                        TaskStatus.IN_PROGRESS
                ),

                Arguments.of(
                        TaskStatus.IN_PROGRESS,
                        TaskStatus.TODO
                ),

                Arguments.of(
                        TaskStatus.IN_PROGRESS,
                        TaskStatus.IN_REVIEW
                ),

                Arguments.of(
                        TaskStatus.IN_REVIEW,
                        TaskStatus.IN_PROGRESS
                ),

                Arguments.of(
                        TaskStatus.IN_REVIEW,
                        TaskStatus.DONE
                ),

                Arguments.of(
                        TaskStatus.DONE,
                        TaskStatus.TODO
                )
        );
    }

    @ParameterizedTest // Все запрещённые переходы
    @MethodSource("forbiddenStatusTransitions")
    void changeStatus_forbiddenTransition_shouldThrowConflict(
            TaskStatus from,
            TaskStatus to
    ) {
        UUID taskId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        authenticateAs("keycloak-owner");

        User creator = createUser(
                "keycloak-owner",
                UserStatus.ACTIVE
        );

        Project project = createProject(
                projectId,
                ProjectStatus.ACTIVE
        );

        Task task = createTask(
                taskId,
                project,
                creator,
                from
        );

        when(taskRepository.findById(taskId))
                .thenReturn(Optional.of(task));

        when(
                projectMemberRepository
                        .existsByProject_IdAndUser_KeycloakId(
                                projectId,
                                "keycloak-owner"
                        )
        ).thenReturn(true);

        assertThatThrownBy(() ->
                taskService.changeStatus(
                        taskId,
                        new TaskStatusUpdateRequest(to)
                )
        )
                .isInstanceOf(
                        BusinessConflictException.class
                );

        assertThat(task.getStatus())
                .isEqualTo(from);
    }
    static Stream<Arguments> forbiddenStatusTransitions() {
        return Stream.of(
                Arguments.of(
                        TaskStatus.TODO,
                        TaskStatus.IN_REVIEW
                ),

                Arguments.of(
                        TaskStatus.TODO,
                        TaskStatus.DONE
                ),

                Arguments.of(
                        TaskStatus.IN_PROGRESS,
                        TaskStatus.DONE
                ),

                Arguments.of(
                        TaskStatus.IN_REVIEW,
                        TaskStatus.TODO
                ),

                Arguments.of(
                        TaskStatus.DONE,
                        TaskStatus.IN_PROGRESS
                ),

                Arguments.of(
                        TaskStatus.DONE,
                        TaskStatus.IN_REVIEW
                )
        );
    }

    @Test // Идемпотентный переход
    void changeStatus_sameStatus_shouldBeIdempotent() {

        UUID taskId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        authenticateAs("keycloak-owner");

        User creator = createUser(
                "keycloak-owner",
                UserStatus.ACTIVE
        );

        Project project = createProject(
                projectId,
                ProjectStatus.ACTIVE
        );

        Instant oldUpdatedAt =
                Instant.parse("2026-10-01T10:00:00Z");

        Task task = createTask(
                taskId,
                project,
                creator,
                TaskStatus.TODO
        );

        task.setUpdatedAt(oldUpdatedAt);

        when(taskRepository.findById(taskId))
                .thenReturn(Optional.of(task));

        when(
                projectMemberRepository
                        .existsByProject_IdAndUser_KeycloakId(
                                projectId,
                                "keycloak-owner"
                        )
        ).thenReturn(true);

        TaskResponse response =
                taskService.changeStatus(
                        taskId,
                        new TaskStatusUpdateRequest(
                                TaskStatus.TODO
                        )
                );

        assertThat(response.status())
                .isEqualTo(TaskStatus.TODO);

        assertThat(task.getUpdatedAt())
                .isEqualTo(oldUpdatedAt);
    }

    @Test // Назначение активного участника проекта
    void createTask_withActiveProjectMemberAssignee_shouldAssignUser() {

        UUID projectId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        UUID assigneeId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        authenticateAs("keycloak-owner");

        User creator = createUser(
                creatorId,
                "keycloak-owner",
                UserStatus.ACTIVE
        );

        User assignee = createUser(
                assigneeId,
                "keycloak-member",
                UserStatus.ACTIVE
        );

        Project project = createProject(
                projectId,
                ProjectStatus.ACTIVE
        );

        ProjectMember currentMember =
                createProjectMember(
                        project,
                        creator,
                        ProjectMemberRole.OWNER
                );

        when(
                projectMemberRepository
                        .findByProject_IdAndUser_KeycloakId(
                                projectId,
                                "keycloak-owner"
                        )
        ).thenReturn(Optional.of(currentMember));

        when(userRepository.findById(assigneeId))
                .thenReturn(Optional.of(assignee));

        when(
                projectMemberRepository
                        .existsByProject_IdAndUser_Id(
                                projectId,
                                assigneeId
                        )
        ).thenReturn(true);

        when(taskRepository.save(any(Task.class)))
                .thenAnswer(invocation -> {
                    Task saved =
                            invocation.getArgument(0);

                    saved.setId(taskId);

                    return saved;
                });

        TaskCreateRequest request =
                new TaskCreateRequest(
                        "Task",
                        "Description",
                        TaskPriority.HIGH,
                        assigneeId,
                        null
                );

        TaskResponse response =
                taskService.createTask(
                        projectId,
                        request
                );

        assertThat(response.assigneeId())
                .isEqualTo(assigneeId);

        verify(userRepository)
                .findById(assigneeId);

        verify(projectMemberRepository)
                .existsByProject_IdAndUser_Id(
                        projectId,
                        assigneeId
                );
    }

    @Test // Пользователь существует, но не входит в проект
    void createTask_whenAssigneeIsNotProjectMember_shouldThrowConflict() {

        UUID projectId = UUID.randomUUID();
        UUID assigneeId = UUID.randomUUID();

        authenticateAs("keycloak-owner");

        User creator = createUser(
                "keycloak-owner",
                UserStatus.ACTIVE
        );

        User assignee = createUser(
                assigneeId,
                "keycloak-outsider",
                UserStatus.ACTIVE
        );

        Project project = createProject(
                projectId,
                ProjectStatus.ACTIVE
        );

        ProjectMember currentMember =
                createProjectMember(
                        project,
                        creator,
                        ProjectMemberRole.OWNER
                );

        when(
                projectMemberRepository
                        .findByProject_IdAndUser_KeycloakId(
                                projectId,
                                "keycloak-owner"
                        )
        ).thenReturn(Optional.of(currentMember));

        when(userRepository.findById(assigneeId))
                .thenReturn(Optional.of(assignee));

        when(
                projectMemberRepository
                        .existsByProject_IdAndUser_Id(
                                projectId,
                                assigneeId
                        )
        ).thenReturn(false);

        TaskCreateRequest request =
                new TaskCreateRequest(
                        "Task",
                        null,
                        null,
                        assigneeId,
                        null
                );

        assertThatThrownBy(() ->
                taskService.createTask(
                        projectId,
                        request
                )
        )
                .isInstanceOf(
                        BusinessConflictException.class
                )
                .hasMessageContaining(
                        "active project member"
                );

        verify(taskRepository, never())
                .save(any());
    }

    @Test //BLOCKED пользователь тоже не может быть assignee
    void createTask_whenAssigneeIsBlocked_shouldThrowConflict() {

        UUID projectId = UUID.randomUUID();
        UUID assigneeId = UUID.randomUUID();

        authenticateAs("keycloak-owner");

        User creator = createUser(
                "keycloak-owner",
                UserStatus.ACTIVE
        );

        User blockedUser = createUser(
                assigneeId,
                "keycloak-blocked",
                UserStatus.BLOCKED
        );

        Project project = createProject(
                projectId,
                ProjectStatus.ACTIVE
        );

        ProjectMember currentMember =
                createProjectMember(
                        project,
                        creator,
                        ProjectMemberRole.OWNER
                );

        when(
                projectMemberRepository
                        .findByProject_IdAndUser_KeycloakId(
                                projectId,
                                "keycloak-owner"
                        )
        ).thenReturn(Optional.of(currentMember));

        when(userRepository.findById(assigneeId))
                .thenReturn(Optional.of(blockedUser));

        when(
                projectMemberRepository
                        .existsByProject_IdAndUser_Id(
                                projectId,
                                assigneeId
                        )
        ).thenReturn(true);

        TaskCreateRequest request =
                new TaskCreateRequest(
                        "Task",
                        null,
                        null,
                        assigneeId,
                        null
                );

        assertThatThrownBy(() ->
                taskService.createTask(
                        projectId,
                        request
                )
        )
                .isInstanceOf(
                        BusinessConflictException.class
                );

        verify(taskRepository, never())
                .save(any());
    }

    @Test //Архивный проект нельзя изменять
    void changeStatus_whenProjectArchived_shouldThrowConflict() {

        UUID projectId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        authenticateAs("keycloak-owner");

        User creator = createUser(
                "keycloak-owner",
                UserStatus.ACTIVE
        );

        Project project = createProject(
                projectId,
                ProjectStatus.ARCHIVED
        );

        Task task = createTask(
                taskId,
                project,
                creator,
                TaskStatus.TODO
        );

        when(taskRepository.findById(taskId))
                .thenReturn(Optional.of(task));

        when(
                projectMemberRepository
                        .existsByProject_IdAndUser_KeycloakId(
                                projectId,
                                "keycloak-owner"
                        )
        ).thenReturn(true);

        assertThatThrownBy(() ->
                taskService.changeStatus(
                        taskId,
                        new TaskStatusUpdateRequest(
                                TaskStatus.IN_PROGRESS
                        )
                )
        )
                .isInstanceOf(
                        BusinessConflictException.class
                )
                .hasMessageContaining(
                        "Archived project"
                );

        assertThat(task.getStatus())
                .isEqualTo(TaskStatus.TODO);
    }

    @Test //OWNER может удалить задачу
    void deleteTask_whenCurrentUserIsOwner_shouldDeleteTask() {

        UUID projectId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        authenticateAs("keycloak-owner");

        User owner = createUser(
                "keycloak-owner",
                UserStatus.ACTIVE
        );

        Project project = createProject(
                projectId,
                ProjectStatus.ACTIVE
        );

        Task task = createTask(
                taskId,
                project,
                owner,
                TaskStatus.TODO
        );

        ProjectMember ownerMember =
                createProjectMember(
                        project,
                        owner,
                        ProjectMemberRole.OWNER
                );

        when(taskRepository.findById(taskId))
                .thenReturn(Optional.of(task));

        when(
                projectMemberRepository
                        .existsByProject_IdAndUser_KeycloakId(
                                projectId,
                                "keycloak-owner"
                        )
        ).thenReturn(true);

        when(
                projectMemberRepository
                        .findByProject_IdAndUser_KeycloakId(
                                projectId,
                                "keycloak-owner"
                        )
        ).thenReturn(Optional.of(ownerMember));

        TaskDeleteResponse response =
                taskService.deleteTask(taskId);

        verify(taskRepository)
                .delete(task);

        assertThat(response.id())
                .isEqualTo(taskId);
    }

    @Test //MEMBER удалить задачу не может
    void deleteTask_whenCurrentUserIsMember_shouldThrowAccessDenied() {

        UUID projectId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        authenticateAs("keycloak-member");

        User member = createUser(
                "keycloak-member",
                UserStatus.ACTIVE
        );

        Project project = createProject(
                projectId,
                ProjectStatus.ACTIVE
        );

        Task task = createTask(
                taskId,
                project,
                member,
                TaskStatus.TODO
        );

        ProjectMember projectMember =
                createProjectMember(
                        project,
                        member,
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
        ).thenReturn(Optional.of(projectMember));

        assertThatThrownBy(() ->
                taskService.deleteTask(taskId)
        )
                .isInstanceOf(
                        ProjectAccessDeniedException.class
                );

        verify(taskRepository, never())
                .delete((Task) any());
    }

    @Test // Конфликт фильтров
    void getListTasks_withAssigneeAndUnassigned_shouldThrowIllegalArgumentException() {

        UUID projectId = UUID.randomUUID();
        UUID assigneeId = UUID.randomUUID();

        authenticateAs("keycloak-member");

        User user = createUser(
                "keycloak-member",
                UserStatus.ACTIVE
        );

        Project project = createProject(
                projectId,
                ProjectStatus.ACTIVE
        );

        ProjectMember member =
                createProjectMember(
                        project,
                        user,
                        ProjectMemberRole.MEMBER
                );

        when(
                projectMemberRepository
                        .findByProject_IdAndUser_KeycloakId(
                                projectId,
                                "keycloak-member"
                        )
        ).thenReturn(Optional.of(member));

        Pageable pageable =
                PageRequest.of(0, 20);

        assertThatThrownBy(() ->
                taskService.getListTasks(
                        projectId,
                        pageable,
                        null,
                        null,
                        assigneeId,
                        true,
                        null
                )
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                )
                .hasMessageContaining(
                        "assigneeId"
                );

        verifyNoInteractions(taskRepository);
    }

    private User createUser(
            String keycloakId,
            UserStatus status
    ) {
        return createUser(
                UUID.randomUUID(),
                keycloakId,
                status
        );
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
        Project project = new Project();

        project.setId(id);
        project.setName("Test project");
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
        ProjectMember member =
                new ProjectMember();

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
            User creator,
            TaskStatus status
    ) {
        Instant now = Instant.now();

        Task task = new Task();

        task.setId(id);
        task.setProject(project);
        task.setTitle("Test task");
        task.setDescription("Description");
        task.setStatus(status);
        task.setPriority(TaskPriority.MEDIUM);
        task.setCreatedBy(creator);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);

        return task;
    }
}
