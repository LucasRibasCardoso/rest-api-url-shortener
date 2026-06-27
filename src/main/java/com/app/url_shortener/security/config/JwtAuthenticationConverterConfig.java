package com.app.url_shortener.security.config;

import com.app.url_shortener.iam.domain.enums.PlanType;
import com.app.url_shortener.iam.domain.enums.UserStatus;
import com.app.url_shortener.security.principal.UserPrincipal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

@Configuration
public class JwtAuthenticationConverterConfig {

  @Bean
  public Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter() {
    JwtGrantedAuthoritiesConverter grantedAuthoritiesConverter =
        new JwtGrantedAuthoritiesConverter();

    grantedAuthoritiesConverter.setAuthoritiesClaimName("authorities");
    grantedAuthoritiesConverter.setAuthorityPrefix("");

    JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
    jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesConverter);

    return jwt -> {
      AbstractAuthenticationToken token = jwtAuthenticationConverter.convert(jwt);
      if (token == null) {
        return null;
      }

      Collection<GrantedAuthority> authorities = token.getAuthorities();

      UUID id = UUID.fromString(jwt.getSubject());
      String planParam = jwt.getClaimAsString("plan");
      PlanType plan = planParam != null ? PlanType.valueOf(planParam.toUpperCase()) : PlanType.FREE;

      UserPrincipal userPrincipal =
          new UserPrincipal(
              id,
              null, // name
              null, // email
              null, // passwordHash
              plan,
              UserStatus.ACTIVE, // Assuming active for stateless validation
              List.copyOf(authorities));

      return new UsernamePasswordAuthenticationToken(userPrincipal, jwt, authorities);
    };
  }
}
