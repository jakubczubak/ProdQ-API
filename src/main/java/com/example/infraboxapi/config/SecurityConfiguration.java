package com.example.infraboxapi.config;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfiguration {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final AuthenticationProvider authenticationProvider;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        // AUTH
                        .requestMatchers("/").permitAll()
                        .requestMatchers("/api/va/auth/**").permitAll()
                        .requestMatchers("/material_reports/**").permitAll() // Access to old PDF reports
                        .requestMatchers("/api/material_reports/**").permitAll() // Access to new reports in the database
                        .requestMatchers("/tool_reports/**").permitAll() // Access to PDF reports
                        .requestMatchers("/accessorie_reports/**").permitAll() // Access to PDF reports

                        // MATERIAL TYPE
                        .requestMatchers("/api/material_type/all").hasAnyAuthority("ADMIN", "USER")
                        .requestMatchers("/api/material_type/create").hasAuthority("ADMIN")
                        .requestMatchers("/api/material_type/update").hasAuthority("ADMIN")
                        .requestMatchers("/api/material_type/delete/*").hasAuthority("ADMIN")

                        // MATERIAL GROUP
                        .requestMatchers("/api/material_group/get/*").hasAnyAuthority("ADMIN", "USER")
                        .requestMatchers("/api/material_group/get").hasAnyAuthority("ADMIN", "USER")
                        .requestMatchers("/api/material_group/create").hasAuthority("ADMIN")
                        .requestMatchers("/api/material_group/update").hasAuthority("ADMIN")
                        .requestMatchers("/api/material_group/delete/*").hasAuthority("ADMIN")

                        // MATERIAL
                        .requestMatchers("/api/material/get/*").hasAnyAuthority("ADMIN", "USER")
                        .requestMatchers("/api/material/get").hasAnyAuthority("ADMIN", "USER")
                        .requestMatchers("/api/material/create").hasAuthority("ADMIN")
                        .requestMatchers("/api/material/update").hasAuthority("ADMIN")
                        .requestMatchers("/api/material/delete/*").hasAuthority("ADMIN")

                        // TOOL GROUP
                        .requestMatchers("/api/tool_group/get/*").hasAnyAuthority("ADMIN", "USER")
                        .requestMatchers("/api/tool_group/get").hasAnyAuthority("ADMIN", "USER")
                        .requestMatchers("/api/tool_group/create").hasAuthority("ADMIN")
                        .requestMatchers("/api/tool_group/update").hasAuthority("ADMIN")
                        .requestMatchers("/api/tool_group/delete/*").hasAuthority("ADMIN")

                        // TOOL
                        .requestMatchers("/api/tool/get/*").hasAnyAuthority("ADMIN", "USER")
                        .requestMatchers("/api/tool/get").hasAnyAuthority("ADMIN", "USER")
                        .requestMatchers("/api/tool/create").hasAuthority("ADMIN")
                        .requestMatchers("/api/tool/update").hasAuthority("ADMIN")
                        .requestMatchers("/api/tool/delete/*").hasAuthority("ADMIN")

                        // CALCULATION
                        .requestMatchers("/api/calculation/all").hasAnyAuthority("ADMIN", "USER")
                        .requestMatchers("/api/calculation/get/*").hasAnyAuthority("ADMIN", "USER")
                        .requestMatchers("/api/calculation/add").hasAuthority("ADMIN")
                        .requestMatchers("/api/calculation/update").hasAuthority("ADMIN")
                        .requestMatchers("/api/calculation/delete/*").hasAuthority("ADMIN")

                        // ORDER
                        .requestMatchers("/api/order/all").hasAnyAuthority("ADMIN", "USER")
                        .requestMatchers("/api/order/get/*").hasAnyAuthority("ADMIN", "USER")
                        .requestMatchers("/api/order/add").hasAuthority("ADMIN")
                        .requestMatchers("/api/order/update").hasAuthority("ADMIN")
                        .requestMatchers("/api/order/delete/*").hasAuthority("ADMIN")

                        // RECYCLING
                        .requestMatchers("/api/recycling/all").hasAnyAuthority("ADMIN", "USER")
                        .requestMatchers("/api/recycling/get/*").hasAnyAuthority("ADMIN", "USER")
                        .requestMatchers("/api/recycling/add").hasAuthority("ADMIN")
                        .requestMatchers("/api/recycling/update").hasAuthority("ADMIN")
                        .requestMatchers("/api/recycling/delete/*").hasAuthority("ADMIN")

                        // CONTACT (SUPPLIER)
                        .requestMatchers("/api/supplier/all").hasAnyAuthority("ADMIN", "USER")
                        .requestMatchers("/api/supplier/get/*").hasAnyAuthority("ADMIN", "USER")
                        .requestMatchers("/api/supplier/create").hasAuthority("ADMIN")
                        .requestMatchers("/api/supplier/update").hasAuthority("ADMIN")
                        .requestMatchers("/api/supplier/delete/*").hasAuthority("ADMIN")

                        // USER
                        .requestMatchers("/api/user/all").hasAnyAuthority("ADMIN", "USER")
                        .requestMatchers("/api/user/get/*").hasAnyAuthority("ADMIN", "USER")
                        .requestMatchers("/api/user/register").hasAuthority("ADMIN")
                        .requestMatchers("/api/user/update").hasAuthority("ADMIN")
                        .requestMatchers("/api/user/update/user_account").hasAuthority("ADMIN")
                        .requestMatchers("/api/user/manageUser/*/*").hasAuthority("ADMIN")
                        .requestMatchers("/api/user/delete/*").hasAuthority("ADMIN")

                        // DEPARTMENT COST
                        .requestMatchers("/api/department_cost/get").hasAnyAuthority("ADMIN", "USER")
                        .requestMatchers("/api/department_cost/update").hasAuthority("ADMIN")

                        // ACCESSORIES GROUP
                        .requestMatchers("/api/accessorie/get").hasAnyAuthority("ADMIN", "USER")
                        .requestMatchers("/api/accessorie/create").hasAuthority("ADMIN")
                        .requestMatchers("/api/accessorie/update").hasAuthority("ADMIN")
                        .requestMatchers("/api/accessorie/delete/*").hasAuthority("ADMIN")

                        // ACCESSORIES
                        .requestMatchers("/api/accessorie/item/get").hasAnyAuthority("ADMIN", "USER")
                        .requestMatchers("/api/accessorie/item/create").hasAuthority("ADMIN")
                        .requestMatchers("/api/accessorie/item/update").hasAuthority("ADMIN")
                        .requestMatchers("/api/accessorie/item/delete/*").hasAuthority("ADMIN")

                        // PRODUCTION QUEUE ITEM
                        .requestMatchers(HttpMethod.GET, "/api/production-queue-item", "/api/production-queue-item/*", "/api/production-queue-item/files/*", "/api/sync-with-machine/*").hasAnyAuthority("ADMIN", "USER")
                        .requestMatchers(HttpMethod.POST, "/api/production-queue-item/add", "/api/sync-with-machine").hasAuthority("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/production-queue-item/*", "/api/production-queue-item/update-order").hasAuthority("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/production-queue-item/*", "/api/production-queue-item/files/*").hasAuthority("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/production-queue-item/*/toggle-complete").hasAuthority("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/production-queue-item/move-completed/*").hasAuthority("ADMIN")

                        // MACHINE
                        .requestMatchers("/api/machine/{id}").hasAnyAuthority("ADMIN", "USER") // GET by ID
                        .requestMatchers("/api/machine").hasAnyAuthority("ADMIN", "USER") // GET all
                        .requestMatchers("/api/machine/{id}/image").hasAnyAuthority("ADMIN", "USER") // GET image
                        .requestMatchers("/api/machine/available-locations").hasAnyAuthority("ADMIN", "USER") // GET available locations
                        .requestMatchers("/api/machine/directory-structure-hash").hasAnyAuthority("ADMIN", "USER") // GET directory structure hash
                        .requestMatchers("/api/machine/{id}/download-programs").hasAnyAuthority("ADMIN", "USER") // GET download programs
                        .requestMatchers("/api/machine/add").hasAuthority("ADMIN") // POST add
                        .requestMatchers(HttpMethod.PUT, "/api/machine/{id}").hasAuthority("ADMIN") // PUT update
                        .requestMatchers(HttpMethod.DELETE, "/api/machine/{id}").hasAuthority("ADMIN") // DELETE

                        // DIRECTORY CLEANUP
                        .requestMatchers("/api/cleanup/all").hasAuthority("ADMIN")
                        .requestMatchers("/api/cleanup/machine/*").hasAuthority("ADMIN")

                        // OTHER
                        .anyRequest().authenticated()
                )
                .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authenticationProvider(authenticationProvider)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling()
                .accessDeniedHandler(accessDeniedHandler());

        return http.build();
    }

    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.getWriter().write("Access Denied (403 Forbidden) - You don't have the required permissions.");
        };
    }
}