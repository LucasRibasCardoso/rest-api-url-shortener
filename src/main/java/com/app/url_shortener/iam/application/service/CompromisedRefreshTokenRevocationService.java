package com.app.url_shortener.iam.application.service;

import java.util.UUID;

public interface CompromisedRefreshTokenRevocationService {
  void revokeAllTokensDueToCompromise(UUID userId);
}
