package com.apollo.elevator.serviceability.repository;

import com.apollo.elevator.serviceability.model.entity.PincodeServiceability;
import com.apollo.elevator.serviceability.model.enums.PostalVerificationStatus;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PincodeServiceabilityRepository extends
        JpaRepository<PincodeServiceability, Long>, PincodeServiceabilitySearchRepository {

    boolean existsByPincodeAndLocalityIgnoreCaseAndActiveTrue(String pincode, String locality);

    @Query("""
            select p from PincodeServiceability p
            left join fetch p.zone
            where p.pincode = :pincode and p.active = true
              and p.effectiveFrom <= :onDate
              and (p.effectiveTo is null or p.effectiveTo >= :onDate)
            order by p.locality
            """)
    List<PincodeServiceability> findCurrentMappings(
            @Param("pincode") String pincode,
            @Param("onDate") LocalDate onDate
    );
}
