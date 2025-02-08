package com.example.infraboxapi.config;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

                        //AUTH
                        .requestMatchers("/").permitAll()
                        .requestMatchers("/api/va/auth/**").permitAll()
                        .requestMatchers("/reports/**").permitAll()// Dostęp do raportów PDF

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

                        // OTHER
                        .anyRequest().authenticated()
                )
                .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authenticationProvider(authenticationProvider)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling()
                .accessDeniedHandler(accessDeniedHandler());  // Dodanie obsługi dostępu zabronionego

        return http.build();
    }

    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            // Tutaj możesz dostosować odpowiedź HTTP dla dostępu zabronionego
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.getWriter().write("Access Denied (403 Forbidden) - You don't have the required permissions.");
        };
    }
}
