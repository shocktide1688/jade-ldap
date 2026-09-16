package com.jade.ldap.api;

import java.util.List;

public record DirectoryGroupView(
        String dn,
        String name,
        String description,
        List<String> members
) {
}
