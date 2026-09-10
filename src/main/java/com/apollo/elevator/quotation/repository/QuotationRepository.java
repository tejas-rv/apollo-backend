package com.apollo.elevator.quotation.repository;

import com.apollo.elevator.quotation.model.entity.Quotation;
import com.apollo.elevator.quotation.model.enums.QuotationStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuotationRepository extends JpaRepository<Quotation, Long> {

    @EntityGraph(attributePaths = "enquiry")
    @Query("""
            SELECT q
            FROM Quotation q
            JOIN q.enquiry e
            WHERE (:status IS NULL OR q.status = :status)
              AND (:enquiryStatus IS NULL OR e.status = :enquiryStatus)
              AND (
                    :query IS NULL OR :query = '' OR
                    LOWER(q.quotationNumber) LIKE LOWER(CONCAT('%', :query, '%')) OR
                    LOWER(e.fullName) LIKE LOWER(CONCAT('%', :query, '%')) OR
                    LOWER(e.phoneNumber) LIKE LOWER(CONCAT('%', :query, '%')) OR
                    LOWER(e.requirementType) LIKE LOWER(CONCAT('%', :query, '%')) OR
                    LOWER(COALESCE(q.notes, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                  )
            ORDER BY q.createdAt DESC
            """)
    Page<Quotation> search(
            @Param("status") QuotationStatus status,
            @Param("enquiryStatus") com.apollo.elevator.enquiry.model.enums.InquiryStatus enquiryStatus,
            @Param("query") String query,
            Pageable pageable
    );

    @Query("""
            SELECT CASE WHEN COUNT(q) > 0 THEN true ELSE false END
            FROM Quotation q
            WHERE q.enquiry.id = :enquiryId
              AND q.status IN :activeStatuses
            """)
    boolean existsActiveQuotationForEnquiry(
            @Param("enquiryId") Long enquiryId,
            @Param("activeStatuses") java.util.Set<QuotationStatus> activeStatuses
    );

    boolean existsByEnquiryIdAndStatus(Long enquiryId, QuotationStatus status);

    Optional<Quotation> findFirstByEnquiryIdAndStatusOrderByCreatedAtDesc(Long enquiryId, QuotationStatus status);

    @Query(value = """
            SELECT COALESCE(MAX(CAST(SUBSTRING(quotation_number, 9) AS BIGINT)), 0)
            FROM quotation
            WHERE quotation_number LIKE CONCAT('QT-', :year, '-%')
            """, nativeQuery = true)
    Long findMaxSequenceForYear(@Param("year") int year);

    @EntityGraph(attributePaths = "enquiry")
    Optional<Quotation> findById(Long id);
}
