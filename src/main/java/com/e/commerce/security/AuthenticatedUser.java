package com.e.commerce.security;

import com.e.commerce.enums.Role;

import java.util.UUID;

public record AuthenticatedUser(UUID id, String email, Role role) {
}
