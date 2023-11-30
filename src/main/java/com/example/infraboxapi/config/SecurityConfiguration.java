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

                //AUTH
                .requestMatchers("/api/va/auth/**").permitAll()

                //MATERIAL GROUP,
                .requestMatchers("/api/material_group/create").hasAuthority("ADMIN")
                .requestMatchers("/api/material_group/update").hasAnyAuthority("ADMIN", "USER")
                .requestMatchers("/api/material_group/delete/*").hasAuthority("ADMIN")
                .requestMatchers("/api/material_group/get/*").hasAnyAuthority("ADMIN", "USER")
                .requestMatchers("/api/material_group/get").hasAnyAuthority("ADMIN", "USER")

                //MATERIAL
                .requestMatchers("/api/material/create").hasAuthority("ADMIN")
                .requestMatchers("/api/material/update").hasAnyAuthority("ADMIN", "USER")
                .requestMatchers("/api/material/delete/*").hasAuthority("ADMIN")

                //TOOL GROUP
                .requestMatchers("/api/tool_group/get").hasAnyAuthority("ADMIN", "USER")
                .requestMatchers("/api/tool_group/update").hasAnyAuthority("ADMIN", "USER")
                .requestMatchers("/api/tool_group/create").hasAuthority("ADMIN")
                .requestMatchers("/api/tool_group/delete/*").hasAuthority("ADMIN")
                .requestMatchers("/api/tool_group/get/*").hasAnyAuthority("ADMIN", "USER")

                //TOOL
                .requestMatchers("/api/tool/create").hasAuthority("ADMIN")
                .requestMatchers("/api/tool/update").hasAnyAuthority("ADMIN", "USER")
                .requestMatchers("/api/tool/delete/*").hasAuthority("ADMIN")

                //CALCULATION
                .requestMatchers("/api/calculation/all").hasAnyAuthority("ADMIN", "USER")
                .requestMatchers("/api/calculation/add").hasAnyAuthority("ADMIN")
                .requestMatchers("/api/calculation/update").hasAnyAuthority("ADMIN", "USER")
                .requestMatchers("/api/calculation/delete/*").hasAnyAuthority("ADMIN")

                //ORDER
                .requestMatchers("/api/order/all").hasAnyAuthority("ADMIN", "USER")
                .requestMatchers("/api/order/add").hasAnyAuthority("ADMIN")
                .requestMatchers("/api/order/update").hasAnyAuthority("ADMIN", "USER")
                .requestMatchers("/api/order/delete/*").hasAnyAuthority("ADMIN")

                //RECYCLING
                .requestMatchers("/api/recycling/all").hasAnyAuthority("ADMIN", "USER")
                .requestMatchers("/api/recycling/add").hasAnyAuthority("ADMIN")
                .requestMatchers("/api/recycling/update").hasAnyAuthority("ADMIN", "USER")
                .requestMatchers("/api/recycling/delete/*").hasAnyAuthority("ADMIN")

                //CONTACT
                .requestMatchers("/api/supplier/all").hasAnyAuthority("ADMIN", "USER")
                .requestMatchers("/api/supplier/create").hasAnyAuthority("ADMIN")
                .requestMatchers("/api/supplier/update").hasAnyAuthority("ADMIN", "USER")
                .requestMatchers("/api/supplier/delete/*").hasAnyAuthority("ADMIN")

                //USER
                .requestMatchers("/api/user/all").hasAnyAuthority("ADMIN", "USER")
                .requestMatchers("/api/user/register").hasAnyAuthority("ADMIN")
                .requestMatchers("/api/user/update").hasAnyAuthority("ADMIN", "USER")
                .requestMatchers("/api/user/update/user_account").hasAnyAuthority("ADMIN")
                .requestMatchers("/api/user/manageUser/*").hasAnyAuthority("ADMIN")
                .requestMatchers("/api/user/delete/*").hasAnyAuthority("ADMIN")

                //DEPARTMENT COST
                .requestMatchers("/api/department_cost/get").hasAnyAuthority("ADMIN", "USER")
                .requestMatchers("/api/department_cost/update").hasAnyAuthority("ADMIN")

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
