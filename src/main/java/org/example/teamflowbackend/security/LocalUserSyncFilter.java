package org.example.teamflowbackend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class LocalUserSyncFilter extends OncePerRequestFilter {

    private final LocalUserIdentityService localUserIdentityService;

    public LocalUserSyncFilter(
            LocalUserIdentityService localUserIdentityService
    ) {
        this.localUserIdentityService = localUserIdentityService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if(authentication instanceof JwtAuthenticationToken jwtAuthenticationToken && authentication.isAuthenticated()) {
            localUserIdentityService.sync(jwtAuthenticationToken.getToken());
        }
        filterChain.doFilter(request, response);
    }
}
