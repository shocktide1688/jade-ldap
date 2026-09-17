package com.jade.ldap.api;

import java.util.List;

public record DirectoryTreeNode(
        String type,
        String dn,
        String name,
        List<DirectoryTreeNode> children
) {
}
