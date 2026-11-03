package com.apollo.elevator.billing.model.entity;

import com.apollo.elevator.billing.model.enums.BillStatus;
import com.apollo.elevator.billing.model.enums.BillType;
import com.apollo.elevator.billing.model.enums.PaymentFrequency;
import com.apollo.elevator.customer.model.enums.ContractType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "bill")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Bill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "invoice_number", nullable = false, unique = true, length = 50)
    private String invoiceNumber;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "customer_name", nullable = false, length = 150)
    private String customerName;

    @Column(name = "amc_contract_id")
    private Long amcContractId;

    @Enumerated(EnumType.STRING)
    @Column(name = "bill_type", nullable = false, length = 20)
    private BillType billType;

    @Enumerated(EnumType.STRING)
    @Column(name = "contract_type", length = 20)
    private ContractType contractType;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_frequency", length = 20)
    private PaymentFrequency paymentFrequency;

    @Column(name = "instalment_no")
    private Integer instalmentNo;

    @Column(name = "total_instalments")
    private Integer totalInstalments;

    /** Service period covered by this bill (instalment period for AMC bills). */
    @Column(name = "period_start")
    private LocalDate periodStart;

    @Column(name = "period_end")
    private LocalDate periodEnd;

    /** Start date of the AMC year (contract period) this bill belongs to; part of the duplicate key. */
    @Column(name = "amc_period_start")
    private LocalDate amcPeriodStart;

    /** Yearly AMC plan price (before GST) at the time of billing. */
    @Column(name = "plan_amount", precision = 15, scale = 2)
    private BigDecimal planAmount;

    /** AMC portion of this bill (before GST), excluding any extra services. */
    @Column(name = "amc_base_amount", precision = 15, scale = 2)
    private BigDecimal amcBaseAmount;

    @Column(name = "invoice_date", nullable = false)
    private LocalDate invoiceDate;

    @Column(name = "taxable_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal taxableAmount;

    @Column(name = "gst_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal gstPercentage;

    @Column(name = "gst_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal gstAmount;

    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BillStatus status;

    @Column(name = "paid_date")
    private LocalDate paidDate;

    @Column(name = "payment_reference", length = 100)
    private String paymentReference;

    /** JSON snapshot of the BillRequest used to render the PDF, so it can be re-rendered or re-sent. */
    @Column(name = "request_json", nullable = false, columnDefinition = "TEXT")
    private String requestJson;

    @Column(name = "created_by", length = 150)
    private String createdBy;

    @Column(name = "updated_by", length = 150)
    private String updatedBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (status == null) {
            status = BillStatus.GENERATED;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
