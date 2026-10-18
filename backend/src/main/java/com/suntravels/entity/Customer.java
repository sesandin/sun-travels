package com.suntravels.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "customer")
public class Customer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "customer_id")
    private Integer customerId;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "email", length = 150)
    private String email;

    // Returns the customer id.
    public Integer getCustomerId() {
        return customerId;
    }

    // Returns the name.
    public String getName() {
        return name;
    }

    // Updates the name.
    public void setName(String name) {
        this.name = name;
    }

    // Returns the phone.
    public String getPhone() {
        return phone;
    }

    // Updates the phone.
    public void setPhone(String phone) {
        this.phone = phone;
    }

    // Returns the email.
    public String getEmail() {
        return email;
    }

    // Updates the email.
    public void setEmail(String email) {
        this.email = email;
    }
}
