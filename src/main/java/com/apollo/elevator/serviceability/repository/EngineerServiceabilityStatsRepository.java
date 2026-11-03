package com.apollo.elevator.serviceability.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class EngineerServiceabilityStatsRepository {

    @PersistenceContext
    private EntityManager entityManager;

    public List<Object[]> counts(LocalDate today) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT u.id,
                       SUM(CASE WHEN h.status = 'SCHEDULED' AND h.service_date = :today THEN 1 ELSE 0 END),
                       SUM(CASE WHEN h.status = 'COMPLETED' AND h.service_date = :today THEN 1 ELSE 0 END),
                       SUM(CASE WHEN h.status = 'SCHEDULED'
                                     AND COALESCE(sa.service_due_date, h.service_date) < :today THEN 1 ELSE 0 END)
                FROM app_user u
                LEFT JOIN amc_service_history h ON h.assigned_engineer_id = u.id
                LEFT JOIN service_assignment sa
                       ON sa.service_visit_id = h.id AND sa.status = 'ACTIVE'
                WHERE u.user_type = 'ENGINEER'
                GROUP BY u.id
                """)
                .setParameter("today", today)
                .getResultList();
        return rows;
    }

    public Object[] dashboardCounts(LocalDate today, LocalDate withinThreeDays) {
        return (Object[]) entityManager.createNativeQuery("""
                SELECT COUNT(*),
                       COUNT(*) FILTER (WHERE COALESCE(sa.service_due_date, h.service_date) < :today),
                       COUNT(*) FILTER (WHERE COALESCE(sa.service_due_date, h.service_date) = :today),
                       COUNT(*) FILTER (WHERE COALESCE(sa.service_due_date, h.service_date) > :today
                                           AND COALESCE(sa.service_due_date, h.service_date) <= :withinThreeDays),
                       COUNT(*) FILTER (WHERE h.assigned_engineer_id IS NULL)
                FROM amc_service_history h
                LEFT JOIN service_assignment sa
                       ON sa.service_visit_id = h.id AND sa.status = 'ACTIVE'
                WHERE h.status = 'SCHEDULED'
                """)
                .setParameter("today", today)
                .setParameter("withinThreeDays", withinThreeDays)
                .getSingleResult();
    }
}
