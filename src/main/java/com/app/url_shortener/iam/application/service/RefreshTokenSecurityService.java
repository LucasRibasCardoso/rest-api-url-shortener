package com.app.url_shortener.iam.application.service;

import java.util.UUID;

public interface RefreshTokenSecurityService {
  void revokeAllTokensDueToCompromise(UUID userId);
}
