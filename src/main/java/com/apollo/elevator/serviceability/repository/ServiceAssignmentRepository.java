package com.apollo.elevator.serviceability.repository;

import com.apollo.elevator.serviceability.model.entity.ServiceAssignment;
import com.apollo.elevator.serviceability.model.enums.AssignmentStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ServiceAssignmentRepository extends JpaRepository<ServiceAssignment, Long> {

    Optional<ServiceAssignment> findByServiceVisitIdAndStatus(Long serviceVisitId, AssignmentStatus status);

    List<ServiceAssignment> findAllByScheduledDateAndStatusOrderByEngineerIdAscVisitSequenceAscIdAsc(
            LocalDate scheduledDate,
            AssignmentStatus status
    );

    List<ServiceAssignment> findAllByStatusAndScheduledDateLessThanEqualOrderByEngineerIdAscVisitSequenceAscIdAsc(
            AssignmentStatus status,
            LocalDate scheduledDate
    );

    List<ServiceAssignment> findAllByStatusAndEngineerIdAndScheduledDate(
            AssignmentStatus status,
            Long engineerId,
            LocalDate scheduledDate
    );

    long countByEngineerIdAndScheduledDateAndStatus(Long engineerId, LocalDate scheduledDate, AssignmentStatus status);

    long countByZoneIdAndScheduledDateAndStatus(String zoneId, LocalDate scheduledDate, AssignmentStatus status);

    @Query(value = """
            select a from ServiceAssignment a
            where (:date is null or a.scheduledDate = :date)
              and (:engineerId is null or a.engineerId = :engineerId)
              and (:zoneId is null or a.zoneId = :zoneId)
              and (:status is null or a.status = :status)
            """,
            countQuery = """
                    select count(a) from ServiceAssignment a
                    where (:date is null or a.scheduledDate = :date)
                      and (:engineerId is null or a.engineerId = :engineerId)
                      and (:zoneId is null or a.zoneId = :zoneId)
                      and (:status is null or a.status = :status)
                    """)
    Page<ServiceAssignment> search(
            @Param("date") LocalDate date,
            @Param("engineerId") Long engineerId,
            @Param("zoneId") String zoneId,
            @Param("status") AssignmentStatus status,
            Pageable pageable
    );
}
