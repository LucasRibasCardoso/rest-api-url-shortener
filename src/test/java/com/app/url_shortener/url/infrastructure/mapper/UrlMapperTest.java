package com.app.url_shortener.url.infrastructure.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.app.url_shortener.url.domain.model.Url;
import com.app.url_shortener.url.domain.model.UrlStatus;
import com.app.url_shortener.url.infrastructure.entity.UrlEntity;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
@DisplayName("Testes de Unidade - Mapper de URL")
class UrlMapperTest {

  private static final UUID USER_ID = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac001");

  private UrlMapper mapper;

  @BeforeEach
  void setUp() {
    mapper = new UrlMapperImpl();
  }

  @Nested
  @DisplayName("Mapeamento para entidade")
  class ToEntityTests {

    @Test
    @DisplayName("Deve mapear domínio para entidade com tipos nativos")
    void shouldMapDomainToEntityWithNativeTypes() {
      // 1. Arrange
      var createdAt = Instant.parse("2026-05-07T10:15:30Z");
      var lastAccessedAt = Instant.parse("2026-05-08T11:30:00Z");
      var url =
          Url.restore(
              USER_ID,
              "aB3dE",
              "https://google.com",
              createdAt,
              UrlStatus.ACTIVE,
              null,
              null,
              createdAt,
              42,
              lastAccessedAt);

      // 2. Act
      var result = mapper.toEntity(url);

      // 3. Assert
      assertThat(result.getUserId()).isEqualTo(USER_ID);
      assertThat(result.getShortCode()).isEqualTo("aB3dE");
      assertThat(result.getOriginalUrl()).isEqualTo("https://google.com");
      assertThat(result.getCreatedAt()).isEqualTo(createdAt);
      assertThat(result.getUpdatedAt()).isEqualTo(createdAt);
      assertThat(result.getStatus()).isEqualTo(UrlStatus.ACTIVE);
      assertThat(result.getDeletedAt()).isNull();
      assertThat(result.getDeletedBy()).isNull();
      assertThat(result.getAccessCount()).isEqualTo(42);
      assertThat(result.getLastAccessedAt()).isEqualTo(lastAccessedAt);
      assertThat(result.getCreatedAtShortCodeGsi()).isEqualTo("2026-05-07T10:15:30Z#aB3dE");
      assertThat(result.getStatusCreatedAtShortCodeGsi())
          .isEqualTo("ACTIVE#2026-05-07T10:15:30Z#aB3dE");
      assertThat(result.getActiveRankingUserIdGsi()).isEqualTo(USER_ID.toString());
    }

    @Test
    @DisplayName("Deve deixar índice de ranking sem usuário quando URL estiver deletada")
    void shouldLeaveRankingIndexUserEmptyWhenUrlIsDeleted() {
      // 1. Arrange
      var createdAt = Instant.parse("2026-05-07T10:15:30Z");
      var deletedAt = Instant.parse("2026-05-08T11:30:00Z");
      var deletedBy = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac002");
      var url =
          Url.restore(
              USER_ID,
              "aB3dE",
              "https://google.com",
              createdAt,
              UrlStatus.DELETED,
              deletedAt,
              deletedBy,
              deletedAt,
              42,
              deletedAt);

      // 2. Act
      var result = mapper.toEntity(url);

      // 3. Assert
      assertThat(result.getActiveRankingUserIdGsi()).isNull();
      assertThat(result.getStatusCreatedAtShortCodeGsi())
          .isEqualTo("DELETED#2026-05-07T10:15:30Z#aB3dE");
    }

    @Test
    @DisplayName("Deve retornar nulo quando domínio for nulo")
    void shouldReturnNullWhenDomainIsNull() {
      // 1. Arrange
      Url url = null;

      // 2. Act
      var result = mapper.toEntity(url);

      // 3. Assert
      assertThat(result).isNull();
    }
  }

  @Nested
  @DisplayName("Mapeamento para domínio")
  class ToDomainTests {

    @Test
    @DisplayName("Deve mapear entidade para domínio com tipos nativos")
    void shouldMapEntityToDomainWithNativeTypes() {
      // 1. Arrange
      var entity = urlEntity();

      // 2. Act
      var result = mapper.toDomain(entity);

      // 3. Assert
      assertThat(result.getUserId()).isEqualTo(USER_ID);
      assertThat(result.getShortCode()).isEqualTo("aB3dE");
      assertThat(result.getOriginalUrl()).isEqualTo("https://google.com");
      assertThat(result.getCreatedAt()).isEqualTo(Instant.parse("2026-05-07T10:15:30Z"));
      assertThat(result.getUpdatedAt()).isEqualTo(Instant.parse("2026-05-07T10:15:30Z"));
      assertThat(result.getStatus()).isEqualTo(UrlStatus.ACTIVE);
      assertThat(result.getDeletedAt()).isNull();
      assertThat(result.getDeletedBy()).isNull();
      assertThat(result.getAccessCount()).isEqualTo(42);
      assertThat(result.getLastAccessedAt()).isEqualTo(Instant.parse("2026-05-08T11:30:00Z"));
    }

    @Test
    @DisplayName("Deve retornar nulo quando entidade for nula")
    void shouldReturnNullWhenEntityIsNull() {
      // 1. Arrange
      UrlEntity entity = null;

      // 2. Act
      var result = mapper.toDomain(entity);

      // 3. Assert
      assertThat(result).isNull();
    }
  }

  @Nested
  @DisplayName("Mapeamento para item de listagem")
  class ToListItemResultTests {

    @Test
    @DisplayName("Deve mapear domínio para resultado de listagem")
    void shouldMapDomainToListItemResult() {
      // 1. Arrange
      var createdAt = Instant.parse("2026-05-07T10:15:30Z");
      var lastAccessedAt = Instant.parse("2026-05-08T11:30:00Z");
      var url =
          Url.restore(
              USER_ID,
              "aB3dE",
              "https://google.com",
              createdAt,
              UrlStatus.ACTIVE,
              null,
              null,
              createdAt,
              42,
              lastAccessedAt);

      // 2. Act
      var result = mapper.toListItemResult(url);

      // 3. Assert
      assertThat(result.originalUrl()).isEqualTo("https://google.com");
      assertThat(result.shortCode()).isEqualTo("aB3dE");
      assertThat(result.createdAt()).isEqualTo(createdAt);
      assertThat(result.status()).isEqualTo(UrlStatus.ACTIVE);
    }

    @Test
    @DisplayName("Deve retornar nulo quando domínio for nulo")
    void shouldReturnNullWhenDomainIsNull() {
      // 1. Arrange
      Url url = null;

      // 2. Act
      var result = mapper.toListItemResult(url);

      // 3. Assert
      assertThat(result).isNull();
    }
  }

  private UrlEntity urlEntity() {
    return UrlEntity.builder()
        .shortCode("aB3dE")
        .originalUrl("https://google.com")
        .createdAt(Instant.parse("2026-05-07T10:15:30Z"))
        .updatedAt(Instant.parse("2026-05-07T10:15:30Z"))
        .status(UrlStatus.ACTIVE)
        .userId(USER_ID)
        .accessCount(42)
        .lastAccessedAt(Instant.parse("2026-05-08T11:30:00Z"))
        .createdAtShortCodeGsi("2026-05-07T10:15:30Z#aB3dE")
        .activeRankingUserIdGsi(USER_ID.toString())
        .statusCreatedAtShortCodeGsi("ACTIVE#2026-05-07T10:15:30Z#aB3dE")
        .build();
  }
}
