package com.paytm.exercise.wallet.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, CorrelationFilter correlationFilter,
                                            TokenAuthenticationFilter tokenFilter, SecurityProblemWriter problems) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/prometheus", "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                problems.write(response, HttpStatus.UNAUTHORIZED, "unauthorized", "A bearer token is required."))
                        .accessDeniedHandler((request, response, exception) ->
                                problems.write(response, HttpStatus.FORBIDDEN, "forbidden", "The authenticated user is not allowed to perform this operation.")))
                .addFilterBefore(correlationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(tokenFilter, CorrelationFilter.class)
                .build();
    }
}

