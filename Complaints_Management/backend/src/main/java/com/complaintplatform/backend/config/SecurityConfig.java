package com.complaintplatform.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {

        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource())) //Allow frontend to call backend APIs
                .authorizeHttpRequests(auth -> auth
                        // Public pages and assets
                        .requestMatchers("/", "/index.html", "/*.html", "/*.png", "/*.jpg", "/assets/**", "/login", "/login.html", "/register.html").permitAll()

                        // Auth API
                        .requestMatchers("/api/auth/**").permitAll()

                        // Protected APIs
                        .requestMatchers("/api/admin/**").permitAll()
                        .requestMatchers("/api/super-admin/**").permitAll()
                        .requestMatchers("/api/complaints/**").permitAll()
                        .requestMatchers("/api/public/**").permitAll()
                        .requestMatchers("/api/users/**").permitAll()
                        .requestMatchers("/api/companies/**", "/api/departments/**", "/api/notifications/**", "/api/internal-notes/**", "/api/repair/**", "/uploads/**").permitAll()
                        .requestMatchers("/api/internal-notes/**", "/api/internal-notes").permitAll()
                        .requestMatchers("/api/repair/**", "/api/repair").permitAll()

                        .anyRequest().authenticated()
                )
                .formLogin(form -> form.loginPage("/login.html").permitAll())
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login.html?logout=true")
                        .permitAll()
                );

        return http.build();
    }

    @Bean
    public UrlBasedCorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        config.setAllowedOriginPatterns(Arrays.asList("*")); 
        config.setAllowedHeaders(Arrays.asList("Origin", "Content-Type", "Accept", "Authorization"));
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}


