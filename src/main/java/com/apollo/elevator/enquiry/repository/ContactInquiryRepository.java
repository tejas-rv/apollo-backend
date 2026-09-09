package com.apollo.elevator.enquiry.repository;

import com.apollo.elevator.enquiry.model.entity.ContactInquiry;
import com.apollo.elevator.enquiry.model.enums.InquiryStatus;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContactInquiryRepository
        extends JpaRepository<ContactInquiry, Long> {

    @Query("""
            SELECT c
            FROM ContactInquiry c
            WHERE (:name IS NULL OR LOWER(c.fullName) LIKE LOWER(CONCAT('%', :name, '%')))
              AND (:phone IS NULL OR c.phoneNumber LIKE CONCAT('%', :phone, '%'))
              AND (:status IS NULL OR c.status = :status)
              AND (:from IS NULL OR c.createdAt >= :from)
              AND (:to IS NULL OR c.createdAt <= :to)
            """)
    Page<ContactInquiry> search(
            @Param("name") String name,
            @Param("phone") String phone,
            @Param("status") InquiryStatus status,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable
    );
}
