package com.suntravels.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Size( max = 100 ) String username,
        @NotBlank @Size( max = 200 ) String password
)
{
    // Omit credentials from diagnostic output.
    @Override
    public String toString()
    {
        return "LoginRequest[username=" + username + "]";
    }
}
