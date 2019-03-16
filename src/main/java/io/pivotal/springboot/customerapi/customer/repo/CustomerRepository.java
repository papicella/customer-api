package io.pivotal.springboot.customerapi.customer.repo;

import io.pivotal.springboot.customerapi.customer.domain.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RestResource;

import java.util.List;

public interface CustomerRepository extends JpaRepository <Customer, Long> {
    @RestResource(path = "customerName", rel = "customerName")
    List<Customer> findByNameIgnoreCase(@Param("q") String name);

    @RestResource(path = "customerNameContains", rel = "customerNameContains")
    List<Customer> findByNameContainsIgnoreCase(@Param("q") String name);

    @Query(value ="select c from Customer c where c.status = :status")
    List<Customer> findByStatus(@Param("status") String status);
}
