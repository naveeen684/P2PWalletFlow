package com.paytm.exercise.wallet.security;

import com.paytm.exercise.wallet.application.AuthService;
import com.paytm.exercise.wallet.application.ApiException;
import com.paytm.exercise.wallet.domain.AuthenticatedPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class TokenAuthenticationFilter extends OncePerRequestFilter {
    private final AuthService authService;
    private final SecurityProblemWriter problems;

    public TokenAuthenticationFilter(AuthService authService, SecurityProblemWriter problems) {
        this.authService = authService;
        this.problems = problems;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null) {
            if (!header.startsWith("Bearer ") || header.length() <= 7) {
                problems.write(response, HttpStatus.UNAUTHORIZED, "invalid_token", "A valid bearer token is required.");
                return;
            }
            AuthenticatedPrincipal principal;
            try {
                principal = authService.authenticate(header.substring(7));
            } catch (ApiException exception) {
                problems.write(response, exception.status(), exception.code(), exception.getMessage());
                return;
            }
            WalletPrincipal securityPrincipal = new WalletPrincipal(principal.userId(), principal.externalId(), principal.isAdmin());
            var authentication = new UsernamePasswordAuthenticationToken(securityPrincipal, null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().name())));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        try {
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
