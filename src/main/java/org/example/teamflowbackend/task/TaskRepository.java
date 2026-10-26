package org.example.teamflowbackend.task;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface TaskRepository extends JpaRepository<Task, UUID>, JpaSpecificationExecutor<Task> {

    @Modifying
    @Query("update Task t set t.assignee = null, t.updatedAt = CURRENT_TIMESTAMP where t.project.id = :projectId and t.assignee.id = :userId")
    void clearAssignee(@Param("projectId") UUID projectId, @Param("userId") UUID userId);
}
