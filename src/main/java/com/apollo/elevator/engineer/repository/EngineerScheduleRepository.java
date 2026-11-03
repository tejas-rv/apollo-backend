package com.apollo.elevator.engineer.repository;

import com.apollo.elevator.engineer.model.dto.EngineerScheduledVisit;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.sql.Date;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

/**
 * Queries over SCHEDULED AMC service visits. Visits are assigned to an engineer by
 * {@code assigned_engineer_id}; legacy visits that could not be linked to a unique engineer
 * fall back to a case-insensitive match on the recorded engineer name.
 */
@Repository
public class EngineerScheduleRepository {

    @PersistenceContext
    private EntityManager entityManager;

    /** Scheduled visit counts ([upcoming, overdue]) by engineer ID, plus unassigned visits by normalised name. */
    public ScheduledCounts countScheduled(LocalDate today) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT h.assigned_engineer_id,
                       CASE WHEN h.assigned_engineer_id IS NULL THEN LOWER(TRIM(h.engineer_name)) END,
                       SUM(CASE WHEN h.service_date >= :today THEN 1 ELSE 0 END),
                       SUM(CASE WHEN h.service_date < :today THEN 1 ELSE 0 END)
                FROM amc_service_history h
                WHERE h.status = 'SCHEDULED'
                GROUP BY 1, 2
                """)
                .setParameter("today", today)
                .getResultList();
        Map<Long, long[]> byEngineer = new HashMap<>();
        Map<String, long[]> unassignedByName = new HashMap<>();
        for (Object[] row : rows) {
            long[] counts = {toLong(row[2]), toLong(row[3])};
            if (row[0] != null) {
                byEngineer.put(toLong(row[0]), counts);
            } else if (row[1] != null) {
                unassignedByName.put((String) row[1], counts);
            }
        }
        return new ScheduledCounts(byEngineer, unassignedByName);
    }

    public List<EngineerScheduledVisit> findScheduledVisits(Long engineerId, Collection<String> normalisedNames, int limit) {
        boolean byName = !normalisedNames.isEmpty();
        Query query = entityManager.createNativeQuery("""
                SELECT h.id, h.service_date, h.work_done, c.id, c.customer_name, c.city, a.contract_number,
                       a.id, l.brand, l.serial_number
                FROM amc_service_history h
                JOIN amc_contract a ON a.id = h.amc_contract_id
                JOIN lift l ON l.id = a.lift_id
                JOIN customer c ON c.id = l.customer_id
                WHERE h.status = 'SCHEDULED'
                  AND (h.assigned_engineer_id = :engineerId
                """ + (byName
                        ? "       OR (h.assigned_engineer_id IS NULL AND LOWER(TRIM(h.engineer_name)) IN (:names)))\n"
                        : "      )\n") + """
                ORDER BY h.service_date ASC, h.id ASC
                """)
                .setParameter("engineerId", engineerId)
                .setMaxResults(limit);
        if (byName) {
            query.setParameter("names", normalisedNames);
        }
        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        return rows.stream()
                .map(row -> new EngineerScheduledVisit(
                        toLong(row[0]),
                        toLocalDate(row[1]),
                        (String) row[2],
                        toLong(row[3]),
                        (String) row[4],
                        (String) row[5],
                        (String) row[6],
                        toLong(row[7]),
                        (String) row[8],
                        (String) row[9]))
                .toList();
    }

    public long countScheduledAssignedTo(Long engineerId) {
        return toLong(entityManager.createNativeQuery("""
                SELECT COUNT(*) FROM amc_service_history
                WHERE assigned_engineer_id = :engineerId AND status = 'SCHEDULED'
                """)
                .setParameter("engineerId", engineerId)
                .getSingleResult());
    }

    /** Keeps the display name of the engineer's scheduled visits in step with their profile. */
    public int renameScheduledVisits(Long engineerId, String engineerName) {
        return entityManager.createNativeQuery("""
                UPDATE amc_service_history
                SET engineer_name = :engineerName, updated_at = CURRENT_TIMESTAMP
                WHERE assigned_engineer_id = :engineerId AND status = 'SCHEDULED'
                  AND engineer_name <> :engineerName
                """)
                .setParameter("engineerId", engineerId)
                .setParameter("engineerName", engineerName)
                .executeUpdate();
    }

    private static long toLong(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    private static LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate date) {
            return date;
        }
        if (value instanceof Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        return null;
    }

    public record ScheduledCounts(Map<Long, long[]> byEngineer, Map<String, long[]> unassignedByName) {

        /** [upcoming, overdue] for an engineer: assigned visits plus unassigned visits matching their names. */
        public long[] forEngineer(Long engineerId, Collection<String> normalisedNames) {
            long[] total = new long[2];
            add(total, byEngineer.get(engineerId));
            for (String name : normalisedNames) {
                add(total, unassignedByName.get(name));
            }
            return total;
        }

        private static void add(long[] total, long[] counts) {
            if (counts != null) {
                total[0] += counts[0];
                total[1] += counts[1];
            }
        }
    }
}
