package com.apollo.elevator.servicevisit.repository;

import com.apollo.elevator.servicevisit.model.entity.ServiceVisitReschedule;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ServiceVisitRescheduleRepository extends JpaRepository<ServiceVisitReschedule, Long> {

    @Query("SELECT r FROM ServiceVisitReschedule r WHERE r.serviceVisitId = :visitId "
            + "OR r.newServiceVisitId = :visitId ORDER BY r.createdAt DESC, r.id DESC")
    List<ServiceVisitReschedule> findHistory(Long visitId);

    /** An open follow-up already scheduled for this visit (re-service not yet done). */
    @Query(value = """
            SELECT r.new_service_visit_id FROM service_visit_reschedule r
            JOIN amc_service_history h ON h.id = r.new_service_visit_id
            WHERE r.service_visit_id = :visitId AND r.new_service_visit_id <> :visitId
              AND h.status = 'SCHEDULED'
            ORDER BY r.id DESC LIMIT 1
            """, nativeQuery = true)
    Optional<Long> findOpenFollowUp(Long visitId);
}
