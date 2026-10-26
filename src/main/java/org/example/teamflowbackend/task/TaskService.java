package org.example.teamflowbackend.task;

import jakarta.persistence.criteria.Predicate;
import org.example.teamflowbackend.common.exception.*;
import org.example.teamflowbackend.project.*;
import org.example.teamflowbackend.security.CurrentUser;
import org.example.teamflowbackend.task.dto.*;
import org.example.teamflowbackend.user.User;
import org.example.teamflowbackend.user.UserRepository;
import org.example.teamflowbackend.user.UserStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final Logger log = LoggerFactory.getLogger(TaskService.class);

    public TaskService(TaskRepository taskRepository,
                       UserRepository userRepository,
                       ProjectMemberRepository projectMemberRepository
    ) {
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
        this.projectMemberRepository = projectMemberRepository;
    }


    @Transactional
    public TaskResponse createTask(
            UUID projectId,
            TaskCreateRequest request
    ) {
        ProjectMember current = getCurrentProjectMember(projectId);
        checkProjectActive(current.getProject());

        User assignee = existsUserIntoProject(projectId, request.assigneeId());

        Instant now = Instant.now();
        Task task = new Task();
        task.setProject(current.getProject());
        task.setTitle(request.title());
        task.setDescription(request.description());
        task.setStatus(TaskStatus.TODO);
        task.setPriority(request.priority() == null ? TaskPriority.MEDIUM : request.priority());
        task.setAssignee(assignee);
        task.setCreatedBy(current.getUser());
        task.setDueDate(request.dueDate());
        task.setCreatedAt(now);
        task.setUpdatedAt(now);

        return mapToTaskResponse(taskRepository.save(task));
    }

    @Transactional(readOnly = true)
    public Page<TaskResponse> getListTasks(UUID projectId,
                                           Pageable pageable,
                                           TaskStatus status,
                                           TaskPriority priority,
                                           UUID assigneeId,
                                           Boolean unassigned,
                                           LocalDate dueBefore
    ) {
        getCurrentProjectMember(projectId);

        if (assigneeId != null && Boolean.TRUE.equals(unassigned)) {
            throw new IllegalArgumentException("assigneeId and unassigned=true cannot be used together");
        }

        Specification<Task> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("project").get("id"), projectId));
            if (status != null) p.add(cb.equal(root.get("status"), status));
            if (priority != null) p.add(cb.equal(root.get("priority"), priority));
            if (assigneeId != null) p.add(cb.equal(root.get("assignee").get("id"), assigneeId));
            if (Boolean.TRUE.equals(unassigned)) p.add(cb.isNull(root.get("assignee")));
            if (dueBefore != null) p.add(cb.lessThanOrEqualTo(root.get("dueDate"), dueBefore));
            return cb.and(p.toArray(Predicate[]::new));
        };

        return taskRepository
                .findAll(spec, pageable)
                .map(this::mapToTaskResponse);
    }

    @Transactional(readOnly = true)
    public TaskResponse getTask(UUID taskId) {
        return mapToTaskResponse(visibleUserTask(taskId));
    }

    @Transactional
    public TaskResponse update(
            UUID taskId,
            TaskUpdateRequest request
    ) {
        Task task = visibleUserTask(taskId);
        checkProjectActive(task.getProject());

        if (request.title() != null)
            task.setTitle(request.title());
        if (request.description() != null)
            task.setDescription(request.description());
        if (request.priority() != null)
            task.setPriority(request.priority());
        if (request.dueDate() != null)
            task.setDueDate(request.dueDate());
        task.setUpdatedAt(Instant.now());

        return mapToTaskResponse(task);
    }

    @Transactional
    public TaskResponse changeStatus(
            UUID taskId,
            TaskStatusUpdateRequest request
    ) {
        Task task = visibleUserTask(taskId);
        checkProjectActive(task.getProject());

        if (!isAllowed(task.getStatus(), request.status())) {
            throw new BusinessConflictException(
                    "Status transition " + task.getStatus() + " -> " + request.status() + " is not allowed"
            );
        }
        if (task.getStatus() != request.status()) {
            task.setStatus(request.status());
            task.setUpdatedAt(Instant.now());
        }
        return mapToTaskResponse(task);
    }

    @Transactional
    public TaskResponse changeAssignee(
            UUID taskId,
            TaskAssigneeUpdateRequest request
    ) {
        Task task = visibleUserTask(taskId);

        checkProjectActive(task.getProject());
        task.setAssignee(existsUserIntoProject(task.getProject().getId(), request.assigneeId()));
        task.setUpdatedAt(Instant.now());

        return mapToTaskResponse(task);
    }

    @Transactional
    public TaskDeleteResponse deleteTask(UUID taskId) {
        Task task = visibleUserTask(taskId);
        checkProjectActive(task.getProject());

        ProjectMember current = getCurrentProjectMember(task.getProject().getId());
        if (current.getRole() != ProjectMemberRole.OWNER)
            throw new ProjectAccessDeniedException("Only project owner can delete tasks");

        taskRepository.delete(task);
        return new TaskDeleteResponse (
                taskId,
                task.getTitle(),
                task.getDescription()
        );
    }

    private User existsUserIntoProject(UUID projectId, UUID userId) {
        User user = userRepository
                .findById(userId)
                .orElseThrow(() ->
                        new CurrentUserNotFoundException(
                                "Specified user was not found id: " + userId)
                );

        boolean isUserExistInProject = projectMemberRepository.existsByProject_IdAndUser_Id(projectId, user.getId());
        if (user.getStatus() != UserStatus.ACTIVE || !isUserExistInProject)
            throw new BusinessConflictException("Assignee must be an active project member");

        return user;
    }

    private boolean isAllowed(TaskStatus from, TaskStatus to) {
        if (from == to) return true;
        return switch (from) {
            case TODO -> to == TaskStatus.IN_PROGRESS;
            case IN_PROGRESS -> to == TaskStatus.TODO || to == TaskStatus.IN_REVIEW;
            case IN_REVIEW -> to == TaskStatus.IN_PROGRESS || to == TaskStatus.DONE;
            case DONE -> to == TaskStatus.TODO;
        };
    }

    private Task visibleUserTask(UUID taskId) {
        Task task = taskRepository
                .findById(taskId)
                .orElseThrow(() ->
                        new TaskNotFoundException(taskId)
                );

        boolean currentMember = projectMemberRepository.existsByProject_IdAndUser_KeycloakId(
                task.getProject().getId(),
                CurrentUser.keycloakId()
        );
        if (!currentMember)
            throw new TaskNotFoundException("Task not found or access denied");
        return task;
    }

    private void checkProjectActive(Project project) {
        if (project.getStatus() == ProjectStatus.ARCHIVED) {
            throw new BusinessConflictException("Archived project is read-only");
        }
    }

    private ProjectMember getCurrentProjectMember(UUID projectId) {
        String keycloakId = CurrentUser.keycloakId();

        return projectMemberRepository
                .findByProject_IdAndUser_KeycloakId(projectId, keycloakId)
                .orElseThrow(() ->
                        new ProjectNotFoundException("Project not found or access denied")
                );
    }

    private TaskResponse mapToTaskResponse(Task task) {
        return new TaskResponse(
                task.getId(),
                task.getProject().getId(),
                task.getTitle(),
                task.getDescription(),
                task.getStatus(),
                task.getPriority(),
                task.getAssignee() == null ? null : task.getAssignee().getId(),
                task.getCreatedBy().getId(),
                task.getDueDate(),
                task.getCreatedAt(),
                task.getUpdatedAt());
    }
}
