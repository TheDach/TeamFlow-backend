package org.example.teamflowbackend.common.exception;

import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {

    private final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);



    /* ---------- 404: проект не найден ---------- */
    @ExceptionHandler(ProjectNotFoundException.class)
    public ProblemDetail handleProjectNotFound(ProjectNotFoundException e) {
        log.warn("Project not found: {}", e.getMessage());

        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        pd.setTitle("Project Not Found");
        pd.setDetail(e.getMessage());
        return pd;
    }

    /* ---------- 403: нет доступа к проекту ---------- */
    @ExceptionHandler(ProjectAccessDeniedException.class)
    public ProblemDetail handleProjectAccessDenied(ProjectAccessDeniedException e) {
        log.warn("Project access denied: {}", e.getMessage());

        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        pd.setTitle("Project Access Denied");
        pd.setDetail(e.getMessage());
        return pd;
    }

    /* ---------- 401: текущий пользователь не найден ---------- */
    @ExceptionHandler(CurrentUserNotFoundException.class)
    public ProblemDetail handleCurrentUserNotFound(CurrentUserNotFoundException e) {
        log.error("Current user not found: {}", e.getMessage());

        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED);
        pd.setTitle("Unauthorized");
        pd.setDetail(e.getMessage());
        return pd;
    }


    /* ---------- 404: любое EntityNotFoundException ---------- */
    @ExceptionHandler(EntityNotFoundException.class)
    public ProblemDetail handleNotFound(
            EntityNotFoundException e
    ) {
        log.error("Handler Not Found: ", e);

        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        pd.setTitle("Not Found");
        pd.setDetail(e.getMessage());

        return pd;
    }

    /* ---------- 500: всё остальное ---------- */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handlerGenericHandler(
            Exception e
    ) {
        log.error("Handler exception: ", e);

        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problemDetail.setTitle("Internal server error");
        problemDetail.setDetail(e.getMessage());

        return problemDetail;
    }
}
