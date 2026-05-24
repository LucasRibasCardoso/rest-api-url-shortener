package com.app.url_shortener.iam.application.command;

import com.app.url_shortener.iam.domain.valueobject.VerificationCode;

import java.util.Locale;

public record VerifyEmailCommand(
        String email,
        VerificationCode code) {

    public VerifyEmailCommand {
        email = email != null ? email.trim().toLowerCase(Locale.ROOT) : null;
    }

    @Override
    public String toString() {
        return "VerifyEmailCommand{"
                + "email='"
                + email
                + '\''
                + ", code='[REDACTED]'"
                + '}';
    }
}
