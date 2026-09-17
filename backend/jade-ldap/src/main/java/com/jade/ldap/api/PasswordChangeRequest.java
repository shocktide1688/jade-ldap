package com.jade.ldap.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordChangeRequest(
        @NotBlank @Size(min = 8, max = 256) String password
) {
}
