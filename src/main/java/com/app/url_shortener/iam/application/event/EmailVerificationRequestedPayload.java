package com.app.url_shortener.iam.application.event;

import java.util.UUID;

public record EmailVerificationRequestedPayload(
    UUID userId,
    String email,
    EmailVerificationReason reason) {}
