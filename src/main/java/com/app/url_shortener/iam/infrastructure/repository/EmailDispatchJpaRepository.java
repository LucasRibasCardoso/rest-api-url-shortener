package com.app.url_shortener.iam.infrastructure.repository;

import com.app.url_shortener.iam.infrastructure.entity.EmailDispatchEntity;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface EmailDispatchJpaRepository extends JpaRepository<EmailDispatchEntity, UUID> {

  Optional<EmailDispatchEntity> findByEventId(UUID eventId);

  Optional<EmailDispatchEntity> findFirstByVerificationTokenIdOrderByCreatedAtDescIdDesc(
      UUID verificationTokenId);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
              UPDATE EmailDispatchEntity dispatch
                  SET dispatch.status = com.app.url_shortener.iam.domain.enums.EmailDispatchStatus.SENDING,
                      dispatch.sendingStartedAt = :now,
                      dispatch.sendAttempts = dispatch.sendAttempts + 1,
                      dispatch.failedAt = null,
                      dispatch.lastErrorCode = null ,
                      dispatch.lastErrorMessage = null,
                      dispatch.updatedAt = :now
                  WHERE dispatch.id = :dispatchId
                      AND dispatch.status <> com.app.url_shortener.iam.domain.enums.EmailDispatchStatus.ACCEPTED
                      AND (
                          dispatch.status IN (
                              com.app.url_shortener.iam.domain.enums.EmailDispatchStatus.PENDING,
                              com.app.url_shortener.iam.domain.enums.EmailDispatchStatus.FAILED
                          )
                          OR (
                              dispatch.status = com.app.url_shortener.iam.domain.enums.EmailDispatchStatus.SENDING
                              AND
                              dispatch.sendingStartedAt < :staleSendingThreshold
                          )
                      )

              """)
  int markAsSendingIfAvailable(UUID dispatchId, Instant now, Instant staleSendingThreshold);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
          UPDATE EmailDispatchEntity dispatch
              SET dispatch.status = com.app.url_shortener.iam.domain.enums.EmailDispatchStatus.ACCEPTED,
                  dispatch.providerMessageId = :providerMessageId,
                  dispatch.acceptedAt = :now,
                  dispatch.failedAt = null,
                  dispatch.lastErrorCode = null,
                  dispatch.lastErrorMessage = null,
                  dispatch.updatedAt = :now
              WHERE dispatch.id = :dispatchId
                  AND dispatch.status = com.app.url_shortener.iam.domain.enums.EmailDispatchStatus.SENDING
          """)
  int markAsAccepted(UUID dispatchId, String providerMessageId, Instant now);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
              UPDATE EmailDispatchEntity dispatch
                  SET dispatch.status = com.app.url_shortener.iam.domain.enums.EmailDispatchStatus.FAILED,
                      dispatch.failedAt = :now,
                      dispatch.lastErrorCode = :errorCode,
                      dispatch.lastErrorMessage = :errorMessage,
                      dispatch.updatedAt = :now
                  WHERE dispatch.id = :dispatchId
                      AND dispatch.status = com.app.url_shortener.iam.domain.enums.EmailDispatchStatus.SENDING
              """)
  int markAsFailed(UUID dispatchId, String errorCode, String errorMessage, Instant now);
}
