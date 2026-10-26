package org.example.teamflowbackend.security;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public class CurrentUser {

    public CurrentUser() {
    }


    public static Jwt jwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (!(authentication instanceof JwtAuthenticationToken jwtAuthenticationToken)
                || !authentication.isAuthenticated()) {
            throw new AuthenticationCredentialsNotFoundException(
                    "Authenticated JWT not found"
            );
        }

        return jwtAuthenticationToken.getToken();
    }

    public static String keycloakId() {
        return jwt().getSubject();
    }

    public static String email() {
        return jwt().getClaimAsString("email");
    }

    public static String displayName() {
        String name = jwt().getClaimAsString("name");

        if (name != null && !name.isBlank()) {
            return name;
        }

        String username = jwt().getClaimAsString("preferred_username");

        if (username != null && !username.isBlank()) {
            return username;
        }

        return email();
    }
}
