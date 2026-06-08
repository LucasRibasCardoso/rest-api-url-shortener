package com.app.url_shortener.iam.presentation.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResendVerificationRequestDto(@NotBlank @Email @Size(max = 180) String email) {}
