package com.apollo.elevator.serviceability.model.entity;

import com.apollo.elevator.authorization.model.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "engineer_serviceability")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EngineerServiceability {

    @Id
    @Column(name = "engineer_id")
    private Long engineerId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "engineer_id", insertable = false, updatable = false)
    private User engineer;

    @Column(name = "daily_capacity", nullable = false)
    @Builder.Default
    private int dailyCapacity = 5;

    @Column(nullable = false)
    @Builder.Default
    private boolean available = true;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
