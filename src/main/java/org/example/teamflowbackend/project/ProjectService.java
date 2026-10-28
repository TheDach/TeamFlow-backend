package org.example.teamflowbackend.project;

import org.example.teamflowbackend.common.exception.BusinessConflictException;
import org.example.teamflowbackend.common.exception.ProjectAccessDeniedException;
import org.example.teamflowbackend.common.exception.ProjectNotFoundException;
import org.example.teamflowbackend.project.dto.ProjectCreateRequest;
import org.example.teamflowbackend.project.dto.ProjectResponse;
import org.example.teamflowbackend.project.dto.ProjectUpdateRequest;
import org.example.teamflowbackend.security.CurrentUser;
import org.example.teamflowbackend.user.User;
import org.example.teamflowbackend.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class ProjectService {

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;

    private final Logger log = LoggerFactory.getLogger(ProjectService.class);


    public ProjectService(UserRepository userRepository,
                          ProjectRepository projectRepository,
                          ProjectMemberRepository projectMemberRepository) {
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
        this.projectMemberRepository = projectMemberRepository;
    }

    @Transactional
    public ProjectResponse createProject(ProjectCreateRequest request) {
        String keycloakId = CurrentUser.keycloakId();
        User currentUser = userRepository
                .findByKeycloakId(keycloakId)
                .orElseThrow(() -> new RuntimeException("Current user not found"));

        Instant now = Instant.now();

        Project project = new Project();
        project.setName(request.name().trim());
        project.setDescription(request.description());
        project.setOwner(currentUser);
        project.setStatus(ProjectStatus.ACTIVE);
        project.setCreatedAt(now);
        project.setUpdatedAt(now);

        Project savedProject = projectRepository.save(project);

        ProjectMember ownerMember = new ProjectMember();
        ownerMember.setProject(savedProject);
        ownerMember.setUser(currentUser);
        ownerMember.setRole(ProjectMemberRole.OWNER);
        ownerMember.setJoinedAt(now);

        projectMemberRepository.save(ownerMember);

        return mapToResponseProject(savedProject);
    }

    @Transactional(readOnly = true)
    public Page<ProjectResponse> getCurrentUserProjects(Pageable pageable) {
        String keycloakId = CurrentUser.keycloakId();

        return projectMemberRepository
                .findAllByUser_KeycloakId(keycloakId, pageable)
                .map(ProjectMember::getProject)
                .map(this::mapToResponseProject);
    }

    @Transactional(readOnly = true)
    public ProjectResponse getProjectById(UUID projectId) {
        ProjectMember projectMember = getCurrentProjectMember(projectId);

        return mapToResponseProject(projectMember.getProject());
    }

    @Transactional
    public ProjectResponse updateProject(UUID projectId, ProjectUpdateRequest request) {
        ProjectMember projectMember = getCurrentProjectMember(projectId);

        checkProjectActive(projectMember.getProject());
        checkOwner(projectMember);

        Project project = projectMember.getProject();
        if (request.name() != null) {
            project.setName(request.name());
        }
        if (request.description() != null) {
            project.setDescription(request.description());
        }

        project.setUpdatedAt(Instant.now());
        // dirty checking - Hibernate увидит изменения и сам сделает UPDATE
        return mapToResponseProject(project);
    }

    @Transactional
    public ProjectResponse archiveProject(UUID projectId) {

        return mapToResponseProject(
                changeStatusProject(
                        ProjectStatus.ARCHIVED,
                        projectId
                )
        );
    }

    @Transactional
    public ProjectResponse restoreProject(UUID projectId) {
        return mapToResponseProject(
                changeStatusProject(
                        ProjectStatus.ACTIVE,
                        projectId
                )
        );
    }


    private ProjectResponse mapToResponseProject(Project project) {
        ProjectMember ownerMember = projectMemberRepository
                .findByProject_IdAndRole(
                        project.getId(),
                        ProjectMemberRole.OWNER
                )
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Project owner membership is missing"
                        )
                );
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getStatus(),
                ownerMember.getUser().getId(),
                project.getCreatedAt()
        );
    }

    private ProjectMember getCurrentProjectMember(UUID projectId) {
        String keycloakId = CurrentUser.keycloakId();

        return projectMemberRepository
                .findByProject_IdAndUser_KeycloakId(projectId, keycloakId)
                .orElseThrow(() ->
                        new ProjectNotFoundException("Project not found or access denied")
                );
    }

    private void checkOwner(ProjectMember projectMember) {
        if (projectMember.getRole() != ProjectMemberRole.OWNER) {
            throw new ProjectAccessDeniedException("Only project owner can perform this action");
        }
    }

    private void checkProjectActive(Project project) {
        if (project.getStatus() == ProjectStatus.ARCHIVED) {
            throw new BusinessConflictException("Archived project is read-only");
        }
    }

    private Project changeStatusProject(ProjectStatus status, UUID projectId) {
        ProjectMember projectMember = getCurrentProjectMember(projectId);
        checkOwner(projectMember);

        Project project = projectMember.getProject();
        project.setStatus(status);
        project.setUpdatedAt(Instant.now());
        // dirty checking - Hibernate увидит изменения и сам сделает UPDATE
        return project;
    }
}
