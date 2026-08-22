package com.apollo.elevators.customer;

import com.apollo.elevators.enums.DriveType;
import com.apollo.elevators.enums.LiftType;
import jakarta.persistence.CascadeType;
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
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "lift")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Lift {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "lift_type", length = 50)
    private LiftType liftType;

    @Enumerated(EnumType.STRING)
    @Column(name = "drive_type", length = 50)
    private DriveType driveType;

    @Column(name = "number_of_floors")
    private Integer numberOfFloors;

    @Column(name = "capacity_in_kg")
    private Integer capacityInKg;

    @Column(name = "capacity_in_persons")
    private Integer capacityInPersons;

    @Column(name = "brand", length = 100)
    private String brand;

    @Column(name = "lift_model", length = 100)
    private String liftModel;

    @Column(name = "installation_type", length = 100)
    private String installationType;

    @Column(name = "year_of_installation")
    private Integer yearOfInstallation;

    @Column(name = "serial_number", length = 100)
    private String serialNumber;

    @Builder.Default
    @OneToMany(
        cascade = CascadeType.ALL,
        orphanRemoval = true
    )
    @JoinColumn(name = "lift_id", nullable = false)
    private List<AmcContract> amcContracts = new ArrayList<>();
}
