package com.apollo.elevator.serviceability.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.sql.Date;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class ServiceabilityVisitQueryRepository {

    @PersistenceContext
    private EntityManager entityManager;

    public List<VisitContext> findUnassignedScheduledThrough(
            LocalDate throughDate,
            Collection<Long> visitIds
    ) {
        String selection = """
                SELECT h.id, h.service_date, h.assigned_engineer_id, h.engineer_name,
                       c.customer_name, c.address, c.city, c.pincode, l.serial_number
                FROM amc_service_history h
                JOIN amc_contract a ON a.id = h.amc_contract_id
                JOIN lift l ON l.id = a.lift_id
                JOIN customer c ON c.id = l.customer_id
                WHERE h.status = 'SCHEDULED'
                  AND h.service_date <= :throughDate
                  AND h.assigned_engineer_id IS NULL
                  AND NOT EXISTS (
                      SELECT 1 FROM service_assignment sa
                      WHERE sa.service_visit_id = h.id AND sa.status = 'ACTIVE'
                  )
                """;
        String filterIds = visitIds == null || visitIds.isEmpty()
                ? ""
                : " AND h.id IN (:visitIds)";
        Query query = entityManager.createNativeQuery(selection + filterIds + " ORDER BY h.service_date, h.id")
                .setParameter("throughDate", throughDate);
        if (visitIds != null && !visitIds.isEmpty()) {
            query.setParameter("visitIds", visitIds);
        }
        return rows(query);
    }

    public List<VisitContext> findScheduledForDate(LocalDate date) {
        Query query = entityManager.createNativeQuery("""
                SELECT h.id, h.service_date, h.assigned_engineer_id, h.engineer_name,
                       c.customer_name, c.address, c.city, c.pincode, l.serial_number
                FROM amc_service_history h
                JOIN amc_contract a ON a.id = h.amc_contract_id
                JOIN lift l ON l.id = a.lift_id
                JOIN customer c ON c.id = l.customer_id
                WHERE h.status = 'SCHEDULED' AND h.service_date = :date
                ORDER BY h.service_date, h.id
                """)
                .setParameter("date", date);
        return rows(query);
    }

    public List<VisitContext> findScheduledThroughDate(LocalDate date) {
        Query query = entityManager.createNativeQuery("""
                SELECT h.id, h.service_date, h.assigned_engineer_id, h.engineer_name,
                       c.customer_name, c.address, c.city, c.pincode, l.serial_number
                FROM amc_service_history h
                JOIN amc_contract a ON a.id = h.amc_contract_id
                JOIN lift l ON l.id = a.lift_id
                JOIN customer c ON c.id = l.customer_id
                WHERE h.status = 'SCHEDULED' AND h.service_date <= :date
                ORDER BY h.service_date, h.id
                """)
                .setParameter("date", date);
        return rows(query);
    }

    public Optional<VisitContext> findById(Long visitId) {
        Query query = entityManager.createNativeQuery("""
                SELECT h.id, h.service_date, h.assigned_engineer_id, h.engineer_name,
                       c.customer_name, c.address, c.city, c.pincode, l.serial_number
                FROM amc_service_history h
                JOIN amc_contract a ON a.id = h.amc_contract_id
                JOIN lift l ON l.id = a.lift_id
                JOIN customer c ON c.id = l.customer_id
                WHERE h.id = :visitId
                """)
                .setParameter("visitId", visitId);
        List<VisitContext> contexts = rows(query);
        return contexts.stream().findFirst();
    }

    private static List<VisitContext> rows(Query query) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        return rows.stream().map(row -> new VisitContext(
                ((Number) row[0]).longValue(),
                toLocalDate(row[1]),
                row[2] == null ? null : ((Number) row[2]).longValue(),
                (String) row[3],
                (String) row[4],
                (String) row[5],
                (String) row[6],
                (String) row[7],
                (String) row[8]
        )).toList();
    }

    private static LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate date) {
            return date;
        }
        if (value instanceof Date date) {
            return date.toLocalDate();
        }
        return null;
    }

    public record VisitContext(
            Long visitId,
            LocalDate dueDate,
            Long assignedEngineerId,
            String engineerName,
            String customerName,
            String address,
            String city,
            String pincode,
            String liftSerialNumber
    ) {
    }
}
