package org.example.teamflowbackend.project;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, UUID> {

    Page<ProjectMember> findAllByUser_KeycloakId(
            String keycloakId,
            Pageable pageable
    );

    Page<ProjectMember> findAllByProject_Id(UUID projectId, Pageable pageable);

    Optional<ProjectMember> findByProject_IdAndUser_KeycloakId(
            UUID projectId,
            String keycloakId
    );

    Optional<ProjectMember> findByProject_IdAndUser_Id(
            UUID projectId,
            UUID userId
    );

    boolean existsByProject_IdAndUser_Id(
            UUID projectId,
            UUID userId
    );

    boolean existsByProject_IdAndUser_KeycloakId(
            UUID projectId,
            String keycloakId
    );

    Optional<ProjectMember> findByProject_IdAndRole(
            UUID projectId,
            ProjectMemberRole projectMemberRole
    );


}
