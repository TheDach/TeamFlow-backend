package org.example.teamflowbackend.comment;

import org.example.teamflowbackend.comment.dto.CommentCreateRequest;
import org.example.teamflowbackend.comment.dto.CommentDeleteResponse;
import org.example.teamflowbackend.comment.dto.CommentResponse;
import org.example.teamflowbackend.common.exception.*;
import org.example.teamflowbackend.project.*;
import org.example.teamflowbackend.security.CurrentUser;
import org.example.teamflowbackend.task.Task;
import org.example.teamflowbackend.task.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class CommentService {

    private final CommentRepository commentRepository;
    private final TaskRepository taskRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final Logger log = LoggerFactory.getLogger(CommentService.class);

    public CommentService(
            CommentRepository commentRepository,
            TaskRepository taskRepository,
            ProjectMemberRepository projectMemberRepository
    ) {
        this.commentRepository = commentRepository;
        this.taskRepository = taskRepository;
        this.projectMemberRepository = projectMemberRepository;
    }

    @Transactional
    public CommentResponse createComment(
            UUID taskId,
            CommentCreateRequest request
    ) {
        Task task = visibleUserTask(taskId);
        checkProjectActive(task.getProject());
        ProjectMember member = getCurrentProjectMember(task.getProject().getId());

        Instant now = Instant.now();
        Comment comment = new Comment();
        comment.setTask(task);
        comment.setAuthor(member.getUser());
        comment.setContent(request.content().trim());
        comment.setCreatedAt(now);
        comment.setUpdatedAt(now);

        return mapToCommentResponse(commentRepository.save(comment));
    }

    @Transactional(readOnly = true)
    public Page<CommentResponse> getListComment(
            UUID taskId,
            Pageable pageable
    ) {
        visibleUserTask(taskId);
        return commentRepository
                .findAllByTask_Id(taskId, pageable)
                .map(this::mapToCommentResponse);
    }

    @Transactional
    public CommentDeleteResponse deleteComment(UUID commentId) {
        Comment comment = commentRepository
                .findById(commentId)
                .orElseThrow(() ->
                        new CommentNotFoundException(commentId)
                );

        ProjectMember member = getCurrentProjectMember(comment.getTask().getProject().getId());
        checkProjectActive(comment.getTask().getProject());

        boolean author = comment.getAuthor().getId().equals(member.getUser().getId());
        boolean owner = member.getRole() == ProjectMemberRole.OWNER;

        if (!author && !owner)
            throw new ProjectAccessDeniedException("Only comment author or project owner can delete comment");

        commentRepository.delete(comment);
        return new CommentDeleteResponse(
                commentId,
                comment.getAuthor().getId(),
                comment.getContent()
        );
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

    private CommentResponse mapToCommentResponse(Comment comment) {
        return new CommentResponse(
                comment.getId(),
                comment.getTask().getId(),
                comment.getAuthor().getId(),
                comment.getAuthor().getDisplayName(),
                comment.getContent(),
                comment.getCreatedAt(),
                comment.getUpdatedAt());
    }
}
