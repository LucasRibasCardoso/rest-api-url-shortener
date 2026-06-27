package com.app.url_shortener.iam.presentation.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequestDto(
    @NotBlank @Size(min = 3, max = 120) String name,
    @NotBlank @Email @Size(max = 180) String email,
    @NotBlank @Size(min = 6, max = 128) String password) {

  @Override
  public String toString() {
    return "RegisterRequestDto{"
        + "name='"
        + name
        + '\''
        + ", email='"
        + email
        + '\''
        + ", password='[REDACTED]'"
        + '}';
  }
}
