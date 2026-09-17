package com.jade.ldap.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DirectoryUserUpdateRequest(
        @NotBlank @Size(max = 128) String commonName,
        @NotBlank @Size(max = 128) String surname,
        @Email @Size(max = 254) String email
) {
}
