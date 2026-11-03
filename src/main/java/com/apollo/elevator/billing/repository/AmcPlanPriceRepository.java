package com.apollo.elevator.billing.repository;

import com.apollo.elevator.billing.model.entity.AmcPlanPrice;
import com.apollo.elevator.customer.model.enums.ContractType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AmcPlanPriceRepository extends JpaRepository<AmcPlanPrice, ContractType> {
}
