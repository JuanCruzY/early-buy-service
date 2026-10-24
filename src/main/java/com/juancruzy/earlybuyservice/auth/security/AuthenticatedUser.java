package com.juancruzy.earlybuyservice.auth.security;

import java.security.Principal;
import java.util.Set;

public record AuthenticatedUser(String id, Set<String> permissions) implements Principal {

    public AuthenticatedUser {
        permissions = Set.copyOf(permissions);
    }

    @Override
    public String getName() {
        return id;
    }
}
