package com.apollo.elevators.customer.model.entity;

import com.apollo.elevators.customer.model.enums.AmcStatus;
import com.apollo.elevators.customer.model.enums.ContractType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
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

    @Enumerated(EnumType.STRING)
    @Column(name = "contract_type", length = 20)
    private ContractType contractType;

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

    @Builder.Default
    @OneToMany(
            cascade = jakarta.persistence.CascadeType.ALL,
            orphanRemoval = true
    )
    @JoinColumn(name = "amc_contract_id", nullable = false)
    private List<ServiceHistory> serviceHistory = new ArrayList<>();

}
