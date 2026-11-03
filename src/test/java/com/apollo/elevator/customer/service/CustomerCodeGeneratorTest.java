package com.apollo.elevator.customer.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CustomerCodeGeneratorTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', nullValues = "NULL", value = {
            "TEJAS V                            | 1    | APL-ELV-TEV-001",
            "SIDDAGANGA INSTITUTE OF TECHNOLOGY | 2    | APL-ELV-SIT-002",
            "BHARGAV                            | 3    | APL-ELV-BHA-003",
            "Sri Sai Residency                  | 12   | APL-ELV-SSR-012",
            "The Prestige Group                 | 7    | APL-ELV-PRG-007",
            "M. G. Road Apartments Pvt Ltd      | 45   | APL-ELV-MGR-045",
            "R & K Towers                       | 9    | APL-ELV-RKT-009",
            "V Tejas                            | 4    | APL-ELV-VTE-004",
            "Jo                                 | 5    | APL-ELV-JOX-005",
            "Dr. Ana                            | 6    | APL-ELV-DRA-006",
            "Ramé Builders                      | 8    | APL-ELV-RAB-008",
            "NULL                               | 10   | APL-ELV-XXX-010",
            "Apollo                             | 1234 | APL-ELV-APO-1234",
    })
    void generatesCodeFromNameInitialsAndId(String name, long id, String expected) {
        assertThat(CustomerCodeGenerator.generate(name, id)).isEqualTo(expected);
    }
}
