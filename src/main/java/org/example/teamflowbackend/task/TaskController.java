package org.example.teamflowbackend.task;

import jakarta.validation.Valid;
import org.example.teamflowbackend.task.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class TaskController {

    private static final Set<String> ALLOWED_SORTS = Set.of("createdAt", "updatedAt", "dueDate", "priority", "status");
    private final TaskService taskService;
    private final Logger log = LoggerFactory.getLogger(TaskController.class);

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping("/projects/{projectId}/tasks")
    public TaskResponse create(
            @PathVariable UUID projectId,
            @Valid @RequestBody TaskCreateRequest request
    ) {
        return taskService.createTask(projectId, request);
    }

    @GetMapping("/projects/{projectId}/tasks")
    public Page<TaskResponse> listTasks(
            @PathVariable UUID projectId,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) TaskPriority priority,
            @RequestParam(required = false) UUID assigneeId,
            @RequestParam(required = false) Boolean unassigned,
            @RequestParam(required = false) LocalDate dueBefore,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        if (pageable.getPageNumber() < 0 || pageable.getPageSize() < 1 || pageable.getPageSize() > 100)
            throw new IllegalArgumentException("page must be >= 0 and size must be between 1 and 100");
        pageable
                .getSort()
                .forEach(order ->
                {
                    if (!ALLOWED_SORTS.contains(order.getProperty()))
                        throw new IllegalArgumentException("Unsupported sort: " + order.getProperty());
                });
        return taskService.getListTasks(projectId, pageable, status, priority, assigneeId, unassigned, dueBefore);
    }

    @GetMapping("/tasks/{id}")
    public TaskResponse getTask(
            @PathVariable UUID id
    ) {
        return taskService.getTask(id);
    }

    @PatchMapping("/tasks/{id}")
    public TaskResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody TaskUpdateRequest request
    ) {
        return taskService.update(id, request);
    }

    @PatchMapping("/tasks/{id}/status")
    public TaskResponse status(
            @PathVariable UUID id,
            @Valid @RequestBody TaskStatusUpdateRequest request
    ) {
        return taskService.changeStatus(id, request);
    }

    @PatchMapping("/tasks/{id}/assignee")
    public TaskResponse assignee(
            @PathVariable UUID id,
            @RequestBody TaskAssigneeUpdateRequest request
    ) {
        return taskService.changeAssignee(id, request);
    }

    @DeleteMapping("/tasks/{id}")
    public TaskDeleteResponse delete(
            @PathVariable UUID id
    ) {
        return taskService.deleteTask(id);
    }
}
