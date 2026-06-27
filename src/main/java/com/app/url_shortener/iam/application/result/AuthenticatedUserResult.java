package com.app.url_shortener.iam.application.result;

import java.util.List;
import java.util.UUID;

public record AuthenticatedUserResult(
    UUID id, String name, String email, List<String> roles, List<String> authorities, String plan) {
  public AuthenticatedUserResult {
    roles = List.copyOf(roles);
    authorities = List.copyOf(authorities);
  }
}
