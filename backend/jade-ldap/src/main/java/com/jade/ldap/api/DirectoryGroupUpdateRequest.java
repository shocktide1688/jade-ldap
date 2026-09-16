package com.jade.ldap.api;

import jakarta.validation.constraints.Size;

public record DirectoryGroupUpdateRequest(@Size(max = 256) String description) {
}
