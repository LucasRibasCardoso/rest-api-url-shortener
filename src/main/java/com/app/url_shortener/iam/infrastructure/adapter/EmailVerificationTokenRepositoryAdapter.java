package com.app.url_shortener.iam.infrastructure.adapter;

import com.app.url_shortener.iam.application.port.output.EmailVerificationTokenRepositoryPort;
import com.app.url_shortener.iam.domain.model.EmailVerificationToken;
import com.app.url_shortener.iam.infrastructure.entity.EmailVerificationTokenEntity;
import com.app.url_shortener.iam.infrastructure.mapper.EmailVerificationTokenPersistenceMapper;
import com.app.url_shortener.iam.infrastructure.repository.EmailVerificationTokenJpaRepository;
import com.app.url_shortener.shared.database.DataIntegrityExceptionTranslator;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class EmailVerificationTokenRepositoryAdapter
    implements EmailVerificationTokenRepositoryPort {

  private final EmailVerificationTokenPersistenceMapper verificationTokenMapper;
  private final EmailVerificationTokenJpaRepository verificationTokenJpaRepository;
  private final DataIntegrityExceptionTranslator dataIntegrityExceptionTranslator;

  @Override
  public EmailVerificationToken save(EmailVerificationToken token) {
    try {
      EmailVerificationTokenEntity entity = verificationTokenMapper.toEntity(token);
      EmailVerificationTokenEntity entitySaved =
          verificationTokenJpaRepository.saveAndFlush(entity);
      return verificationTokenMapper.toDomain(entitySaved);
    } catch (DataIntegrityViolationException exception) {
      throw dataIntegrityExceptionTranslator.translate(exception);
    }
  }

  @Override
  public Optional<EmailVerificationToken> findById(UUID tokenId) {
    return verificationTokenJpaRepository.findById(tokenId).map(verificationTokenMapper::toDomain);
  }

  @Override
  public Optional<EmailVerificationToken> findOpenByUserIdAndEmail(UUID userId, String email) {
    return verificationTokenJpaRepository
        .findOpenByUserIdAndEmail(userId, email)
        .map(verificationTokenMapper::toDomain);
  }

  @Override
  public Optional<EmailVerificationToken> findActiveByUserIdAndEmail(
      UUID userId, String email, Instant now) {
    return verificationTokenJpaRepository
        .findActiveByUserIdAndEmail(userId, email, now)
        .map(verificationTokenMapper::toDomain);
  }

  @Override
  @Transactional
  public int revokeOpenByUserIdAndEmail(UUID userId, String email, Instant now) {
    return verificationTokenJpaRepository.revokeOpenByUserIdAndEmail(userId, email, now);
  }

  @Override
  @Transactional
  public boolean consumeIfActive(UUID tokenId, Instant now) {
    return verificationTokenJpaRepository.consumeIfActive(tokenId, now) == 1;
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean registerFailedAttempt(UUID tokenId, Instant now) {
    return verificationTokenJpaRepository.registerFailedAttempt(tokenId, now) == 1;
  }
}
