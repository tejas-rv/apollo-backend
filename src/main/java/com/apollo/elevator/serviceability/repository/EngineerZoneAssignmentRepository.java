package com.apollo.elevator.serviceability.repository;

import com.apollo.elevator.serviceability.model.entity.EngineerZoneAssignment;
import com.apollo.elevator.serviceability.model.enums.EngineerZoneType;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EngineerZoneAssignmentRepository extends JpaRepository<EngineerZoneAssignment, Long> {

    @EntityGraph(attributePaths = {"engineer", "zone"})
    @Query("""
            select a from EngineerZoneAssignment a
            where a.active = true
              and a.effectiveFrom <= :onDate
              and (a.effectiveTo is null or a.effectiveTo >= :onDate)
              and (:zoneId is null or a.zone.zoneId = :zoneId)
              and (:engineerId is null or a.engineer.id = :engineerId)
            order by a.zone.zoneId, a.assignmentType, a.engineer.id
            """)
    List<EngineerZoneAssignment> findCurrent(
            @Param("onDate") LocalDate onDate,
            @Param("zoneId") String zoneId,
            @Param("engineerId") Long engineerId
    );

    boolean existsByEngineerIdAndZoneZoneIdAndAssignmentTypeAndActiveTrue(
            Long engineerId,
            String zoneId,
            EngineerZoneType assignmentType
    );

    boolean existsByEngineerIdAndZoneZoneIdAndAssignmentTypeAndActiveTrueAndIdNot(
            Long engineerId,
            String zoneId,
            EngineerZoneType assignmentType,
            Long id
    );

    boolean existsByEngineerIdAndAssignmentTypeAndActiveTrue(Long engineerId, EngineerZoneType assignmentType);

    boolean existsByEngineerIdAndAssignmentTypeAndActiveTrueAndIdNot(
            Long engineerId,
            EngineerZoneType assignmentType,
            Long id
    );

    @EntityGraph(attributePaths = {"engineer", "zone"})
    @Query("""
            select a from EngineerZoneAssignment a
            where (:engineerId is null or a.engineer.id = :engineerId)
            order by a.zone.zoneId, a.engineer.id, a.assignmentType
            """)
    List<EngineerZoneAssignment> searchAll(@Param("engineerId") Long engineerId);
}
