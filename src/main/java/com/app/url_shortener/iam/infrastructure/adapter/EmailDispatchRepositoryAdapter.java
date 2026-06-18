package com.app.url_shortener.iam.infrastructure.adapter;

import com.app.url_shortener.iam.application.port.output.EmailDispatchRepositoryPort;
import com.app.url_shortener.iam.domain.model.EmailDispatch;
import com.app.url_shortener.iam.infrastructure.entity.EmailDispatchEntity;
import com.app.url_shortener.iam.infrastructure.mapper.EmailDispatchPersistenceMapper;
import com.app.url_shortener.iam.infrastructure.repository.EmailDispatchJpaRepository;
import com.app.url_shortener.shared.database.DataIntegrityExceptionTranslator;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class EmailDispatchRepositoryAdapter implements EmailDispatchRepositoryPort {

  private final EmailDispatchPersistenceMapper mapper;
  private final EmailDispatchJpaRepository emailDispatchJpaRepository;
  private final DataIntegrityExceptionTranslator dataIntegrityExceptionTranslator;

  @Override
  public EmailDispatch save(EmailDispatch dispatch) {
    try {
      EmailDispatchEntity entity = mapper.toEntity(dispatch);
      EmailDispatchEntity savedEntity = emailDispatchJpaRepository.saveAndFlush(entity);
      return mapper.toDomain(savedEntity);
    } catch (DataIntegrityViolationException exception) {
      throw dataIntegrityExceptionTranslator.translate(exception);
    }
  }

  @Override
  public Optional<EmailDispatch> findByEventId(UUID eventId) {
    return emailDispatchJpaRepository.findByEventId(eventId).map(mapper::toDomain);
  }

  @Override
  public Optional<EmailDispatch> findLatestByVerificationTokenId(UUID verificationTokenId) {
    return emailDispatchJpaRepository
        .findFirstByVerificationTokenIdOrderByCreatedAtDescIdDesc(verificationTokenId)
        .map(mapper::toDomain);
  }

  @Override
  @Transactional
  public boolean markAsAccepted(UUID dispatchId, String providerMessageId, Instant now) {
    return emailDispatchJpaRepository.markAsAccepted(dispatchId, providerMessageId, now) == 1;
  }

  @Override
  @Transactional
  public boolean markAsFailed(UUID dispatchId, String errorCode, String errorMessage, Instant now) {
    return emailDispatchJpaRepository.markAsFailed(dispatchId, errorCode, errorMessage, now) == 1;
  }

  @Override
  @Transactional
  public boolean markAsSendingIfAvailable(
      UUID dispatchId, Instant now, Instant staleSendingThreshold) {
    return emailDispatchJpaRepository.markAsSendingIfAvailable(
            dispatchId, now, staleSendingThreshold)
        == 1;
  }
}
