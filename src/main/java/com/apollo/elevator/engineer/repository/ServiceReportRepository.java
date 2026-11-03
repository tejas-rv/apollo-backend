package com.apollo.elevator.engineer.repository;

import com.apollo.elevator.engineer.model.entity.ServiceReport;
import com.apollo.elevator.engineer.model.enums.ReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface ServiceReportRepository extends JpaRepository<ServiceReport, Long> {

    Page<ServiceReport> findByEngineerUserIdOrderByVisitDateDesc(Long engineerUserId, Pageable pageable);

    List<ServiceReport> findByCustomerIdOrderByVisitDateDesc(Long customerId);

    List<ServiceReport> findByEngineerUserIdAndVisitDate(Long engineerUserId, LocalDate visitDate);

    long countByEngineerUserIdAndStatus(Long engineerUserId, ReportStatus status);

    boolean existsByEngineerUserId(Long engineerUserId);

    long countByEngineerUserIdAndStatusIn(Long engineerUserId, Collection<ReportStatus> statuses);

    long countByEngineerUserIdAndStatusInAndVisitDateBetween(
            Long engineerUserId, Collection<ReportStatus> statuses, LocalDate from, LocalDate to);

    List<ServiceReport> findTop10ByEngineerUserIdAndStatusInOrderByVisitDateDescIdDesc(
            Long engineerUserId, Collection<ReportStatus> statuses);

    /** Rows of [engineerUserId, reportCount, lastVisitDate] for the given statuses. */
    @Query("SELECT r.engineerUserId, COUNT(r), MAX(r.visitDate) FROM ServiceReport r " +
           "WHERE r.status IN :statuses GROUP BY r.engineerUserId")
    List<Object[]> summarizeByEngineer(Collection<ReportStatus> statuses);

    @Query("SELECT r FROM ServiceReport r WHERE r.engineerUserId = :engineerUserId " +
           "AND r.visitDate >= :from AND r.visitDate <= :to ORDER BY r.visitDate DESC")
    List<ServiceReport> findByEngineerUserIdAndDateRange(Long engineerUserId, LocalDate from, LocalDate to);
}
