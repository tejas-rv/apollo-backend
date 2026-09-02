package com.apollo.elevators.customer.model.entity;

import com.apollo.elevators.customer.model.enums.DoorType;
import com.apollo.elevators.customer.model.enums.DriveType;
import com.apollo.elevators.customer.model.enums.LiftType;
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

    @Enumerated(EnumType.STRING)
    @Column(name = "door_type", length = 20)
    private DoorType doorType;

    @Column(name = "machine_type", length = 100)
    private String machineType;

    @Column(name = "machine_name", length = 100)
    private String machineName;

    @Column(name = "kw", precision = 10, scale = 2)
    private java.math.BigDecimal kw;

    @Column(name = "amps", precision = 10, scale = 2)
    private java.math.BigDecimal amps;

    @Column(name = "speed", precision = 10, scale = 2)
    private java.math.BigDecimal speed;

    @Column(name = "voltage", precision = 10, scale = 2)
    private java.math.BigDecimal voltage;

    @Column(name = "frequency", precision = 10, scale = 2)
    private java.math.BigDecimal frequency;

    @Column(name = "osg_type", length = 100)
    private String osgType;

    @Column(name = "rated_speed", precision = 10, scale = 2)
    private java.math.BigDecimal ratedSpeed;

    @Column(name = "tripping_speed", precision = 10, scale = 2)
    private java.math.BigDecimal trippingSpeed;

    @Column(name = "is_ups_present")
    private Boolean isUpsPresent;

    @Column(name = "ups_type", length = 100)
    private String upsType;

    @Column(name = "kva", precision = 10, scale = 2)
    private java.math.BigDecimal kva;

    @Column(name = "manufactured_by", length = 100)
    private String manufacturedBy;

    @Column(name = "year_of_manufacture")
    private Integer yearOfManufacture;

    @Column(name = "no_of_grooves")
    private Integer noOfGrooves;

    @Column(name = "friction_sheave_diameter")
    private Integer frictionSheaveDiameter;

    @Column(name = "no_of_ropes")
    private Integer noOfRopes;

    @Column(name = "dia_of_the_rope_mm")
    private Integer diaOfTheRopeMm;

    @Column(name = "length_of_the_rope_mm")
    private Integer lengthOfTheRopeMm;

    @Column(name = "is_deflector_pulley")
    private Boolean isDeflectorPulley;

    @Column(name = "deflector_pulley_diameter")
    private Integer deflectorPulleyDiameter;

    @Column(name = "deflector_pulley_no_of_grooves")
    private Integer deflectorPulleyNoOfGrooves;

    @Column(name = "roping", length = 50)
    private String roping;

    @Column(name = "main_motor_kw", length = 50)
    private String mainMotorKw;

    @Column(name = "main_motor_amps", length = 50)
    private String mainMotorAmps;

    @Column(name = "main_motor_speed", length = 50)
    private String mainMotorSpeed;

    @Column(name = "main_motor_voltage", length = 50)
    private String mainMotorVoltage;

    @Column(name = "main_motor_frequency", length = 50)
    private String mainMotorFrequency;

    @Column(name = "main_motor_no_of_poles")
    private Integer mainMotorNoOfPoles;

    @Column(name = "battery_make", length = 100)
    private String batteryMake;

    @Column(name = "battery_voltage", length = 50)
    private String batteryVoltage;

    @Column(name = "battery_no_of_batteries")
    private Integer batteryNoOfBatteries;

    @Builder.Default
    @OneToMany(
        cascade = CascadeType.ALL,
        orphanRemoval = true
    )
    @JoinColumn(name = "lift_id", nullable = false)
    private List<AmcContract> amcContracts = new ArrayList<>();
}
