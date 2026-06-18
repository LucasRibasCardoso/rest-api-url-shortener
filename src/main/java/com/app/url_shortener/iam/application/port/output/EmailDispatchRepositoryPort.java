package com.app.url_shortener.iam.application.port.output;

import com.app.url_shortener.iam.domain.model.EmailDispatch;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface EmailDispatchRepositoryPort {

  EmailDispatch save(EmailDispatch dispatch);

  Optional<EmailDispatch> findByEventId(UUID eventId);

  Optional<EmailDispatch> findLatestByVerificationTokenId(UUID verificationTokenId);

  boolean markAsAccepted(UUID dispatchId, String providerMessageId, Instant now);

  boolean markAsFailed(UUID dispatchId, String errorCode, String errorMessage, Instant now);

  boolean markAsSendingIfAvailable(UUID dispatchId, Instant now, Instant staleSendingThreshold);
}
