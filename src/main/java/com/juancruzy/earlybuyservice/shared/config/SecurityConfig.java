package com.juancruzy.earlybuyservice.shared.config;

import java.util.List;

import com.juancruzy.earlybuyservice.auth.security.AuthAuthenticationProvider;
import com.juancruzy.earlybuyservice.auth.security.BearerAuthenticationFilter;
import com.juancruzy.earlybuyservice.auth.security.SecurityErrorHandler;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public AuthenticationManager authenticationManager(AuthAuthenticationProvider provider) {
        return new ProviderManager(provider);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, AuthenticationManager authenticationManager,
                                                  SecurityErrorHandler errorHandler,
                                                  @Value("${earlybuy.cors.allowed-origin}") String allowedOrigin)
            throws Exception {
        var subscriptions = PathPatternRequestMatcher.withDefaults().matcher("/v1/subscriptions/**");
        var filter = new BearerAuthenticationFilter(authenticationManager, errorHandler, subscriptions);
        var corsConfig = new CorsConfiguration();
        corsConfig.setAllowedOrigins(List.of(allowedOrigin));
        corsConfig.setAllowedMethods(List.of("GET", "POST", "DELETE"));
        corsConfig.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE));
        var corsSource = new UrlBasedCorsConfigurationSource();
        corsSource.registerCorsConfiguration("/v1/subscriptions/**", corsConfig);

        return http
                .cors(cors -> cors.configurationSource(corsSource))
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(errors -> errors.authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler))
                .authorizeHttpRequests(requests -> requests
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**",
                                "/actuator/health", "/actuator/health/**", "/error").permitAll()
                        .requestMatchers(subscriptions).hasAuthority("user")
                        .anyRequest().denyAll())
                .addFilterBefore(filter, AnonymousAuthenticationFilter.class)
                .build();
    }
}
