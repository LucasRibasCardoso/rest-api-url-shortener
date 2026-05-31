package com.app.url_shortener.url.presentation.validator.imp;

import com.app.url_shortener.url.presentation.validator.ValidHttpUrl;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.net.URI;

public class HttpUrlValidator implements ConstraintValidator<ValidHttpUrl, String> {

  @Override
  public boolean isValid(String value, ConstraintValidatorContext context) {
    if (value == null || value.isBlank()) return true; // @NotBlank trata sozinho

    try {
      URI uri = URI.create(value.trim());

      return hasHttpScheme(uri)
          && hasHost(uri)
          && hasAuthority(uri)
          && hasValidPort(uri)
          && hasNoUserInfo(uri);
    } catch (Exception ex) {
      return false;
    }
  }

  private boolean hasHttpScheme(URI uri) {
    String scheme = uri.getScheme();
    return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
  }

  private boolean hasAuthority(URI uri) {
    String authority = uri.getAuthority();
    return authority != null && !authority.isBlank();
  }

  private boolean hasHost(URI uri) {
    String host = uri.getHost();
    return host != null && !host.isBlank();
  }

  private boolean hasNoUserInfo(URI uri) {
    String userInfo = uri.getUserInfo();
    return userInfo == null;
  }

  private boolean hasValidPort(URI uri) {
    String authority = uri.getRawAuthority();

    if (!isExplicitPort(authority)) {
      return true;
    }

    int port = uri.getPort();
    return port >= 1 && port <= 65535;
  }

  private boolean isExplicitPort(String authority) {
    if (authority == null || authority.isBlank()) return false;

    return isIpv6Authority(authority)
        ? isExplicitPortInIpv6Authority(authority)
        : isExplicitPortInRegularAuthority(authority);
  }

  private boolean isIpv6Authority(String authority) {
    // Exemplo de autoridade IPv6: [2001:db8::1]:8080 ou [2001:db8::1]
    return authority.startsWith("[");
  }

  private boolean isExplicitPortInIpv6Authority(String authority) {
    int closingBracketIndex = authority.indexOf(']');

    return closingBracketIndex >= 0
        && authority.length() > closingBracketIndex + 1
        && authority.charAt(closingBracketIndex + 1) == ':';
  }

  private boolean isExplicitPortInRegularAuthority(String authority) {
    // Exemplo de autoridade regular: example.com:8080 ou example.com
    return authority.contains(":");
  }
}
