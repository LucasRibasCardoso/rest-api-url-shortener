package com.app.url_shortener.iam.application.port.output;

import com.app.url_shortener.iam.domain.model.RefreshToken;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepositoryPort {

  void save(RefreshToken refreshToken);

  Optional<RefreshToken> findByTokenHash(String tokenHash);

  void revokeAllTokensForUser(UUID userId);

  void revokeActiveTokenByHash(String tokenHash);

  int markTokenAsRotatedIfActive(String oldTokenHash, Instant rotatedAt, UUID replacedByTokenId);
}
