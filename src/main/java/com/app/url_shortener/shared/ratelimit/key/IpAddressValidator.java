package com.app.url_shortener.shared.ratelimit.key;

import org.apache.commons.validator.routines.InetAddressValidator;

public final class IpAddressValidator {

  private static final InetAddressValidator VALIDATOR = InetAddressValidator.getInstance();

  private IpAddressValidator() {}

  public static boolean isValidIp(String value) {
    if (value == null || value.isBlank()) {
      return false;
    }

    return VALIDATOR.isValidInet4Address(value) || VALIDATOR.isValidInet6Address(value);
  }

  public static boolean isValidIpv4(String value) {
    return value != null && VALIDATOR.isValidInet4Address(value.trim());
  }

  public static boolean isValidIpv6(String value) {
    return value != null && VALIDATOR.isValidInet6Address(value.trim());
  }
}
