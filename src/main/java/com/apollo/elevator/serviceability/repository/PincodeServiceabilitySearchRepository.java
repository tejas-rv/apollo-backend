package com.apollo.elevator.serviceability.repository;

import com.apollo.elevator.serviceability.model.entity.PincodeServiceability;
import com.apollo.elevator.serviceability.model.enums.PostalVerificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PincodeServiceabilitySearchRepository {

    Page<PincodeServiceability> search(
            String pincode,
            String locality,
            String district,
            String zoneId,
            PostalVerificationStatus verificationStatus,
            Boolean active,
            Pageable pageable
    );
}
