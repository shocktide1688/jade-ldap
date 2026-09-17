package com.jade.ldap.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DirectoryUserRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{1,64}") String uid,
        @NotBlank @Size(max = 128) String commonName,
        @NotBlank @Size(max = 128) String surname,
        @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 8, max = 256) String password
) {
}
