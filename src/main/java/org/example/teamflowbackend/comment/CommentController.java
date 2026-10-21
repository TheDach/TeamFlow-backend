package org.example.teamflowbackend.comment;

import jakarta.validation.Valid;
import org.example.teamflowbackend.comment.dto.CommentCreateRequest;
import org.example.teamflowbackend.comment.dto.CommentDeleteResponse;
import org.example.teamflowbackend.comment.dto.CommentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class CommentController {

    private final CommentService commentService;
    private final Logger log = LoggerFactory.getLogger(CommentController.class);

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @PostMapping("/tasks/{taskId}/comments")
    public CommentResponse create(
            @PathVariable UUID taskId,
            @Valid @RequestBody CommentCreateRequest request
    ) {
        return commentService.createComment(taskId, request);
    }

    @GetMapping("/tasks/{taskId}/comments")
    public Page<CommentResponse> listComment(
            @PathVariable UUID taskId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.ASC)
            Pageable pageable
    ) {
        if (pageable.getPageSize() < 1 || pageable.getPageSize() > 100)
            throw new IllegalArgumentException("size must be between 1 and 100");
        return commentService.getListComment(taskId, pageable);
    }

    @DeleteMapping("/comments/{id}")
    public CommentDeleteResponse delete(
            @PathVariable UUID id
    ) {
        return commentService.deleteComment(id);
    }
}
