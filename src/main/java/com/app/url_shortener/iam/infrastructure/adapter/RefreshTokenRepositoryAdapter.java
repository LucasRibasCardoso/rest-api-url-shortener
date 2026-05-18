package com.app.url_shortener.iam.infrastructure.adapter;

import com.app.url_shortener.iam.application.port.output.RefreshTokenRepositoryPort;
import com.app.url_shortener.iam.domain.model.RefreshToken;
import com.app.url_shortener.iam.infrastructure.persistence.entity.RefreshTokenEntity;
import com.app.url_shortener.iam.infrastructure.persistence.mapper.RefreshTokenPersistenceMapper;
import com.app.url_shortener.iam.infrastructure.persistence.repository.RefreshTokenJpaRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RefreshTokenRepositoryAdapter implements RefreshTokenRepositoryPort {

  private final EntityManager entityManager;
  private final RefreshTokenJpaRepository refreshTokenJpaRepository;
  private final RefreshTokenPersistenceMapper refreshTokenPersistenceMapper;

  @Override
  public void save(RefreshToken refreshToken) {
    RefreshTokenEntity refreshTokenEntity = refreshTokenPersistenceMapper.toEntity(refreshToken);
    refreshTokenJpaRepository.save(refreshTokenEntity);
  }

  @Override
  public Optional<RefreshToken> findByTokenHash(String tokenHash) {
    return refreshTokenJpaRepository
        .findByTokenHash(tokenHash)
        .map(refreshTokenPersistenceMapper::toDomain);
  }

  @Override
  public void revokeAllTokensForUser(UUID userId) {
    refreshTokenJpaRepository.revokeAllActiveTokensByUserId(userId, Instant.now());
  }

  @Override
  public void revokeActiveTokenByHash(String tokenHash) {
    refreshTokenJpaRepository.revokeActiveTokenByHash(tokenHash, Instant.now());
  }

  @Override
  public int markTokenAsRotatedIfActive(String oldTokenHash, Instant rotatedAt, UUID replacedByTokenId) {
    RefreshTokenEntity replacedByTokenReference =
        entityManager.getReference(RefreshTokenEntity.class, replacedByTokenId);

    return refreshTokenJpaRepository.markTokenAsRotatedIfActive(oldTokenHash, rotatedAt, replacedByTokenReference);
  }
}
