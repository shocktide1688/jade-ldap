package com.jade.ldap.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DirectoryGroupRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{1,64}") String name,
        @Size(max = 256) String description
) {
}
