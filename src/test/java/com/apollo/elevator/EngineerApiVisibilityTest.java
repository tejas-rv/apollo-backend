package com.apollo.elevator;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.apollo.elevator.customer.model.entity.AmcContract;
import com.apollo.elevator.customer.model.entity.Customer;
import com.apollo.elevator.customer.model.entity.Lift;
import com.apollo.elevator.customer.model.enums.AmcStatus;
import com.apollo.elevator.customer.model.enums.ContractType;
import com.apollo.elevator.customer.model.enums.DoorType;
import com.apollo.elevator.customer.model.enums.DriveType;
import com.apollo.elevator.customer.model.enums.LiftType;
import com.apollo.elevator.engineer.model.dto.EngineerCustomerDto;
import com.apollo.elevator.engineer.model.dto.EngineerLiftDto;
import com.apollo.elevator.engineer.model.dto.ServiceCheckItemDto;
import com.apollo.elevator.engineer.model.dto.ServiceReportRequest;
import com.apollo.elevator.engineer.model.dto.ServiceReportResponse;
import com.apollo.elevator.engineer.model.enums.AnswerType;
import com.apollo.elevator.engineer.model.enums.ReportStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class EngineerApiVisibilityTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void engineer_request_allows_missing_amc_contract_id() {
        ServiceReportRequest request = new ServiceReportRequest(
                25L,
                LocalDate.of(2026, 9, 10),
                "Routine inspection completed",
                null,
                List.of()
        );

        assertNotNull(request);
        assertTrue(request.checkItems().isEmpty());
    }

    @Test
    void engineer_customer_serialization_excludes_amc_fields() throws Exception {
        Customer customer = new Customer();
        customer.setId(25L);
        customer.setCustomerCode("CUST-25");
        customer.setCustomerName("Customer Name");
        customer.setMobileNumber("9876543210");
        customer.setAddress("Address");
        customer.setCity("Bangalore");
        customer.setState("Karnataka");
        customer.setPincode("560001");
        customer.setLifts(List.of(buildLiftWithAmc()));

        EngineerCustomerDto dto = new EngineerCustomerDto(
                customer.getId(),
                customer.getCustomerCode(),
                customer.getCustomerName(),
                customer.getMobileNumber(),
                customer.getAddress(),
                customer.getCity(),
                customer.getState(),
                customer.getPincode(),
                List.of(new EngineerLiftDto(
                        101L,
                        LiftType.HOME,
                        DriveType.TRACTION,
                        5,
                        400,
                        6,
                        "Apollo",
                        "A-100",
                        "RESIDENTIAL",
                        2020,
                        "SN-123",
                        DoorType.AUTO,
                        "MOTOR",
                        "Main Motor",
                        BigDecimal.valueOf(7.5),
                        BigDecimal.valueOf(12),
                        BigDecimal.valueOf(1.0),
                        BigDecimal.valueOf(230),
                        BigDecimal.valueOf(50),
                        "OSG",
                        BigDecimal.valueOf(1.0),
                        BigDecimal.valueOf(0.9),
                        Boolean.TRUE,
                        "UPS",
                        BigDecimal.valueOf(15),
                        "Apollo",
                        2020,
                        2,
                        300,
                        4,
                        500,
                        1,
                        false,
                        250,
                        1,
                        "ROPE",
                        "7.5",
                        "12",
                        "1.0",
                        "230",
                        "50",
                        4,
                        "Battery",
                        "24V",
                        2
                ))
        );

        String json = objectMapper.writeValueAsString(dto);
        assertFalse(json.contains("amcContracts"));
        assertFalse(json.contains("contractNumber"));
        assertFalse(json.contains("nextServiceDate"));
        assertFalse(json.contains("amcContractId"));
        assertTrue(json.contains("\"customerName\":\"Customer Name\""));
    }

    @Test
    void engineer_service_report_serialization_excludes_amc_data() throws Exception {
        ServiceReportResponse response = new ServiceReportResponse(
                1L,
                25L,
                "Customer Name",
                99L,
                "engineer-user",
                LocalDate.of(2026, 9, 10),
                "Routine inspection completed",
                ReportStatus.SUBMITTED,
                LocalDateTime.of(2026, 9, 10, 10, 0),
                null,
                List.of(new ServiceCheckItemDto(1L, 1, "Cleaning of Parts", AnswerType.YES_NO_NA, true, null))
        );

        String json = objectMapper.writeValueAsString(response);
        assertFalse(json.contains("amcContractId"));
        assertFalse(json.contains("contractNumber"));
        assertFalse(json.contains("nextServiceDate"));
        assertTrue(json.contains("\"customerId\":25"));
    }

    private Lift buildLiftWithAmc() {
        Lift lift = new Lift();
        lift.setId(101L);
        lift.setLiftType(LiftType.HOME);
        lift.setDriveType(DriveType.TRACTION);
        lift.setNumberOfFloors(5);
        lift.setCapacityInKg(400);
        lift.setCapacityInPersons(6);
        lift.setBrand("Apollo");
        lift.setLiftModel("A-100");
        lift.setInstallationType("RESIDENTIAL");
        lift.setYearOfInstallation(2020);
        lift.setSerialNumber("SN-123");
        lift.setDoorType(DoorType.AUTO);
        lift.setAmcContracts(List.of(buildAmcContract()));
        return lift;
    }

    private AmcContract buildAmcContract() {
        AmcContract contract = new AmcContract();
        contract.setId(77L);
        contract.setContractNumber("AMC-77");
        contract.setStatus(AmcStatus.ACTIVE);
        contract.setContractType(ContractType.GOLD);
        contract.setStartDate(LocalDate.of(2025, 1, 1));
        contract.setEndDate(LocalDate.of(2026, 12, 31));
        contract.setNextServiceDate(LocalDate.of(2026, 9, 15));
        contract.setTotalServices(12);
        contract.setCompletedServices(3);
        return contract;
    }
}
