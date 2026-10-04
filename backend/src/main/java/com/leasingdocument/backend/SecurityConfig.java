package com.leasingdocument.backend;

import com.leasingdocument.backend.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter
            jwtAuthenticationFilter;


    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter
    ) {

        this.jwtAuthenticationFilter =
                jwtAuthenticationFilter;
    }


    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http
    ) throws Exception {


        http

                // =================================================
                // REST API - DISABLE CSRF
                // =================================================

                .csrf(
                        csrf ->
                                csrf.disable()
                )


                // =================================================
                // JWT STATELESS AUTHENTICATION
                // =================================================

                .sessionManagement(
                        session ->
                                session.sessionCreationPolicy(
                                        SessionCreationPolicy.STATELESS
                                )
                )


                // =================================================
                // ACCESS CONTROL
                // =================================================

                .authorizeHttpRequests(
                        auth -> auth


                                // =================================
                                // LOGIN DOES NOT REQUIRE JWT
                                // =================================

                                .requestMatchers(
                                        "/api/auth/login"
                                )
                                .permitAll()


                                // =================================
                                // SPRING ERROR ENDPOINT
                                // =================================

                                .requestMatchers(
                                        "/error"
                                )
                                .permitAll()


                                // =================================
                                // LOGOUT AND EVERYTHING ELSE
                                // REQUIRE VALID JWT
                                // =================================

                                .anyRequest()
                                .authenticated()
                )


                // =================================================
                // JWT FILTER
                // =================================================

                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                );


        return http.build();
    }


    // =============================================================
    // BCRYPT PASSWORD ENCODER
    // =============================================================

    @Bean
    public PasswordEncoder passwordEncoder() {

        return new BCryptPasswordEncoder();
    }
}