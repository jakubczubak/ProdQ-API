package com.example.infraboxapi.config;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.access.AccessDeniedHandler;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfiguration {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final AuthenticationProvider authenticationProvider;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors()
                .and()
                .csrf()
                .disable()
                .authorizeHttpRequests()
                .requestMatchers("/api/va/auth/**").permitAll()
                //MATERIAL GROUP
                .requestMatchers("/api/material_group/create").hasRole("ADMIN")
                .requestMatchers("/api/material_group/update").hasAnyRole("ADMIN", "USER")
                .requestMatchers("/api/material_group/delete/*").hasRole("ADMIN")
                .requestMatchers("/api/material_group/get/*").hasAnyRole("ADMIN", "USER")
                .requestMatchers("/api/material_group/get").hasAnyRole("ADMIN", "USER")
                //MATERIAL
                .requestMatchers("/api/material/create").hasRole("ADMIN")
                .requestMatchers("/api/material/update").hasAnyRole("ADMIN", "USER")
                .requestMatchers("/api/material/delete/*").hasRole("ADMIN")
                //TOOL GROUP
                .requestMatchers("/api/tool_group/get").hasAnyRole("ADMIN", "USER")
                .requestMatchers("/api/tool_group/update").hasAnyRole("ADMIN", "USER")
                .requestMatchers("/api/tool_group/create").hasRole("ADMIN")
                .requestMatchers("/api/tool_group/delete/*").hasRole("ADMIN")
                .requestMatchers("/api/tool_group/get/*").hasAnyRole("ADMIN", "USER")
                //TOOL
                .requestMatchers("/api/tool/create").hasRole("ADMIN")
                .requestMatchers("/api/tool/update").hasAnyRole("ADMIN", "USER")
                .requestMatchers("/api/tool/delete/*").hasRole("ADMIN")


                .anyRequest().authenticated()
                .and()
                .sessionManagement()
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                .and()
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
