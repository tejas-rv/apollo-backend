package com.apollo.elevators.repository;

import com.apollo.elevators.customer.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerRepository
        extends JpaRepository<Customer, Long> {

    boolean existsByCustomerCode(String customerCode);

    @Query("""
            SELECT c
            FROM Customer c
            WHERE LOWER(c.customerCode) LIKE LOWER(CONCAT('%', :query, '%'))
               OR LOWER(c.customerName) LIKE LOWER(CONCAT('%', :query, '%'))
               OR LOWER(c.mobileNumber) LIKE LOWER(CONCAT('%', :query, '%'))
               OR LOWER(c.email) LIKE LOWER(CONCAT('%', :query, '%'))
               OR LOWER(c.city) LIKE LOWER(CONCAT('%', :query, '%'))
               OR LOWER(c.state) LIKE LOWER(CONCAT('%', :query, '%'))
            """)
    Page<Customer> searchCustomers(
            @Param("query") String query,
            Pageable pageable
    );
}
