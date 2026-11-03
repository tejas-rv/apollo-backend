package com.apollo.elevator.billing.repository;

import com.apollo.elevator.billing.model.entity.Bill;
import com.apollo.elevator.billing.model.enums.BillStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface BillRepository extends JpaRepository<Bill, Long>, JpaSpecificationExecutor<Bill> {

    boolean existsByInvoiceNumberIgnoreCase(String invoiceNumber);

    List<Bill> findByAmcContractIdAndAmcPeriodStartAndStatusNot(Long amcContractId, LocalDate amcPeriodStart, BillStatus status);

    List<Bill> findByStatusNot(BillStatus status);

    Optional<Bill> findTopByInvoiceNumberStartingWithOrderByIdDesc(String prefix);
}
