package com.app.url_shortener.shared.infrastructure.idempotency.impl;

import com.app.url_shortener.security.principal.UserPrincipal;
import com.app.url_shortener.shared.ratelimit.key.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PrincipalScopeResolver {

  private static final String ANONYMOUS_USER = "anonymousUser";

  private final ClientIpResolver clientIpResolver;

  public String resolve(HttpServletRequest request) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

    if (authentication != null && authentication.isAuthenticated()) {
      Object principal = authentication.getPrincipal();

      if (principal instanceof UserPrincipal userPrincipal) {
        return userScope(userPrincipal.getId().toString());
      }

      if (principal instanceof Jwt jwt) {
        return userScope(jwt.getSubject());
      }

      if (authentication.getCredentials() instanceof Jwt jwt) {
        return userScope(jwt.getSubject());
      }

      String name = authentication.getName();
      if (name != null && !name.isBlank() && !ANONYMOUS_USER.equals(name)) {
        return resolveAuthenticationNameScope(name);
      }
    }

    return "ip:" + clientIpResolver.resolve(request);
  }

  private static String resolveAuthenticationNameScope(String name) {
    try {
      return userScope(UUID.fromString(name).toString());
    } catch (IllegalArgumentException ignored) {
      return "principal:" + name;
    }
  }

  private static String userScope(String userId) {
    return "user:" + userId;
  }
}
