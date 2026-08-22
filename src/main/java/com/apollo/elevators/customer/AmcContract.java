package com.apollo.elevators.customer;

import com.apollo.elevators.enums.AmcStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "amc_contract")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AmcContract {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "contract_number", length = 50)
    private String contractNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private AmcStatus status;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "amc_type", length = 50)
    private String amcType;

    @Column(name = "amc_amount", precision = 12, scale = 2)
    private BigDecimal amcAmount;

    @Column(name = "payment_frequency", length = 20)
    private String paymentFrequency;

    @Column(name = "next_payment_date")
    private LocalDate nextPaymentDate;

    @Column(name = "next_service_date")
    private LocalDate nextServiceDate;

    @Column(name = "total_services")
    private Integer totalServices;

    @Column(name = "completed_services")
    private Integer completedServices;

    @Column(name = "terms_and_conditions", length = 500)
    private String termsAndConditions;

    @Column(name = "remarks", length = 500)
    private String remarks;
}
