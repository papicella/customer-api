package io.pivotal.springboot.customerapi.customer.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;

@Data
@AllArgsConstructor
@NoArgsConstructor
@ToString
@Entity
public class Customer {

    @Id @GeneratedValue (strategy = GenerationType.AUTO)
    private Long id;
    private String name;
    private String status;

    public Customer(String name, String status) {
        this.name = name;
        this.status = status;
    }
}
