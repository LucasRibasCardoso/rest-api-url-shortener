package com.app.url_shortener.url.presentation.mapper;

import com.app.url_shortener.iam.domain.enums.PlanType;
import com.app.url_shortener.url.application.command.FindAllUrlsByUserIdCommand;
import com.app.url_shortener.url.application.command.UrlStatusFilter;
import com.app.url_shortener.url.application.result.PageUrlResult;
import com.app.url_shortener.url.application.result.ShortenUrlResult;
import com.app.url_shortener.url.application.result.UrlDetailsResult;
import com.app.url_shortener.url.application.result.UrlListItemResult;
import com.app.url_shortener.url.domain.model.UrlStatus;
import com.app.url_shortener.url.presentation.dto.request.ShortenUrlRequestDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("Testes de Unidade - Mapper Web de URL")
class UrlWebMapperTest {

  private final UrlWebMapper mapper = Mappers.getMapper(UrlWebMapper.class);

  @Nested
  @DisplayName("Mapeamento para Comandos")
  class ToCommandTests {

    @Test
    @DisplayName("Deve mapear request de encurtamento para comando")
    void shouldMapShortenUrlRequestToCommand() {
      // 1. Arrange
      var userId = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac001");
      var request = new ShortenUrlRequestDto("  https://google.com  ");
      var planType = PlanType.PREMIUM;

      // 2. Act
      var command = mapper.toCommand(request, userId, planType);

      // 3. Assert
      assertAll(
          () -> assertThat(command.userId()).isEqualTo(userId),
          () -> assertThat(command.originalUrl()).isEqualTo("https://google.com"),
          () -> assertThat(command.planType()).isEqualTo(planType)
      );
    }

    @Test
    @DisplayName("Deve mapear dados de detalhes para comando")
    void shouldMapUrlDetailsDataToCommand() {
      // 1. Arrange
      var requesterId = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac001");
      var shortCode = "  aB3dE  ";
      var canReadAny = true;

      // 2. Act
      var command = mapper.toCommand(requesterId, shortCode, canReadAny);

      // 3. Assert
      assertAll(
          () -> assertThat(command.requesterId()).isEqualTo(requesterId),
          () -> assertThat(command.shortCode()).isEqualTo("aB3dE"),
          () -> assertThat(command.canReadAny()).isTrue()
      );
    }

    @Test
    @DisplayName("Deve mapear dados de exclusão para comando")
    void shouldMapDeleteDataToCommand() {
      // 1. Arrange
      var requesterId = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac001");
      var shortCode = "  aB3dE  ";
      var canDeleteAny = true;

      // 2. Act
      var command = mapper.toCommandDelete(requesterId, shortCode, canDeleteAny);

      // 3. Assert
      assertAll(
          () -> assertThat(command.requesterId()).isEqualTo(requesterId),
          () -> assertThat(command.shortCode()).isEqualTo("aB3dE"),
          () -> assertThat(command.canDeleteAny()).isTrue()
      );
    }

    @Test
    @DisplayName("Deve mapear dados de listagem para comando")
    void shouldMapFindAllDataToCommand() {
      // 1. Arrange
      var userId = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac001");
      var limit = 20;
      var cursor = "  next-page-cursor  ";
      var status = UrlStatusFilter.DELETED;

      // 2. Act
      var command = mapper.toCommand(userId, limit, cursor, status);

      // 3. Assert
      assertAll(
          () -> assertThat(command.userId()).isEqualTo(userId),
          () -> assertThat(command.limit()).isEqualTo(limit),
          () -> assertThat(command.cursor()).isEqualTo("next-page-cursor"),
          () -> assertThat(command.status()).isEqualTo(status)
      );
    }

    @Test
    @DisplayName("Deve usar filtro ACTIVE quando comando de listagem receber status nulo")
    void shouldUseActiveFilterWhenFindAllCommandReceivesNullStatus() {
      // 1. Arrange
      var userId = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac001");
      var limit = 20;

      // 2. Act
      var command = new FindAllUrlsByUserIdCommand(userId, limit, null, null);

      // 3. Assert
      assertThat(command.status()).isEqualTo(UrlStatusFilter.ACTIVE);
    }

    @Test
    @DisplayName("Deve mapear código curto para comando de resolução")
    void shouldMapShortCodeToResolveCommand() {
      // 1. Arrange
      var shortCode = "  aB3dE  ";

      // 2. Act
      var command = mapper.toCommand(shortCode);

      // 3. Assert
      assertThat(command.shortCode()).isEqualTo("aB3dE");
    }
  }

  @Nested
  @DisplayName("Mapeamento para Respostas")
  class ToResponseTests {

    @Test
    @DisplayName("Deve mapear resultado de encurtamento para resposta com URL curta completa")
    void shouldMapShortenUrlResultToResponseWithFullShortUrl() {
      // 1. Arrange
      var createdAt = Instant.parse("2026-05-10T14:30:00Z");
      var result = new ShortenUrlResult("https://google.com", "aB3dE", createdAt);
      var baseUrl = "https://sho.rt";

      // 2. Act
      var response = mapper.toResponse(result, baseUrl);

      // 3. Assert
      assertAll(
          () -> assertThat(response.originalUrl()).isEqualTo(result.originalUrl()),
          () -> assertThat(response.shortCode()).isEqualTo(result.shortCode()),
          () -> assertThat(response.shortUrl()).isEqualTo("https://sho.rt/r/aB3dE"),
          () -> assertThat(response.createdAt()).isEqualTo(createdAt),
          () -> assertThat(response.status()).isEqualTo(UrlStatus.ACTIVE)
      );
    }

    @Test
    @DisplayName("Deve mapear resultado de detalhes para resposta completa")
    void shouldMapUrlDetailsResultToFullDetailsResponse() {
      // 1. Arrange
      var userId = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac001");
      var createdAt = Instant.parse("2026-05-10T14:30:00Z");
      var updatedAt = Instant.parse("2026-05-11T10:00:00Z");
      var lastAccessedAt = Instant.parse("2026-05-11T09:00:00Z");
      var result = new UrlDetailsResult(
          "aB3dE",
          "https://google.com",
          userId,
          UrlStatus.ACTIVE,
          createdAt,
          updatedAt,
          null,
          null,
          42,
          lastAccessedAt);

      // 2. Act
      var response = mapper.toResponse(result);

      // 3. Assert
      assertAll(
          () -> assertThat(response.shortCode()).isEqualTo(result.shortCode()),
          () -> assertThat(response.originalUrl()).isEqualTo(result.originalUrl()),
          () -> assertThat(response.userId()).isEqualTo(userId),
          () -> assertThat(response.status()).isEqualTo(UrlStatus.ACTIVE),
          () -> assertThat(response.createdAt()).isEqualTo(createdAt),
          () -> assertThat(response.updatedAt()).isEqualTo(updatedAt),
          () -> assertThat(response.deletedAt()).isNull(),
          () -> assertThat(response.deletedBy()).isNull(),
          () -> assertThat(response.accessCount()).isEqualTo(42),
          () -> assertThat(response.lastAccessedAt()).isEqualTo(lastAccessedAt)
      );
    }

    @Test
    @DisplayName("Deve mapear resultado de detalhes deletado para resposta completa")
    void shouldMapDeletedUrlDetailsResultToFullDetailsResponse() {
      // 1. Arrange
      var userId = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac001");
      var deletedBy = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac002");
      var createdAt = Instant.parse("2026-05-10T14:30:00Z");
      var deletedAt = Instant.parse("2026-05-11T10:00:00Z");
      var lastAccessedAt = Instant.parse("2026-05-11T09:00:00Z");
      var result = new UrlDetailsResult(
          "aB3dE",
          "https://google.com",
          userId,
          UrlStatus.DELETED,
          createdAt,
          deletedAt,
          deletedAt,
          deletedBy,
          10,
          lastAccessedAt);

      // 2. Act
      var response = mapper.toResponse(result);

      // 3. Assert
      assertAll(
          () -> assertThat(response.shortCode()).isEqualTo(result.shortCode()),
          () -> assertThat(response.originalUrl()).isEqualTo(result.originalUrl()),
          () -> assertThat(response.userId()).isEqualTo(userId),
          () -> assertThat(response.status()).isEqualTo(UrlStatus.DELETED),
          () -> assertThat(response.createdAt()).isEqualTo(createdAt),
          () -> assertThat(response.updatedAt()).isEqualTo(deletedAt),
          () -> assertThat(response.deletedAt()).isEqualTo(deletedAt),
          () -> assertThat(response.deletedBy()).isEqualTo(deletedBy),
          () -> assertThat(response.accessCount()).isEqualTo(10),
          () -> assertThat(response.lastAccessedAt()).isEqualTo(lastAccessedAt)
      );
    }

    @Test
    @DisplayName("Deve mapear resultado paginado para resposta propagando URL base")
    void shouldMapPageUrlResultToResponsePropagatingBaseUrl() {
      // 1. Arrange
      var firstCreatedAt = Instant.parse("2026-05-10T14:30:00Z");
      var secondCreatedAt = Instant.parse("2026-05-10T15:45:00Z");
      var result = new PageUrlResult(List.of(
          new UrlListItemResult("https://google.com", "aB3dE", firstCreatedAt, UrlStatus.ACTIVE),
          new UrlListItemResult("https://spring.io", "fG4hI", secondCreatedAt, UrlStatus.DELETED)
      ), "next-page-cursor");
      var baseUrl = "https://sho.rt";

      // 2. Act
      var response = mapper.toResponse(result, baseUrl);

      // 3. Assert
      assertAll(
          () -> assertThat(response.nextCursor()).isEqualTo(result.nextCursor()),
          () -> assertThat(response.urls()).hasSize(2),
          () -> assertThat(response.urls().get(0).originalUrl()).isEqualTo("https://google.com"),
          () -> assertThat(response.urls().get(0).shortCode()).isEqualTo("aB3dE"),
          () -> assertThat(response.urls().get(0).shortUrl()).isEqualTo("https://sho.rt/r/aB3dE"),
          () -> assertThat(response.urls().get(0).createdAt()).isEqualTo(firstCreatedAt),
          () -> assertThat(response.urls().get(0).status()).isEqualTo(UrlStatus.ACTIVE),
          () -> assertThat(response.urls().get(1).originalUrl()).isEqualTo("https://spring.io"),
          () -> assertThat(response.urls().get(1).shortCode()).isEqualTo("fG4hI"),
          () -> assertThat(response.urls().get(1).shortUrl()).isEqualTo("https://sho.rt/r/fG4hI"),
          () -> assertThat(response.urls().get(1).createdAt()).isEqualTo(secondCreatedAt),
          () -> assertThat(response.urls().get(1).status()).isEqualTo(UrlStatus.DELETED)
      );
    }
  }

  @Nested
  @DisplayName("Conversão de URL Curta")
  class ShortUrlConversionTests {

    @Test
    @DisplayName("Deve montar URL curta completa quando URL base não terminar em barra")
    void shouldBuildFullShortUrlWhenBaseUrlDoesNotEndWithSlash() {
      // 1. Arrange
      var shortCode = "aB3dE";
      var baseUrl = "https://sho.rt";

      // 2. Act
      var result = mapper.toFullShortUrl(shortCode, baseUrl);

      // 3. Assert
      assertThat(result).isEqualTo("https://sho.rt/r/aB3dE");
    }

    @Test
    @DisplayName("Deve montar URL curta completa quando URL base terminar em barra")
    void shouldBuildFullShortUrlWhenBaseUrlEndsWithSlash() {
      // 1. Arrange
      var shortCode = "aB3dE";
      var baseUrl = "https://sho.rt/";

      // 2. Act
      var result = mapper.toFullShortUrl(shortCode, baseUrl);

      // 3. Assert
      assertThat(result).isEqualTo("https://sho.rt/r/aB3dE");
    }

    @Test
    @DisplayName("Deve retornar nulo quando código curto for nulo")
    void shouldReturnNullWhenShortCodeIsNull() {
      // 1. Arrange
      String shortCode = null;
      var baseUrl = "https://sho.rt";

      // 2. Act
      var result = mapper.toFullShortUrl(shortCode, baseUrl);

      // 3. Assert
      assertThat(result).isNull();
    }

    @Test
    @DisplayName("Deve retornar nulo quando URL base for nula")
    void shouldReturnNullWhenBaseUrlIsNull() {
      // 1. Arrange
      var shortCode = "aB3dE";
      String baseUrl = null;

      // 2. Act
      var result = mapper.toFullShortUrl(shortCode, baseUrl);

      // 3. Assert
      assertThat(result).isNull();
    }
  }
}
