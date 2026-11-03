package com.apollo.elevator.servicevisit.repository;

import com.apollo.elevator.customer.model.enums.ServiceVisitStatus;
import com.apollo.elevator.servicevisit.model.dto.ServiceVisitFilter;
import com.apollo.elevator.servicevisit.model.dto.ServiceVisitRow;
import com.apollo.elevator.servicevisit.model.dto.ServiceVisitSummary;
import com.apollo.elevator.servicevisit.model.enums.IssueRaisedBy;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.sql.Date;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

/** Read queries over AMC service visits (amc_service_history) across all customers. */
@Repository
public class ServiceVisitQueryRepository {

    private static final String SELECT = """
            SELECT h.id, h.service_date, h.status, h.engineer_name, h.assigned_engineer_id, h.work_done,
                   a.id, a.contract_number, a.status, l.id, l.brand, l.serial_number,
                   c.id, c.customer_code, c.customer_name, c.city,
                   (SELECT COUNT(*) FROM service_visit_reschedule r WHERE r.service_visit_id = h.id),
                   lr.raised_by, lr.reason,
                   (SELECT r.service_visit_id FROM service_visit_reschedule r
                     WHERE r.new_service_visit_id = h.id AND r.service_visit_id <> h.id
                     ORDER BY r.id DESC LIMIT 1),
                   (SELECT r.new_service_visit_id FROM service_visit_reschedule r
                     WHERE r.service_visit_id = h.id AND r.new_service_visit_id <> h.id
                     ORDER BY r.id DESC LIMIT 1)
            """;

    private static final String FROM = """
            FROM amc_service_history h
            JOIN amc_contract a ON a.id = h.amc_contract_id
            JOIN lift l ON l.id = a.lift_id
            JOIN customer c ON c.id = l.customer_id
            """;

    private static final String LAST_RESCHEDULE = """
            LEFT JOIN LATERAL (
                SELECT r.raised_by, r.reason FROM service_visit_reschedule r
                WHERE r.service_visit_id = h.id OR r.new_service_visit_id = h.id
                ORDER BY r.id DESC LIMIT 1
            ) lr ON TRUE
            """;

    private static final String RESCHEDULED = """
            EXISTS (SELECT 1 FROM service_visit_reschedule r
                    WHERE r.service_visit_id = h.id OR r.new_service_visit_id = h.id)
            """;

    @PersistenceContext
    private EntityManager entityManager;

    public Page<ServiceVisitRow> search(ServiceVisitFilter filter, LocalDate today, int page, int size) {
        Map<String, Object> params = new HashMap<>();
        String where = where(filter, today, params);

        Query countQuery = entityManager.createNativeQuery("SELECT COUNT(*) " + FROM + where);
        params.forEach(countQuery::setParameter);
        long total = toLong(countQuery.getSingleResult());

        Query query = entityManager.createNativeQuery(SELECT + FROM + LAST_RESCHEDULE + where
                        + " ORDER BY h.service_date DESC, h.id DESC")
                .setFirstResult(page * size)
                .setMaxResults(size);
        params.forEach(query::setParameter);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        return new PageImpl<>(rows.stream().map(ServiceVisitQueryRepository::toRow).toList(),
                PageRequest.of(page, size), total);
    }

    public Optional<ServiceVisitRow> findRow(Long visitId) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery(SELECT + FROM + LAST_RESCHEDULE + " WHERE h.id = :visitId")
                .setParameter("visitId", visitId)
                .getResultList();
        return rows.stream().findFirst().map(ServiceVisitQueryRepository::toRow);
    }

    public Optional<Long> findContractIdOfVisit(Long visitId) {
        @SuppressWarnings("unchecked")
        List<Object> rows = entityManager.createNativeQuery(
                        "SELECT amc_contract_id FROM amc_service_history WHERE id = :visitId")
                .setParameter("visitId", visitId)
                .getResultList();
        return rows.stream().findFirst().map(ServiceVisitQueryRepository::toLong);
    }

    public ServiceVisitSummary summary(LocalDate today) {
        Object[] row = (Object[]) entityManager.createNativeQuery("""
                        SELECT SUM(CASE WHEN h.status = 'SCHEDULED' THEN 1 ELSE 0 END),
                               SUM(CASE WHEN h.status = 'SCHEDULED' AND h.service_date < :today THEN 1 ELSE 0 END),
                               SUM(CASE WHEN h.status = 'COMPLETED' THEN 1 ELSE 0 END),
                               SUM(CASE WHEN h.status = 'CANCELLED' THEN 1 ELSE 0 END),
                               SUM(CASE WHEN %s THEN 1 ELSE 0 END)
                        FROM amc_service_history h
                        """.formatted(RESCHEDULED))
                .setParameter("today", today)
                .getSingleResult();
        return new ServiceVisitSummary(toLong(row[0]), toLong(row[1]), toLong(row[2]), toLong(row[3]),
                toLong(row[4]));
    }

    private static String where(ServiceVisitFilter filter, LocalDate today, Map<String, Object> params) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        if (filter.status() != null) {
            where.append(" AND h.status = :status");
            params.put("status", filter.status().name());
        }
        if (filter.overdueOnly()) {
            where.append(" AND h.status = 'SCHEDULED' AND h.service_date < :today");
            params.put("today", today);
        }
        if (filter.from() != null) {
            where.append(" AND h.service_date >= :from");
            params.put("from", filter.from());
        }
        if (filter.to() != null) {
            where.append(" AND h.service_date <= :to");
            params.put("to", filter.to());
        }
        if (filter.rescheduledOnly()) {
            where.append(" AND ").append(RESCHEDULED);
        }
        if (filter.query() != null && !filter.query().isBlank()) {
            where.append("""
                     AND (LOWER(c.customer_name) LIKE :q OR LOWER(c.customer_code) LIKE :q
                          OR LOWER(COALESCE(c.mobile_number, '')) LIKE :q
                          OR LOWER(COALESCE(a.contract_number, '')) LIKE :q
                          OR LOWER(h.engineer_name) LIKE :q)
                    """);
            params.put("q", "%" + filter.query().trim().toLowerCase(Locale.ROOT) + "%");
        }
        return where.toString();
    }

    private static ServiceVisitRow toRow(Object[] row) {
        return new ServiceVisitRow(
                toLong(row[0]),
                toLocalDate(row[1]),
                row[2] == null ? null : ServiceVisitStatus.valueOf((String) row[2]),
                (String) row[3],
                toNullableLong(row[4]),
                (String) row[5],
                toLong(row[6]),
                (String) row[7],
                (String) row[8],
                toLong(row[9]),
                (String) row[10],
                (String) row[11],
                toLong(row[12]),
                (String) row[13],
                (String) row[14],
                (String) row[15],
                toLong(row[16]),
                row[17] == null ? null : IssueRaisedBy.valueOf((String) row[17]),
                (String) row[18],
                toNullableLong(row[19]),
                toNullableLong(row[20]));
    }

    private static long toLong(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    private static Long toNullableLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
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
}
