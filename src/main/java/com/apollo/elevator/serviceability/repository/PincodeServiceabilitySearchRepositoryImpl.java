package com.apollo.elevator.serviceability.repository;

import com.apollo.elevator.serviceability.model.entity.PincodeServiceability;
import com.apollo.elevator.serviceability.model.enums.PostalVerificationStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public class PincodeServiceabilitySearchRepositoryImpl implements PincodeServiceabilitySearchRepository {

    private static final List<String> SORTABLE_FIELDS = List.of(
            "pincode", "locality", "district", "state", "postalDivision", "active", "effectiveFrom");

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Page<PincodeServiceability> search(
            String pincode,
            String locality,
            String district,
            String zoneId,
            PostalVerificationStatus verificationStatus,
            Boolean active,
            Pageable pageable
    ) {
        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();

        CriteriaQuery<PincodeServiceability> contentQuery =
                criteriaBuilder.createQuery(PincodeServiceability.class);
        Root<PincodeServiceability> contentRoot = contentQuery.from(PincodeServiceability.class);
        contentRoot.fetch("zone", JoinType.LEFT);
        contentQuery.select(contentRoot)
                .where(predicates(criteriaBuilder, contentRoot, pincode, locality, district,
                        zoneId, verificationStatus, active))
                .orderBy(orderBy(criteriaBuilder, contentRoot, pageable.getSort()));
        List<PincodeServiceability> content = entityManager.createQuery(contentQuery)
                .setFirstResult(Math.toIntExact(pageable.getOffset()))
                .setMaxResults(pageable.getPageSize())
                .getResultList();

        CriteriaQuery<Long> countQuery = criteriaBuilder.createQuery(Long.class);
        Root<PincodeServiceability> countRoot = countQuery.from(PincodeServiceability.class);
        countQuery.select(criteriaBuilder.count(countRoot))
                .where(predicates(criteriaBuilder, countRoot, pincode, locality, district,
                        zoneId, verificationStatus, active));
        long total = entityManager.createQuery(countQuery).getSingleResult();
        return new PageImpl<>(content, pageable, total);
    }

    private static List<Predicate> predicates(
            CriteriaBuilder criteriaBuilder,
            Root<PincodeServiceability> root,
            String pincode,
            String locality,
            String district,
            String zoneId,
            PostalVerificationStatus verificationStatus,
            Boolean active
    ) {
        List<Predicate> predicates = new ArrayList<>();
        if (pincode != null) {
            predicates.add(criteriaBuilder.equal(root.get("pincode"), pincode));
        }
        if (locality != null) {
            predicates.add(criteriaBuilder.like(
                    criteriaBuilder.lower(root.get("locality")),
                    contains(locality)));
        }
        if (district != null) {
            predicates.add(criteriaBuilder.like(
                    criteriaBuilder.lower(root.get("district")),
                    contains(district)));
        }
        if (zoneId != null) {
            predicates.add(criteriaBuilder.equal(
                    root.join("zone", JoinType.LEFT).get("zoneId"), zoneId));
        }
        if (verificationStatus != null) {
            predicates.add(criteriaBuilder.equal(root.get("verificationStatus"), verificationStatus));
        }
        if (active != null) {
            predicates.add(criteriaBuilder.equal(root.get("active"), active));
        }
        return predicates;
    }

    private static List<jakarta.persistence.criteria.Order> orderBy(
            CriteriaBuilder criteriaBuilder,
            Root<PincodeServiceability> root,
            Sort sort
    ) {
        List<jakarta.persistence.criteria.Order> orders = new ArrayList<>();
        for (Sort.Order order : sort) {
            if (!SORTABLE_FIELDS.contains(order.getProperty())) {
                throw new IllegalArgumentException("Unsupported PIN-code sort field: " + order.getProperty());
            }
            orders.add(order.isAscending()
                    ? criteriaBuilder.asc(root.get(order.getProperty()))
                    : criteriaBuilder.desc(root.get(order.getProperty())));
        }
        if (orders.isEmpty()) {
            orders.add(criteriaBuilder.asc(root.get("pincode")));
            orders.add(criteriaBuilder.asc(root.get("locality")));
        }
        return orders;
    }

    private static String contains(String value) {
        return "%" + value.toLowerCase(Locale.ROOT) + "%";
    }
}
