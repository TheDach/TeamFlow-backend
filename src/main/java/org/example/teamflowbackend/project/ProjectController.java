package org.example.teamflowbackend.project;

import jakarta.validation.Valid;
import org.example.teamflowbackend.project.dto.ProjectCreateRequest;
import org.example.teamflowbackend.project.dto.ProjectResponse;
import org.example.teamflowbackend.project.dto.ProjectUpdateRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final ProjectService projectService;
    private final Logger log = LoggerFactory.getLogger(ProjectController.class);

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    public ProjectResponse createProject(
            @Valid @RequestBody ProjectCreateRequest projectCreateRequest
    ) {
        return projectService.createProject(projectCreateRequest);
    }

    @GetMapping
    public Page<ProjectResponse> getMyProjects(
            @PageableDefault(
                    size = 20,
                    sort = "project.createdAt",
                    direction = Sort.Direction.DESC
            )
            Pageable pageable
    ) {
        if (pageable.getPageNumber() < 0 || pageable.getPageSize() < 1 || pageable.getPageSize() > 100)
            throw new IllegalArgumentException("page must be >= 0 and size must be between 1 and 100");
        return projectService.getCurrentUserProjects(pageable);
    }

    @GetMapping("/{id}")
    public ProjectResponse getProjectById(
            @PathVariable UUID id
    ) {
        return projectService.getProjectById(id);
    }

    @PatchMapping("/{id}")
    public ProjectResponse updateProject(
            @PathVariable UUID id,
            @Valid @RequestBody ProjectUpdateRequest request
    ) {
        return projectService.updateProject(id, request);
    }

    @PatchMapping("/{id}/archive")
    public ProjectResponse archiveProject(
            @PathVariable UUID id
    ) {
        return projectService.archiveProject(id);
    }

    @PatchMapping("/{id}/restore")
    public ProjectResponse restoreProject(
            @PathVariable UUID id
    ) {
        return projectService.restoreProject(id);
    }
}
