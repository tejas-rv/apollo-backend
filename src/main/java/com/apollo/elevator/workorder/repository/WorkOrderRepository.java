package com.apollo.elevator.workorder.repository;

import com.apollo.elevator.enquiry.model.entity.ContactInquiry;
import com.apollo.elevator.workorder.model.entity.WorkOrder;
import com.apollo.elevator.workorder.model.enums.WorkOrderStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkOrderRepository extends JpaRepository<WorkOrder, Long> {

    @EntityGraph(attributePaths = {"enquiry", "quotation"})
    Optional<WorkOrder> findByEnquiryId(Long enquiryId);

    @EntityGraph(attributePaths = {"enquiry", "quotation"})
    @Query("""
            SELECT w
            FROM WorkOrder w
            JOIN w.enquiry e
            WHERE (:status IS NULL OR w.status = :status)
              AND (:query IS NULL OR :query = '' OR
                  LOWER(w.workOrderNumber) LIKE LOWER(CONCAT('%', :query, '%')) OR
                  LOWER(e.fullName) LIKE LOWER(CONCAT('%', :query, '%')) OR
                  LOWER(e.phoneNumber) LIKE LOWER(CONCAT('%', :query, '%')) OR
                  LOWER(COALESCE(w.notes, '')) LIKE LOWER(CONCAT('%', :query, '%')))
              AND e.status = com.apollo.elevator.enquiry.model.enums.InquiryStatus.WORK_ORDER
            ORDER BY w.createdAt DESC
            """)
    Page<WorkOrder> search(
            @Param("status") WorkOrderStatus status,
            @Param("query") String query,
            Pageable pageable
    );

    @Query(value = """
            SELECT COALESCE(MAX(CAST(SUBSTRING(work_order_number, 10) AS BIGINT)), 0)
            FROM work_order
            WHERE work_order_number LIKE CONCAT('WO-', :year, '-%')
            """, nativeQuery = true)
    Long findMaxSequenceForYear(@Param("year") int year);
}
