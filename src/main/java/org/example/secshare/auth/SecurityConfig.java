package org.example.secshare.auth;

import org.example.secshare.config.RateLimitFilter;
import org.example.secshare.config.RateLimiter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;
    private final RateLimiter rateLimiter;
    private final boolean rateLimitEnabled;

    public SecurityConfig(JwtAuthenticationFilter jwtFilter,
                          RateLimiter rateLimiter,
                          @Value("${app.ratelimit.enabled:true}") boolean rateLimitEnabled) {
        this.jwtFilter = jwtFilter;
        this.rateLimiter = rateLimiter;
        this.rateLimitEnabled = rateLimitEnabled;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())

                .sessionManagement(sm ->
                        sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/health", "/healthz").permitAll()
                    .requestMatchers("/api/auth/register", "/api/auth/login", "/api/auth/verify").permitAll()
                    .requestMatchers("/api/info").permitAll()
                    .requestMatchers("/api/public/shares/**").permitAll()
                    .requestMatchers("/", "/index.html", "/guide.html", "/share.html").permitAll()
                    .requestMatchers("/s/**").permitAll()
                    .requestMatchers("/favicon.ico", "/**/*.css", "/**/*.js", "/**/*.png", "/**/*.jpg", "/**/*.svg").permitAll()
                    .requestMatchers("/error").permitAll()
                    .requestMatchers("/api/files/all").hasRole("ADMIN")

                    .anyRequest().authenticated()
                )

                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new RateLimitFilter(rateLimiter, rateLimitEnabled), JwtAuthenticationFilter.class)

                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}