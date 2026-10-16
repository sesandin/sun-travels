package com.suntravels.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Size(max = 100) String username,
        @NotBlank @Size(max = 200) String password
) {
    // The fixed marker is redaction text, not a credential; never include the password value here.
    @Override
    public String toString() {
        return "LoginRequest[username=" + username + ", password=[PROTECTED]]";
    }
}
