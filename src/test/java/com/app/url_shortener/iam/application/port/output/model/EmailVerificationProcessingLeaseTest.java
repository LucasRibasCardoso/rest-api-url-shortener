package com.app.url_shortener.iam.application.port.output.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
@DisplayName("Testes de Unidade - Lease de Processamento de Verificação de Email")
class EmailVerificationProcessingLeaseTest {

  private static final UUID LEASE_ID =
      UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac200");

  @Nested
  @DisplayName("Criação")
  class CreationTests {

    @Test
    @DisplayName("Deve associar leaseId somente ao status ACQUIRED")
    void shouldAssociateLeaseIdOnlyWithAcquiredStatus() {
      // 1. Arrange

      // 2. Act
      var acquired = EmailVerificationProcessingLease.acquired(LEASE_ID);
      var processing = EmailVerificationProcessingLease.processing();
      var completed = EmailVerificationProcessingLease.completed();

      // 3. Assert
      assertThat(acquired.leaseId()).isEqualTo(LEASE_ID);
      assertThat(processing.leaseId()).isNull();
      assertThat(completed.leaseId()).isNull();
    }

    @Test
    @DisplayName("Deve rejeitar ACQUIRED sem leaseId")
    void shouldRejectAcquiredWithoutLeaseId() {
      // 1. Arrange

      // 2. Act & 3. Assert
      assertThatThrownBy(
              () ->
                  new EmailVerificationProcessingLease(
                      EmailVerificationProcessingLeaseStatus.ACQUIRED, null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("leaseId is required for an acquired lease");
    }

    @Test
    @DisplayName("Deve rejeitar leaseId em status sem propriedade")
    void shouldRejectLeaseIdForNonOwnedStatus() {
      // 1. Arrange

      // 2. Act & 3. Assert
      assertThatThrownBy(
              () ->
                  new EmailVerificationProcessingLease(
                      EmailVerificationProcessingLeaseStatus.PROCESSING, LEASE_ID))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("leaseId is only allowed for an acquired lease");
    }
  }
}
