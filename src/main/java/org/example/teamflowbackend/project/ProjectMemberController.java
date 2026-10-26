package org.example.teamflowbackend.project;

import jakarta.validation.Valid;
import org.example.teamflowbackend.project.dto.ProjectMemberAddRequest;
import org.example.teamflowbackend.project.dto.ProjectMemberDeleteResponse;
import org.example.teamflowbackend.project.dto.ProjectMemberResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects/{id}/members")
public class ProjectMemberController {

    private final ProjectMemberService projectMemberService;
    private final Logger log = LoggerFactory.getLogger(ProjectMemberController.class);

    public ProjectMemberController(ProjectMemberService projectMemberService) {
        this.projectMemberService = projectMemberService;
    }

    @GetMapping
    public Page<ProjectMemberResponse> getProjectMembers(
            @PageableDefault(
                    size = 20,
                    sort = "user.displayName",
                    direction = Sort.Direction.DESC
            )
            Pageable pageable,
            @PathVariable UUID id
    ) {
        if (pageable.getPageNumber() < 0 || pageable.getPageSize() < 1 || pageable.getPageSize() > 100)
            throw new IllegalArgumentException("page must be >= 0 and size must be between 1 and 100");
        return projectMemberService.getProjectMembers(id, pageable);
    }

    @PostMapping
    public ProjectMemberResponse addMember(
            @PathVariable UUID id,
            @Valid @RequestBody ProjectMemberAddRequest request
    ) {
        return projectMemberService.addMemberIntoProject(id, request);
    }

    @DeleteMapping("/{userId}")
    public ProjectMemberDeleteResponse deleteMember (
            @PathVariable UUID id,
            @PathVariable UUID userId
    ) {
        return projectMemberService.deleteMemberProject(id, userId);
    }
}
