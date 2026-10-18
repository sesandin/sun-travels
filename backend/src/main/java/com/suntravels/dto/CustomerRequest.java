package com.suntravels.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class CustomerRequest {
    @NotBlank(message = "Customer name is required")
    @Size(max = 150, message = "Name must be at most 150 characters")
    private String name;

    @Size(max = 20, message = "Phone must be at most 20 characters")
    @Pattern(regexp = "^\\s*$|^(?=.*\\d)[+0-9(). \\-]{5,20}$", message = "Phone must use 5 to 20 international phone characters and include a digit")
    private String phone;

    @Size(max = 150, message = "Email must be at most 150 characters")
    @Email(message = "Provide a valid email address")
    private String email;

    // Checks that at least one customer contact method is supplied.
    @AssertTrue(message = "Provide at least a phone number or email address")
    public boolean isContactProvided() {
        return (phone != null && !phone.isBlank()) || (email != null && !email.isBlank());
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
