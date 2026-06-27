package com.app.url_shortener.iam.infrastructure.persistence.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.app.url_shortener.iam.domain.enums.EmailDispatchPurpose;
import com.app.url_shortener.iam.domain.enums.EmailDispatchReason;
import com.app.url_shortener.iam.domain.enums.EmailDispatchStatus;
import com.app.url_shortener.iam.domain.model.EmailDispatch;
import com.app.url_shortener.iam.infrastructure.entity.EmailDispatchEntity;
import com.app.url_shortener.iam.infrastructure.mapper.EmailDispatchPersistenceMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

@Tag("unit")
@DisplayName("Testes de Unidade - EmailDispatchPersistenceMapper")
class EmailDispatchPersistenceMapperTest {

  private static final UUID DISPATCH_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID EVENT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID USER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
  private static final UUID VERIFICATION_TOKEN_ID =
      UUID.fromString("44444444-4444-4444-4444-444444444444");
  private static final Instant CREATED_AT = Instant.parse("2026-06-16T10:00:00Z");

  private final EmailDispatchPersistenceMapper mapper = createMapper();

  @Nested
  @DisplayName("Mapeamento para Domínio")
  class ToDomainTests {

    @Test
    @DisplayName("Deve mapear entidade de dispatch de email para domínio")
    void shouldMapEmailDispatchEntityToDomain() {
      // 1. Arrange
      Instant sendingStartedAt = CREATED_AT.plus(Duration.ofSeconds(5));
      Instant acceptedAt = CREATED_AT.plus(Duration.ofSeconds(10));
      Instant updatedAt = CREATED_AT.plus(Duration.ofSeconds(10));
      EmailDispatchEntity entity =
          new EmailDispatchEntity(
              DISPATCH_ID,
              EVENT_ID,
              USER_ID,
              VERIFICATION_TOKEN_ID,
              "user@example.com",
              EmailDispatchPurpose.EMAIL_VERIFICATION,
              EmailDispatchReason.REGISTER,
              EmailDispatchStatus.ACCEPTED,
              "provider-message-id",
              1,
              sendingStartedAt,
              acceptedAt,
              null,
              null,
              null,
              CREATED_AT,
              updatedAt);

      // 2. Act
      EmailDispatch domain = mapper.toDomain(entity);

      // 3. Assert
      assertThat(domain).isNotNull();
      assertThat(domain.getId()).isEqualTo(DISPATCH_ID);
      assertThat(domain.getEventId()).isEqualTo(EVENT_ID);
      assertThat(domain.getUserId()).isEqualTo(USER_ID);
      assertThat(domain.getVerificationTokenId()).isEqualTo(VERIFICATION_TOKEN_ID);
      assertThat(domain.getEmail()).isEqualTo("user@example.com");
      assertThat(domain.getPurpose()).isEqualTo(EmailDispatchPurpose.EMAIL_VERIFICATION);
      assertThat(domain.getReason()).isEqualTo(EmailDispatchReason.REGISTER);
      assertThat(domain.getStatus()).isEqualTo(EmailDispatchStatus.ACCEPTED);
      assertThat(domain.getProviderMessageId()).isEqualTo("provider-message-id");
      assertThat(domain.getSendAttempts()).isEqualTo(1);
      assertThat(domain.getSendingStartedAt()).isEqualTo(sendingStartedAt);
      assertThat(domain.getAcceptedAt()).isEqualTo(acceptedAt);
      assertThat(domain.getFailedAt()).isNull();
      assertThat(domain.getLastErrorCode()).isNull();
      assertThat(domain.getLastErrorMessage()).isNull();
      assertThat(domain.getCreatedAt()).isEqualTo(CREATED_AT);
      assertThat(domain.getUpdatedAt()).isEqualTo(updatedAt);
    }

    @Test
    @DisplayName("Deve retornar nulo quando a entidade for nula")
    void shouldReturnNullWhenEmailDispatchEntityIsNull() {
      // 1. Arrange
      EmailDispatchEntity entity = null;

      // 2. Act
      EmailDispatch domain = mapper.toDomain(entity);

      // 3. Assert
      assertThat(domain).isNull();
    }
  }

  @Nested
  @DisplayName("Mapeamento para Entidade")
  class ToEntityTests {

    @Test
    @DisplayName("Deve mapear domínio de dispatch de email para entidade")
    void shouldMapEmailDispatchDomainToEntity() {
      // 1. Arrange
      Instant sendingStartedAt = CREATED_AT.plus(Duration.ofSeconds(5));
      Instant failedAt = CREATED_AT.plus(Duration.ofSeconds(10));
      Instant updatedAt = CREATED_AT.plus(Duration.ofSeconds(10));
      EmailDispatch domain =
          EmailDispatch.restore(
              DISPATCH_ID,
              EVENT_ID,
              USER_ID,
              VERIFICATION_TOKEN_ID,
              "user@example.com",
              EmailDispatchPurpose.EMAIL_VERIFICATION,
              EmailDispatchReason.REGISTER,
              EmailDispatchStatus.FAILED,
              null,
              1,
              sendingStartedAt,
              null,
              failedAt,
              "PROVIDER_ERROR",
              null,
              CREATED_AT,
              updatedAt);

      // 2. Act
      EmailDispatchEntity entity = mapper.toEntity(domain);

      // 3. Assert
      assertThat(entity).isNotNull();
      assertThat(entity.getId()).isEqualTo(DISPATCH_ID);
      assertThat(entity.getEventId()).isEqualTo(EVENT_ID);
      assertThat(entity.getUserId()).isEqualTo(USER_ID);
      assertThat(entity.getVerificationTokenId()).isEqualTo(VERIFICATION_TOKEN_ID);
      assertThat(entity.getEmail()).isEqualTo("user@example.com");
      assertThat(entity.getPurpose()).isEqualTo(EmailDispatchPurpose.EMAIL_VERIFICATION);
      assertThat(entity.getReason()).isEqualTo(EmailDispatchReason.REGISTER);
      assertThat(entity.getStatus()).isEqualTo(EmailDispatchStatus.FAILED);
      assertThat(entity.getProviderMessageId()).isNull();
      assertThat(entity.getSendAttempts()).isEqualTo(1);
      assertThat(entity.getSendingStartedAt()).isEqualTo(sendingStartedAt);
      assertThat(entity.getAcceptedAt()).isNull();
      assertThat(entity.getFailedAt()).isEqualTo(failedAt);
      assertThat(entity.getLastErrorCode()).isEqualTo("PROVIDER_ERROR");
      assertThat(entity.getLastErrorMessage()).isNull();
      assertThat(entity.getCreatedAt()).isEqualTo(CREATED_AT);
      assertThat(entity.getUpdatedAt()).isEqualTo(updatedAt);
    }

    @Test
    @DisplayName("Deve retornar nulo quando o domínio for nulo")
    void shouldReturnNullWhenEmailDispatchDomainIsNull() {
      // 1. Arrange
      EmailDispatch domain = null;

      // 2. Act
      EmailDispatchEntity entity = mapper.toEntity(domain);

      // 3. Assert
      assertThat(entity).isNull();
    }
  }

  private static EmailDispatchPersistenceMapper createMapper() {
    return Mappers.getMapper(EmailDispatchPersistenceMapper.class);
  }
}
