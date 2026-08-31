package com.apollo.elevators.engineer.model.entity;

import com.apollo.elevators.engineer.model.enums.ReportStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "service_report")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FK to amc_contract.id */
    @Column(name = "amc_contract_id", nullable = false)
    private Long amcContractId;

    /** FK to app_user.id — the engineer who submitted */
    @Column(name = "engineer_user_id", nullable = false)
    private Long engineerUserId;

    @Column(name = "engineer_name", nullable = false, length = 100)
    private String engineerName;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "customer_name", nullable = false, length = 100)
    private String customerName;

    @Column(name = "visit_date", nullable = false)
    private LocalDate visitDate;

    @Column(name = "overall_notes", length = 2000)
    private String overallNotes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ReportStatus status = ReportStatus.DRAFT;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "pdf_sent_at")
    private LocalDateTime pdfSentAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder.Default
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "service_report_id", nullable = false)
    private List<ServiceCheckItem> checkItems = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
