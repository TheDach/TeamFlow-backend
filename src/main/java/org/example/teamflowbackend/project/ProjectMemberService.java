package org.example.teamflowbackend.project;

import org.example.teamflowbackend.common.exception.*;
import org.example.teamflowbackend.project.dto.ProjectMemberAddRequest;
import org.example.teamflowbackend.project.dto.ProjectMemberDeleteResponse;
import org.example.teamflowbackend.project.dto.ProjectMemberResponse;
import org.example.teamflowbackend.security.CurrentUser;
import org.example.teamflowbackend.user.User;
import org.example.teamflowbackend.user.UserRepository;
import org.example.teamflowbackend.user.UserStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class ProjectMemberService {


    private final ProjectMemberRepository projectMemberRepository;
    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;

    private final Logger log = LoggerFactory.getLogger(ProjectMemberService.class);


    public ProjectMemberService(
            ProjectMemberRepository projectMemberRepository,
            UserRepository userRepository,
            ProjectRepository projectRepository
    ) {
        this.projectMemberRepository = projectMemberRepository;
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
    }

    @Transactional(readOnly = true)
    public Page<ProjectMemberResponse> getProjectMembers(
            UUID projectId,
            Pageable pageable
    ) {
        String keycloakId = CurrentUser.keycloakId();
        boolean isProjectMember = projectMemberRepository.existsByProject_IdAndUser_KeycloakId(projectId, keycloakId);

        if (!isProjectMember) {
            throw new ProjectNotFoundException("Project not found or access denied");
        }

        return projectMemberRepository
                .findAllByProject_Id(projectId, pageable)
                .map(this::mapToProjectMemberResponse);
    }

    @Transactional
    public ProjectMemberResponse addMemberIntoProject(
            UUID projectId,
            ProjectMemberAddRequest projectMemberAddRequest
    ) {
        ProjectMember projectMember = getCurrentProjectMember(projectId);
        checkOwner(projectMember);
        checkProjectActive(projectMember.getProject());

        Project project = projectRepository
                .findById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));

        User addUser = existsUserIntoProject(projectMemberAddRequest.userId(), projectId);

        ProjectMember addProjectMember = new ProjectMember();
        addProjectMember.setUser(addUser);
        addProjectMember.setProject(project);
        addProjectMember.setJoinedAt(Instant.now());
        addProjectMember.setRole(ProjectMemberRole.MEMBER);

        projectMemberRepository.save(addProjectMember);

        return mapToProjectMemberResponse(addProjectMember);
    }

    @Transactional
    public ProjectMemberDeleteResponse deleteMemberProject(
            UUID projectId,
            UUID userId
    ) {
        ProjectMember projectMember = getCurrentProjectMember(projectId);
        checkOwner(projectMember);
        checkProjectActive(projectMember.getProject());

        ProjectMember memberToDelete = projectMemberRepository
                .findByProject_IdAndUser_Id(projectId, userId)
                .orElseThrow(() -> new CurrentUserNotFoundException("Project member not found userId: " + userId));

        if (memberToDelete.getRole() == ProjectMemberRole.OWNER) {
            throw new OwnerException("You can't delete the project owner id: " + userId);
        }

        //TODO (При удалении участника назначенные ему задачи автоматически становятся неназначенными (assignee_id = NULL).)
        projectMemberRepository.delete(memberToDelete);

        return new ProjectMemberDeleteResponse(
                userId,
                memberToDelete.getUser().getDisplayName()
        );
    }



    private User existsUserIntoProject(UUID userId, UUID projectId) {
        User addUser = userRepository
                .findById(userId)
                .orElseThrow(() ->
                        new CurrentUserNotFoundException(
                                "Specified user was not found id: " + userId)
                );

        boolean isUserExistInProject = projectMemberRepository.existsByProject_IdAndUser_Id(projectId, addUser.getId());
        if (isUserExistInProject) {
            throw new UserExistsProjectException("User already exist in project userId: " + addUser.getId());
        }
        if (addUser.getStatus() != UserStatus.ACTIVE) {
            throw new ProjectAccessDeniedException("Only ACTIVE users can be added to a project");
        }
        return addUser;
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

    private void checkOwner(ProjectMember projectMember) {
        if (projectMember.getRole() != ProjectMemberRole.OWNER) {
            throw new ProjectAccessDeniedException("Only project owner can perform this action");
        }
    }

    private ProjectMemberResponse mapToProjectMemberResponse(ProjectMember projectMember) {
        return new ProjectMemberResponse(
                projectMember.getUser().getId(),
                projectMember.getUser().getDisplayName(),
                projectMember.getRole(),
                projectMember.getJoinedAt()
        );
    }
}
