package com.app.url_shortener.security.config;

import com.app.url_shortener.security.exception.handler.CustomAccessDeniedHandler;
import com.app.url_shortener.security.exception.handler.CustomAuthenticationEntryPoint;
import com.app.url_shortener.shared.config.OpenApiProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public AuthenticationManager authenticationManager(
      AuthenticationConfiguration authenticationConfiguration) {
    return authenticationConfiguration.getAuthenticationManager();
  }

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      CustomAccessDeniedHandler accessDeniedHandler,
      CustomAuthenticationEntryPoint authenticationEntryPoint,
      ObjectProvider<OpenApiProperties> openApiPropertiesProvider,
      Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter) {

    boolean openApiEnabled = openApiPropertiesProvider.getIfAvailable(() -> new OpenApiProperties(false)).enabled();

    return http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth -> {
              auth.requestMatchers(
                      "/api/v1/auth/register",
                      "/api/v1/auth/verify-email",
                      "/api/v1/auth/login",
                      "/api/v1/auth/refresh",
                      "/api/v1/auth/resend-verification")
                  .permitAll()
                  .requestMatchers("/r/**")
                  .permitAll();

              if (openApiEnabled) {
                auth.requestMatchers(HttpMethod.GET, "/docs", "/docs/openapi.json").permitAll();
              }

              auth.requestMatchers(
                      "/docs",
                      "/docs/**",
                      "/docs.html",
                      "/v3/api-docs",
                      "/v3/api-docs/**",
                      "/swagger-ui.html",
                      "/swagger-ui/**")
                  .denyAll()
                  .requestMatchers("/api/v1/urls/**")
                  .authenticated()
                  .anyRequest()
                  .authenticated();
            })
        .oauth2ResourceServer(
            oauth2 ->
                oauth2
                    .authenticationEntryPoint(authenticationEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler)
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
        .build();
  }
}
