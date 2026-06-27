package com.app.url_shortener.url.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

@Tag("unit")
@DisplayName("Testes de Unidade - Entidade Url")
class UrlTest {

  private static final UUID USER_ID = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac001");

  @Nested
  @DisplayName("Criação")
  class CreationTests {

    @Test
    @DisplayName("Deve criar URL com código curto, URL original e data de criação")
    void shouldCreateUrlSuccessfully() {
      // 1. Arrange
      var shortCode = "abc123";
      var originalUrl = "https://example.com/articles/1";

      // 2. Act
      var url = Url.create(USER_ID, shortCode, originalUrl);

      // 3. Assert
      assertThat(url.getUserId()).isEqualTo(USER_ID);
      assertThat(url.getShortCode()).isEqualTo(shortCode);
      assertThat(url.getOriginalUrl()).isEqualTo(originalUrl);
      assertThat(url.getCreatedAt()).isNotNull();
      assertThat(url.getAccessCount()).isZero();
      assertThat(url.getLastAccessedAt()).isNull();
    }

    @Test
    @DisplayName("Deve remover espaços do código curto e da URL original ao criar")
    void shouldTrimShortCodeAndOriginalUrlWhenCreating() {
      // 1. Arrange
      var shortCode = "  abc123  ";
      var originalUrl = "  https://example.com/articles/1  ";

      // 2. Act
      var url = Url.create(USER_ID, shortCode, originalUrl);

      // 3. Assert
      assertThat(url.getUserId()).isEqualTo(USER_ID);
      assertThat(url.getShortCode()).isEqualTo("abc123");
      assertThat(url.getOriginalUrl()).isEqualTo("https://example.com/articles/1");
    }
  }

  @Nested
  @DisplayName("Restauração")
  class RestorationTests {

    @Test
    @DisplayName("Deve restaurar URL com os dados persistidos")
    void shouldRestoreUrlSuccessfully() {
      // 1. Arrange
      var shortCode = "abc123";
      var originalUrl = "https://example.com/articles/1";
      var createdAt = Instant.parse("2026-05-07T10:15:00Z");
      var lastAccessedAt = Instant.parse("2026-05-08T11:30:00Z");

      // 2. Act
      var url =
          Url.restore(
              USER_ID,
              shortCode,
              originalUrl,
              createdAt,
              UrlStatus.ACTIVE,
              null,
              null,
              createdAt,
              42,
              lastAccessedAt);

      // 3. Assert
      assertThat(url.getUserId()).isEqualTo(USER_ID);
      assertThat(url.getShortCode()).isEqualTo(shortCode);
      assertThat(url.getOriginalUrl()).isEqualTo(originalUrl);
      assertThat(url.getCreatedAt()).isEqualTo(createdAt);
      assertThat(url.getAccessCount()).isEqualTo(42);
      assertThat(url.getLastAccessedAt()).isEqualTo(lastAccessedAt);
    }
  }

  @Nested
  @DisplayName("Validação")
  class ValidationTests {

    @ParameterizedTest
    @NullSource
    @DisplayName("Deve rejeitar código curto nulo")
    void shouldThrowExceptionWhenShortCodeIsNull(String invalidShortCode) {
      // 1. Arrange
      var originalUrl = "https://example.com/articles/1";

      // 2. Act & 3. Assert
      assertThatThrownBy(() -> Url.create(USER_ID, invalidShortCode, originalUrl))
          .isInstanceOf(NullPointerException.class)
          .hasMessage("shortCode is required");
    }

    @ParameterizedTest
    @NullSource
    @DisplayName("Deve rejeitar URL original nula")
    void shouldThrowExceptionWhenOriginalUrlIsNull(String invalidOriginalUrl) {
      // 1. Arrange
      var shortCode = "abc123";

      // 2. Act & 3. Assert
      assertThatThrownBy(() -> Url.create(USER_ID, shortCode, invalidOriginalUrl))
          .isInstanceOf(NullPointerException.class)
          .hasMessage("originalUrl is required");
    }

    @ParameterizedTest
    @NullSource
    @DisplayName("Deve lançar NullPointerException quando o usuário for nulo")
    void shouldThrowExceptionWhenUserIdIsNull(UUID invalidUserId) {
      // 1. Arrange
      var shortCode = "abc123";
      var originalUrl = "https://example.com/articles/1";

      // 2. Act & 3. Assert
      assertThatThrownBy(() -> Url.create(invalidUserId, shortCode, originalUrl))
          .isInstanceOf(NullPointerException.class)
          .hasMessage("userId is required.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   ", "\t", "\n"})
    @DisplayName("Deve rejeitar código curto vazio ou em branco")
    void shouldRejectBlankShortCode(String blankShortCode) {
      // 1. Arrange
      var originalUrl = "https://example.com/articles/1";

      // 2. Act & 3. Assert
      assertThatThrownBy(() -> Url.create(USER_ID, blankShortCode, originalUrl))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("shortCode must not be blank");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   ", "\t", "\n"})
    @DisplayName("Deve rejeitar URL original vazia ou em branco")
    void shouldRejectBlankOriginalUrl(String blankOriginalUrl) {
      // 1. Arrange
      var shortCode = "abc123";

      // 2. Act & 3. Assert
      assertThatThrownBy(() -> Url.create(USER_ID, shortCode, blankOriginalUrl))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("originalUrl must not be blank");
    }

    @Test
    @DisplayName("Deve rejeitar contador de acessos negativo ao restaurar")
    void shouldRejectNegativeAccessCountWhenRestoring() {
      // 1. Arrange
      var createdAt = Instant.parse("2026-05-07T10:15:00Z");

      // 2. Act & 3. Assert
      assertThatThrownBy(
              () ->
                  Url.restore(
                      USER_ID,
                      "abc123",
                      "https://example.com/articles/1",
                      createdAt,
                      UrlStatus.ACTIVE,
                      null,
                      null,
                      createdAt,
                      -1,
                      null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("accessCount must not be negative.");
    }
  }

  @Nested
  @DisplayName("Igualdade")
  class EqualityTests {

    @Test
    @DisplayName("Deve considerar URLs iguais quando possuírem o mesmo código curto")
    void shouldBeEqualWhenShortCodeIsEqual() {
      // 1. Arrange
      var createdAt = Instant.parse("2026-05-07T10:15:00Z");
      var firstUrl = activeUrl(USER_ID, "abc123", "https://example.com/articles/1", createdAt);
      var secondUrl =
          Url.restore(
              UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac002"),
              "abc123",
              "https://example.com/articles/2",
              createdAt.plusSeconds(60),
              UrlStatus.ACTIVE,
              null,
              null,
              createdAt.plusSeconds(120),
              10,
              createdAt.plusSeconds(180));

      // 2. Act & 3. Assert
      assertThat(firstUrl).isEqualTo(secondUrl).hasSameHashCodeAs(secondUrl);
    }

    @Test
    @DisplayName("Deve considerar URLs diferentes quando o código curto for diferente")
    void shouldNotBeEqualWhenShortCodeIsDifferent() {
      // 1. Arrange
      var createdAt = Instant.parse("2026-05-07T10:15:00Z");
      var url = activeUrl(USER_ID, "abc123", "https://example.com/articles/1", createdAt);
      var differentShortCode =
          activeUrl(USER_ID, "xyz789", "https://example.com/articles/1", createdAt);

      // 2. Act & 3. Assert
      assertThat(url).isNotEqualTo(differentShortCode);
    }
  }

  private static Url activeUrl(
      UUID userId, String shortCode, String originalUrl, Instant createdAt) {
    return Url.restore(
        userId,
        shortCode,
        originalUrl,
        createdAt,
        UrlStatus.ACTIVE,
        null,
        null,
        createdAt,
        0,
        null);
  }
}
