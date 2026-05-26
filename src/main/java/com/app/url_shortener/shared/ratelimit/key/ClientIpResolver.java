package com.app.url_shortener.shared.ratelimit.key;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class ClientIpResolver {

  private static final String UNKNOWN_CLIENT_IP = "unknown-client-ip";
  private static final String X_FORWARDED_FOR = "X-Forwarded-For";
  private static final String X_REAL_IP = "X-Real-IP";

  public String resolve(HttpServletRequest request) {
    if (request == null) {
      return UNKNOWN_CLIENT_IP;
    }

    String forwardedIp = firstValidIpFromForwardedFor(request.getHeader(X_FORWARDED_FOR));
    if (forwardedIp != null) {
      return forwardedIp;
    }

    String realIp = normalizeAndValidateIp(request.getHeader(X_REAL_IP));
    if (realIp != null) {
      return realIp;
    }

    String remoteAddr = normalizeAndValidateIp(request.getRemoteAddr());
    if (remoteAddr != null) {
      return remoteAddr;
    }

    return UNKNOWN_CLIENT_IP;
  }

  private String firstValidIpFromForwardedFor(String forwardedFor) {
    if (forwardedFor == null || forwardedFor.isBlank()) {
      return null;
    }

    return Arrays.stream(forwardedFor.split(","))
        .map(this::normalizeAndValidateIp)
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(null);
  }

  private String normalizeAndValidateIp(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }

    String normalized = value.trim();
    return IpAddressValidator.isValidIp(normalized) ? normalized : null;
  }
}
