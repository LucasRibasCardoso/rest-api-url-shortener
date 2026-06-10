package com.app.url_shortener.url.presentation.controller;

import com.app.url_shortener.config.BaseWebSliceTest;
import com.app.url_shortener.iam.domain.enums.PlanType;
import com.app.url_shortener.iam.domain.enums.UserStatus;
import com.app.url_shortener.security.config.SecurityConfig;
import com.app.url_shortener.security.exception.handler.CustomAccessDeniedHandler;
import com.app.url_shortener.security.exception.handler.CustomAuthenticationEntryPoint;
import com.app.url_shortener.security.principal.UserPrincipal;
import com.app.url_shortener.shared.idempotency.config.IdempotencyProperties;
import com.app.url_shortener.shared.config.JacksonConfig;
import com.app.url_shortener.shared.exception.CommonErrorCode;
import com.app.url_shortener.shared.ratelimit.exception.TooManyRequestsException;
import com.app.url_shortener.shared.idempotency.port.IdempotencyPort;
import com.app.url_shortener.shared.error.GlobalExceptionHandler;
import com.app.url_shortener.shared.error.ProblemDetailFactory;
import com.app.url_shortener.shared.error.ProblemDetailResponseWriter;
import com.app.url_shortener.shared.error.ProblemType;
import com.app.url_shortener.url.application.command.DeleteUrlCommand;
import com.app.url_shortener.url.application.command.FindAllUrlsByUserIdCommand;
import com.app.url_shortener.url.application.command.ShortenUrlCommand;
import com.app.url_shortener.url.application.command.UrlRankingCommand;
import com.app.url_shortener.url.application.command.UrlDetailsCommand;
import com.app.url_shortener.url.application.command.UrlStatusFilter;
import com.app.url_shortener.url.application.result.UrlPageResult;
import com.app.url_shortener.url.application.result.ShortenUrlResult;
import com.app.url_shortener.url.application.result.UrlDetailsResult;
import com.app.url_shortener.url.application.result.UrlListItemResult;
import com.app.url_shortener.url.application.result.UrlRankingItemResult;
import com.app.url_shortener.url.application.result.UrlRankingResult;
import com.app.url_shortener.url.application.usecase.DeleteUrlUseCase;
import com.app.url_shortener.url.application.usecase.FindAllUrlsByUserIdUseCase;
import com.app.url_shortener.url.application.usecase.FindTopAccessedUrlsByUserIdUseCase;
import com.app.url_shortener.url.application.usecase.FindUrlDetailsUseCase;
import com.app.url_shortener.url.application.usecase.ShortenUrlUseCase;
import com.app.url_shortener.url.presentation.dto.request.ShortenUrlRequestDto;
import com.app.url_shortener.url.presentation.dto.response.UrlPageResponseDto;
import com.app.url_shortener.url.presentation.dto.response.UrlDetailsResponseDto;
import com.app.url_shortener.url.presentation.dto.response.UrlRankingItemResponseDto;
import com.app.url_shortener.url.presentation.dto.response.UrlRankingResponseDto;
import com.app.url_shortener.url.presentation.dto.response.UrlResponseDto;
import com.app.url_shortener.url.presentation.mapper.UrlWebMapper;
import com.app.url_shortener.url.domain.model.UrlStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("web-slice")
@WebMvcTest(UrlController.class)
@TestPropertySource(properties = "app.base-url=https://sho.rt")
@Import({
    SecurityConfig.class,
    CustomAccessDeniedHandler.class,
    CustomAuthenticationEntryPoint.class,
    GlobalExceptionHandler.class,
    JacksonConfig.class,
    ProblemDetailFactory.class,
    ProblemDetailResponseWriter.class
})
@DisplayName("Slice Web MVC - UrlController")
class UrlControllerTest extends BaseWebSliceTest {

  private static final String URL_BASE_PATH = "/api/v1/urls";
  private static final String BASE_URL = "https://sho.rt";
  private static final UUID USER_ID = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac001");
  private static final UUID TARGET_USER_ID = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac002");

  @MockitoBean
  private UrlWebMapper urlWebMapper;

  @MockitoBean
  private DeleteUrlUseCase deleteUrlUseCase;

  @MockitoBean
  private ShortenUrlUseCase shortenUrlUseCase;

  @MockitoBean
  private FindUrlDetailsUseCase findUrlDetailsUseCase;

  @MockitoBean
  private FindAllUrlsByUserIdUseCase findAllUrlsByUserIdUseCase;

  @MockitoBean
  private FindTopAccessedUrlsByUserIdUseCase findTopAccessedUrlsByUserIdUseCase;

  @MockitoBean
  private IdempotencyPort idempotencyStore;

  @MockitoBean
  private IdempotencyProperties idempotencyProperties;

  @MockitoBean
  private Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter;

  @BeforeEach
  void setUp() {
    given(idempotencyProperties.protectedUris()).willReturn(List.of());
  }

  @Nested
  @DisplayName("Criação")
  class CreateTests {

    @Test
    @DisplayName("Deve retornar 403 quando autoridade url:create estiver ausente")
    void shouldReturnForbiddenWhenCreateAuthorityIsMissing() throws Exception {
      // 1. Arrange
      var request = new ShortenUrlRequestDto("https://google.com");

      // 2. Act
      ResultActions resultActions = mockMvc.perform(jsonPost("", request)
          .with(authenticatedUser("url:read:own")));

      // 3. Assert
      resultActions.andExpect(status().isForbidden());
      verifyNoInteractions(urlWebMapper, shortenUrlUseCase);
    }

    @Test
    @DisplayName("Deve retornar 201, Location e resposta ao encurtar URL")
    void shouldReturnCreatedLocationAndResponseWhenCreatingShortUrl() throws Exception {
      // 1. Arrange
      var request = new ShortenUrlRequestDto("https://google.com");
      var planType = PlanType.FREE;
      var command = new ShortenUrlCommand(USER_ID, request.originalUrl(), planType);
      var createdAt = Instant.parse("2026-05-10T14:30:00Z");
      var result = new ShortenUrlResult(request.originalUrl(), "aB3dE", createdAt);
      var response = new UrlResponseDto(request.originalUrl(), "aB3dE", BASE_URL + "/r/aB3dE", createdAt, UrlStatus.ACTIVE);
      given(urlWebMapper.toShortenUrlCommand(request, USER_ID, planType)).willReturn(command);
      given(shortenUrlUseCase.execute(command)).willReturn(result);
      given(urlWebMapper.toUrlResponse(result, BASE_URL)).willReturn(response);

      // 2. Act
      ResultActions resultActions = mockMvc.perform(jsonPost("", request)
          .with(authenticatedUser("url:create")));

      // 3. Assert
      resultActions
          .andExpect(status().isCreated())
          .andExpect(header().string(HttpHeaders.LOCATION, response.shortUrl()))
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
          .andExpect(jsonPath("$.originalUrl").value(response.originalUrl()))
          .andExpect(jsonPath("$.shortCode").value(response.shortCode()))
          .andExpect(jsonPath("$.shortUrl").value(response.shortUrl()))
          .andExpect(jsonPath("$.createdAt").value("2026-05-10T14:30:00Z"))
          .andExpect(jsonPath("$.status").value(response.status().name()))
          .andExpect(jsonPath("$.accessCount").doesNotExist())
          .andExpect(jsonPath("$.lastAccessedAt").doesNotExist());

      verify(urlWebMapper).toShortenUrlCommand(request, USER_ID, planType);
      verify(shortenUrlUseCase).execute(command);
      verify(urlWebMapper).toUrlResponse(result, BASE_URL);
      verifyNoMoreInteractions(urlWebMapper, shortenUrlUseCase);
    }

    @Test
    @DisplayName("Deve retornar 400 quando URL original for inválida")
    void shouldReturnBadRequestWhenOriginalUrlIsInvalid() throws Exception {
      // 1. Arrange
      var request = new ShortenUrlRequestDto("ftp://google.com");

      // 2. Act
      ResultActions resultActions = mockMvc.perform(jsonPost("", request)
          .with(authenticatedUser("url:create")));

      // 3. Assert
      resultActions
          .andExpect(status().isBadRequest())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
      verifyNoInteractions(urlWebMapper, shortenUrlUseCase);
    }

    @Test
    @DisplayName("Deve retornar 429 com ProblemDetail quando rate limit de criação for excedido")
    void shouldReturnTooManyRequestsProblemDetailWhenCreateRateLimitIsExceeded() throws Exception {
      // 1. Arrange
      var request = new ShortenUrlRequestDto("https://google.com");
      var planType = PlanType.FREE;
      var command = new ShortenUrlCommand(USER_ID, request.originalUrl(), planType);

      given(urlWebMapper.toShortenUrlCommand(request, USER_ID, planType)).willReturn(command);
      doThrow(new TooManyRequestsException(Duration.ofSeconds(45)))
          .when(shortenUrlUseCase).execute(command);

      // 2. Act
      ResultActions resultActions = mockMvc.perform(jsonPost("", request)
          .with(authenticatedUser("url:create")));

      // 3. Assert
      resultActions
          .andExpect(status().isTooManyRequests())
          .andExpect(header().string(HttpHeaders.RETRY_AFTER, "45"))
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(jsonPath("$.type").value(ProblemType.TOO_MANY_REQUESTS))
          .andExpect(jsonPath("$.title").value("Muitas requisições"))
          .andExpect(jsonPath("$.status").value(429))
          .andExpect(jsonPath("$.detail").value(CommonErrorCode.TOO_MANY_REQUESTS.getMessage()))
          .andExpect(jsonPath("$.errorCode").value(CommonErrorCode.TOO_MANY_REQUESTS.getCode()));

      verify(urlWebMapper).toShortenUrlCommand(request, USER_ID, planType);
      verify(shortenUrlUseCase).execute(command);
      verifyNoMoreInteractions(urlWebMapper, shortenUrlUseCase);
    }
  }

  @Nested
  @DisplayName("Busca de detalhes")
  class FindDetailsTests {

    @Test
    @DisplayName("Deve retornar 403 quando autoridades de leitura estiverem ausentes")
    void shouldReturnForbiddenWhenReadAuthoritiesAreMissing() throws Exception {
      // 1. Arrange

      // 2. Act
      ResultActions resultActions = mockMvc.perform(get(URL_BASE_PATH + "/aB3dE")
          .with(authenticatedUser("url:create")));

      // 3. Assert
      resultActions.andExpect(status().isForbidden());
      verifyNoInteractions(urlWebMapper, findUrlDetailsUseCase);
    }

    @Test
    @DisplayName("Deve retornar detalhes quando usuário puder ler próprias URLs")
    void shouldReturnDetailsWhenUserCanReadOwnUrls() throws Exception {
      // 1. Arrange
      var shortCode = "aB3dE";
      var command = new UrlDetailsCommand(USER_ID, shortCode, false);
      var createdAt = Instant.parse("2026-05-10T14:30:00Z");
      var lastAccessedAt = Instant.parse("2026-05-11T10:00:00Z");
      var result = new UrlDetailsResult(shortCode, "https://google.com", USER_ID, UrlStatus.ACTIVE, createdAt, createdAt, null, null, 42, lastAccessedAt);
      var response = new UrlDetailsResponseDto(shortCode, result.originalUrl(), USER_ID, UrlStatus.ACTIVE, createdAt, createdAt, null, null, 42, lastAccessedAt);
      given(urlWebMapper.toUrlDetailsCommand(USER_ID, shortCode, false)).willReturn(command);
      given(findUrlDetailsUseCase.execute(command)).willReturn(result);
      given(urlWebMapper.toUrlDetailsResponse(result)).willReturn(response);

      // 2. Act
      ResultActions resultActions = mockMvc.perform(get(URL_BASE_PATH + "/{shortcode}", shortCode)
          .with(authenticatedUser("url:read:own")));

      // 3. Assert
      resultActions
          .andExpect(status().isOk())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
          .andExpect(jsonPath("$.shortCode").value(response.shortCode()))
          .andExpect(jsonPath("$.originalUrl").value(response.originalUrl()))
          .andExpect(jsonPath("$.userId").value(response.userId().toString()))
          .andExpect(jsonPath("$.status").value(response.status().name()))
          .andExpect(jsonPath("$.createdAt").value(response.createdAt().toString()))
          .andExpect(jsonPath("$.updatedAt").value(response.updatedAt().toString()))
          .andExpect(jsonPath("$.deletedAt").doesNotExist())
          .andExpect(jsonPath("$.deletedBy").doesNotExist())
          .andExpect(jsonPath("$.accessCount").value(42))
          .andExpect(jsonPath("$.lastAccessedAt").value(lastAccessedAt.toString()));

      verify(urlWebMapper).toUrlDetailsCommand(USER_ID, shortCode, false);
      verify(findUrlDetailsUseCase).execute(command);
      verify(urlWebMapper).toUrlDetailsResponse(result);
      verifyNoMoreInteractions(urlWebMapper, findUrlDetailsUseCase);
    }

    @Test
    @DisplayName("Deve retornar detalhes quando usuário puder ler qualquer URL")
    void shouldReturnDetailsWhenUserCanReadAnyUrl() throws Exception {
      // 1. Arrange
      var shortCode = "aB3dE";
      var command = new UrlDetailsCommand(USER_ID, shortCode, true);
      var createdAt = Instant.parse("2026-05-10T14:30:00Z");
      var result = new UrlDetailsResult(shortCode, "https://google.com", TARGET_USER_ID, UrlStatus.ACTIVE, createdAt, createdAt, null, null, 0, null);
      var response = new UrlDetailsResponseDto(shortCode, result.originalUrl(), TARGET_USER_ID, UrlStatus.ACTIVE, createdAt, createdAt, null, null, 0, null);
      given(urlWebMapper.toUrlDetailsCommand(USER_ID, shortCode, true)).willReturn(command);
      given(findUrlDetailsUseCase.execute(command)).willReturn(result);
      given(urlWebMapper.toUrlDetailsResponse(result)).willReturn(response);

      // 2. Act
      ResultActions resultActions = mockMvc.perform(get(URL_BASE_PATH + "/{shortcode}", shortCode)
          .with(authenticatedUser("url:read:any")));

      // 3. Assert
      resultActions
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.shortCode").value(response.shortCode()))
          .andExpect(jsonPath("$.userId").value(response.userId().toString()))
          .andExpect(jsonPath("$.status").value(response.status().name()));

      verify(urlWebMapper).toUrlDetailsCommand(USER_ID, shortCode, true);
      verify(findUrlDetailsUseCase).execute(command);
      verify(urlWebMapper).toUrlDetailsResponse(result);
      verifyNoMoreInteractions(urlWebMapper, findUrlDetailsUseCase);
    }

    @Test
    @DisplayName("Deve retornar detalhes de URL deletada com metadados de exclusão")
    void shouldReturnDeletedUrlDetailsWithDeletionMetadata() throws Exception {
      // 1. Arrange
      var shortCode = "aB3dE";
      var command = new UrlDetailsCommand(USER_ID, shortCode, true);
      var createdAt = Instant.parse("2026-05-10T14:30:00Z");
      var deletedAt = Instant.parse("2026-05-11T10:00:00Z");
      var deletedBy = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac003");
      var result = new UrlDetailsResult(shortCode, "https://google.com", TARGET_USER_ID, UrlStatus.DELETED, createdAt, deletedAt, deletedAt, deletedBy, 0, null);
      var response = new UrlDetailsResponseDto(shortCode, result.originalUrl(), TARGET_USER_ID, UrlStatus.DELETED, createdAt, deletedAt, deletedAt, deletedBy, 0, null);
      given(urlWebMapper.toUrlDetailsCommand(USER_ID, shortCode, true)).willReturn(command);
      given(findUrlDetailsUseCase.execute(command)).willReturn(result);
      given(urlWebMapper.toUrlDetailsResponse(result)).willReturn(response);

      // 2. Act
      ResultActions resultActions = mockMvc.perform(get(URL_BASE_PATH + "/{shortcode}", shortCode)
          .with(authenticatedUser("url:read:any")));

      // 3. Assert
      resultActions
          .andExpect(status().isOk())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
          .andExpect(jsonPath("$.shortCode").value(response.shortCode()))
          .andExpect(jsonPath("$.originalUrl").value(response.originalUrl()))
          .andExpect(jsonPath("$.userId").value(response.userId().toString()))
          .andExpect(jsonPath("$.status").value(response.status().name()))
          .andExpect(jsonPath("$.createdAt").value(response.createdAt().toString()))
          .andExpect(jsonPath("$.updatedAt").value(response.updatedAt().toString()))
          .andExpect(jsonPath("$.deletedAt").value(response.deletedAt().toString()))
          .andExpect(jsonPath("$.deletedBy").value(response.deletedBy().toString()));

      verify(urlWebMapper).toUrlDetailsCommand(USER_ID, shortCode, true);
      verify(findUrlDetailsUseCase).execute(command);
      verify(urlWebMapper).toUrlDetailsResponse(result);
      verifyNoMoreInteractions(urlWebMapper, findUrlDetailsUseCase);
    }

    @Test
    @DisplayName("Deve retornar detalhes quando código curto tiver tamanho máximo permitido")
    void shouldReturnDetailsWhenShortCodeHasMaximumAllowedLength() throws Exception {
      // 1. Arrange
      var shortCode = "a".repeat(64);
      var command = new UrlDetailsCommand(USER_ID, shortCode, false);
      var createdAt = Instant.parse("2026-05-10T14:30:00Z");
      var result = new UrlDetailsResult(shortCode, "https://google.com", USER_ID, UrlStatus.ACTIVE, createdAt, createdAt, null, null, 0, null);
      var response = new UrlDetailsResponseDto(shortCode, result.originalUrl(), USER_ID, UrlStatus.ACTIVE, createdAt, createdAt, null, null, 0, null);
      given(urlWebMapper.toUrlDetailsCommand(USER_ID, shortCode, false)).willReturn(command);
      given(findUrlDetailsUseCase.execute(command)).willReturn(result);
      given(urlWebMapper.toUrlDetailsResponse(result)).willReturn(response);

      // 2. Act
      ResultActions resultActions = mockMvc.perform(get(URL_BASE_PATH + "/{shortCode}", shortCode)
          .with(authenticatedUser("url:read:own")));

      // 3. Assert
      resultActions
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.shortCode").value(response.shortCode()));

      verify(urlWebMapper).toUrlDetailsCommand(USER_ID, shortCode, false);
      verify(findUrlDetailsUseCase).execute(command);
      verify(urlWebMapper).toUrlDetailsResponse(result);
      verifyNoMoreInteractions(urlWebMapper, findUrlDetailsUseCase);
    }

    @Test
    @DisplayName("Deve retornar 404 sem delegar quando código curto de detalhes exceder o tamanho máximo")
    void shouldReturnNotFoundWithoutDelegatingWhenDetailsShortCodeExceedsMaximumLength() throws Exception {
      // 1. Arrange
      var shortCode = "a".repeat(65);

      // 2. Act
      ResultActions resultActions = mockMvc.perform(get(URL_BASE_PATH + "/{shortCode}", shortCode)
          .with(authenticatedUser("url:read:own")));

      // 3. Assert
      resultActions.andExpect(status().isNotFound());
      verifyNoInteractions(urlWebMapper, findUrlDetailsUseCase);
    }

    @Test
    @DisplayName("Deve retornar 404 sem delegar quando código curto de detalhes tiver caracteres especiais")
    void shouldReturnNotFoundWithoutDelegatingWhenDetailsShortCodeHasSpecialCharacters() throws Exception {
      // 1. Arrange

      // 2. Act
      ResultActions resultActions = mockMvc.perform(get(URL_BASE_PATH + "/invalid-code!")
          .with(authenticatedUser("url:read:own")));

      // 3. Assert
      resultActions.andExpect(status().isNotFound());
      verifyNoInteractions(urlWebMapper, findUrlDetailsUseCase);
    }
  }

  @Nested
  @DisplayName("Exclusão")
  class DeleteTests {

    @Test
    @DisplayName("Deve retornar 403 quando autoridades de exclusão estiverem ausentes")
    void shouldReturnForbiddenWhenDeleteAuthoritiesAreMissing() throws Exception {
      // 1. Arrange

      // 2. Act
      ResultActions resultActions = mockMvc.perform(delete(URL_BASE_PATH + "/aB3dE")
          .with(authenticatedUser("url:read:own")));

      // 3. Assert
      resultActions.andExpect(status().isForbidden());
      verifyNoInteractions(urlWebMapper, deleteUrlUseCase);
    }

    @Test
    @DisplayName("Deve retornar 204 quando usuário puder excluir próprias URLs")
    void shouldReturnNoContentWhenUserCanDeleteOwnUrls() throws Exception {
      // 1. Arrange
      var shortCode = "aB3dE";
      var command = new DeleteUrlCommand(USER_ID, shortCode, false);
      given(urlWebMapper.toDeleteUrlCommand(USER_ID, shortCode, false)).willReturn(command);

      // 2. Act
      ResultActions resultActions = mockMvc.perform(delete(URL_BASE_PATH + "/{shortcode}", shortCode)
          .with(authenticatedUser("url:delete:own")));

      // 3. Assert
      resultActions
          .andExpect(status().isNoContent())
          .andExpect(content().string(""));

      verify(urlWebMapper).toDeleteUrlCommand(USER_ID, shortCode, false);
      verify(deleteUrlUseCase).execute(command);
      verifyNoMoreInteractions(urlWebMapper, deleteUrlUseCase);
    }

    @Test
    @DisplayName("Deve retornar 204 quando código curto tiver tamanho máximo permitido")
    void shouldReturnNoContentWhenDeleteShortCodeHasMaximumAllowedLength() throws Exception {
      // 1. Arrange
      var shortCode = "a".repeat(64);
      var command = new DeleteUrlCommand(USER_ID, shortCode, false);
      given(urlWebMapper.toDeleteUrlCommand(USER_ID, shortCode, false)).willReturn(command);

      // 2. Act
      ResultActions resultActions = mockMvc.perform(delete(URL_BASE_PATH + "/{shortCode}", shortCode)
          .with(authenticatedUser("url:delete:own")));

      // 3. Assert
      resultActions
          .andExpect(status().isNoContent())
          .andExpect(content().string(""));

      verify(urlWebMapper).toDeleteUrlCommand(USER_ID, shortCode, false);
      verify(deleteUrlUseCase).execute(command);
      verifyNoMoreInteractions(urlWebMapper, deleteUrlUseCase);
    }

    @Test
    @DisplayName("Deve retornar 404 sem delegar quando código curto de exclusão exceder o tamanho máximo")
    void shouldReturnNotFoundWithoutDelegatingWhenDeleteShortCodeExceedsMaximumLength() throws Exception {
      // 1. Arrange
      var shortCode = "a".repeat(65);

      // 2. Act
      ResultActions resultActions = mockMvc.perform(delete(URL_BASE_PATH + "/{shortCode}", shortCode)
          .with(authenticatedUser("url:delete:own")));

      // 3. Assert
      resultActions.andExpect(status().isNotFound());
      verifyNoInteractions(urlWebMapper, deleteUrlUseCase);
    }

    @Test
    @DisplayName("Deve retornar 404 sem delegar quando código curto de exclusão tiver caracteres especiais")
    void shouldReturnNotFoundWithoutDelegatingWhenDeleteShortCodeHasSpecialCharacters() throws Exception {
      // 1. Arrange

      // 2. Act
      ResultActions resultActions = mockMvc.perform(delete(URL_BASE_PATH + "/invalid-code!")
          .with(authenticatedUser("url:delete:own")));

      // 3. Assert
      resultActions.andExpect(status().isNotFound());
      verifyNoInteractions(urlWebMapper, deleteUrlUseCase);
    }

    @Test
    @DisplayName("Deve retornar 204 quando usuário puder excluir qualquer URL")
    void shouldReturnNoContentWhenUserCanDeleteAnyUrl() throws Exception {
      // 1. Arrange
      var shortCode = "aB3dE";
      var command = new DeleteUrlCommand(USER_ID, shortCode, true);
      given(urlWebMapper.toDeleteUrlCommand(USER_ID, shortCode, true)).willReturn(command);

      // 2. Act
      ResultActions resultActions = mockMvc.perform(delete(URL_BASE_PATH + "/{shortcode}", shortCode)
          .with(authenticatedUser("url:delete:any")));

      // 3. Assert
      resultActions
          .andExpect(status().isNoContent())
          .andExpect(content().string(""));

      verify(urlWebMapper).toDeleteUrlCommand(USER_ID, shortCode, true);
      verify(deleteUrlUseCase).execute(command);
      verifyNoMoreInteractions(urlWebMapper, deleteUrlUseCase);
    }
  }

  @Nested
  @DisplayName("Listagem do usuário autenticado")
  class FindAllMineTests {

    @Test
    @DisplayName("Deve retornar 403 quando autoridade url:list:own estiver ausente")
    void shouldReturnForbiddenWhenListOwnAuthorityIsMissing() throws Exception {
      // 1. Arrange

      // 2. Act
      ResultActions resultActions = mockMvc.perform(get(URL_BASE_PATH + "/me")
          .with(authenticatedUser("url:read:own")));

      // 3. Assert
      resultActions.andExpect(status().isForbidden());
      verifyNoInteractions(urlWebMapper, findAllUrlsByUserIdUseCase);
    }

    @Test
    @DisplayName("Deve retornar URLs do usuário autenticado")
    void shouldReturnAuthenticatedUserUrls() throws Exception {
      // 1. Arrange
      var limit = 10;
      var cursor = "next-page-cursor";
      var command = new FindAllUrlsByUserIdCommand(USER_ID, limit, cursor, UrlStatusFilter.ACTIVE);
      var result = new UrlPageResult(List.of(), null);
      var response = new UrlPageResponseDto(List.of(), null);
      given(urlWebMapper.toFindAllUrlsByUserIdCommand(USER_ID, limit, cursor, UrlStatusFilter.ACTIVE)).willReturn(command);
      given(findAllUrlsByUserIdUseCase.execute(command)).willReturn(result);
      given(urlWebMapper.toUrlPagelResponse(result, BASE_URL)).willReturn(response);

      // 2. Act
      ResultActions resultActions = mockMvc.perform(get(URL_BASE_PATH + "/me")
          .queryParam("limit", String.valueOf(limit))
          .queryParam("cursor", cursor)
          .with(authenticatedUser("url:list:own")));

      // 3. Assert
      resultActions
          .andExpect(status().isOk())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
          .andExpect(jsonPath("$.urls").isArray())
          .andExpect(jsonPath("$.nextCursor").doesNotExist());

      verify(urlWebMapper).toFindAllUrlsByUserIdCommand(USER_ID, limit, cursor, UrlStatusFilter.ACTIVE);
      verify(findAllUrlsByUserIdUseCase).execute(command);
      verify(urlWebMapper).toUrlPagelResponse(result, BASE_URL);
      verifyNoMoreInteractions(urlWebMapper, findAllUrlsByUserIdUseCase);
    }

    @Test
    @DisplayName("Deve retornar URLs deletadas do usuário autenticado quando filtro for DELETED")
    void shouldReturnAuthenticatedUserDeletedUrlsWhenStatusFilterIsDeleted() throws Exception {
      // 1. Arrange
      var limit = 20;
      var status = UrlStatusFilter.DELETED;
      var command = new FindAllUrlsByUserIdCommand(USER_ID, limit, null, status);
      var result = new UrlPageResult(List.of(), null);
      var response = new UrlPageResponseDto(List.of(), null);
      given(urlWebMapper.toFindAllUrlsByUserIdCommand(USER_ID, limit, null, status)).willReturn(command);
      given(findAllUrlsByUserIdUseCase.execute(command)).willReturn(result);
      given(urlWebMapper.toUrlPagelResponse(result, BASE_URL)).willReturn(response);

      // 2. Act
      ResultActions resultActions = mockMvc.perform(get(URL_BASE_PATH + "/me")
          .queryParam("status", status.name())
          .with(authenticatedUser("url:list:own")));

      // 3. Assert
      resultActions
          .andExpect(status().isOk())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
          .andExpect(jsonPath("$.urls").isArray());

      verify(urlWebMapper).toFindAllUrlsByUserIdCommand(USER_ID, limit, null, status);
      verify(findAllUrlsByUserIdUseCase).execute(command);
      verify(urlWebMapper).toUrlPagelResponse(result, BASE_URL);
      verifyNoMoreInteractions(urlWebMapper, findAllUrlsByUserIdUseCase);
    }

    @Test
    @DisplayName("Deve retornar 400 quando filtro de status do usuário autenticado for inválido")
    void shouldReturnBadRequestWhenMineStatusFilterIsInvalid() throws Exception {
      // 1. Arrange

      // 2. Act
      ResultActions resultActions = mockMvc.perform(get(URL_BASE_PATH + "/me")
          .queryParam("status", "INVALID")
          .with(authenticatedUser("url:list:own")));

      // 3. Assert
      resultActions
          .andExpect(status().isBadRequest())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
      verifyNoInteractions(urlWebMapper, findAllUrlsByUserIdUseCase);
    }

    @Test
    @DisplayName("Deve retornar 400 quando limite for menor que o permitido")
    void shouldReturnBadRequestWhenMineLimitIsBelowMinimum() throws Exception {
      // 1. Arrange

      // 2. Act
      ResultActions resultActions = mockMvc.perform(get(URL_BASE_PATH + "/me")
          .queryParam("limit", "0")
          .with(authenticatedUser("url:list:own")));

      // 3. Assert
      resultActions
          .andExpect(status().isBadRequest())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
      verifyNoInteractions(urlWebMapper, findAllUrlsByUserIdUseCase);
    }
  }

  @Nested
  @DisplayName("Ranking do usuário autenticado")
  class FindMyRankingTests {

    @Test
    @DisplayName("Deve retornar 403 quando autoridade url:ranking:own estiver ausente")
    void shouldReturnForbiddenWhenRankingOwnAuthorityIsMissing() throws Exception {
      // 1. Arrange

      // 2. Act
      ResultActions resultActions = mockMvc.perform(get(URL_BASE_PATH + "/me/ranking")
          .with(authenticatedUser("url:list:own")));

      // 3. Assert
      resultActions.andExpect(status().isForbidden());
      verifyNoInteractions(urlWebMapper, findTopAccessedUrlsByUserIdUseCase);
    }

    @Test
    @DisplayName("Deve retornar ranking com rankingSize padrão igual a 3")
    void shouldReturnRankingWithDefaultRankingSize() throws Exception {
      // 1. Arrange
      var rankingSize = 3;
      var command = new UrlRankingCommand(USER_ID, rankingSize);
      var createdAt = Instant.parse("2026-05-10T14:30:00Z");
      var lastAccessedAt = Instant.parse("2026-05-11T10:00:00Z");
      var result = new UrlRankingResult(List.of(
          new UrlRankingItemResult("https://google.com", "aB3dE", createdAt, UrlStatus.ACTIVE, 42, lastAccessedAt)
      ));
      var response = new UrlRankingResponseDto(List.of(
          new UrlRankingItemResponseDto("https://google.com", BASE_URL + "/r/aB3dE", createdAt, UrlStatus.ACTIVE, 42, lastAccessedAt)
      ));
      given(urlWebMapper.toUrlRankingCommand(USER_ID, rankingSize)).willReturn(command);
      given(findTopAccessedUrlsByUserIdUseCase.execute(command)).willReturn(result);
      given(urlWebMapper.toUrlRankingResponse(result, BASE_URL)).willReturn(response);

      // 2. Act
      ResultActions resultActions = mockMvc.perform(get(URL_BASE_PATH + "/me/ranking")
          .with(authenticatedUser("url:ranking:own")));

      // 3. Assert
      resultActions
          .andExpect(status().isOk())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
          .andExpect(jsonPath("$.urls[0].originalUrl").value("https://google.com"))
          .andExpect(jsonPath("$.urls[0].shortUrl").value(BASE_URL + "/r/aB3dE"))
          .andExpect(jsonPath("$.urls[0].createdAt").value(createdAt.toString()))
          .andExpect(jsonPath("$.urls[0].status").value(UrlStatus.ACTIVE.name()))
          .andExpect(jsonPath("$.urls[0].accessCount").value(42))
          .andExpect(jsonPath("$.urls[0].lastAccessedAt").value(lastAccessedAt.toString()));

      verify(urlWebMapper).toUrlRankingCommand(USER_ID, rankingSize);
      verify(findTopAccessedUrlsByUserIdUseCase).execute(command);
      verify(urlWebMapper).toUrlRankingResponse(result, BASE_URL);
      verifyNoMoreInteractions(urlWebMapper, findTopAccessedUrlsByUserIdUseCase);
    }

    @Test
    @DisplayName("Deve retornar ranking quando rankingSize for 10")
    void shouldReturnRankingWhenRankingSizeIsTen() throws Exception {
      // 1. Arrange
      var rankingSize = 10;
      var command = new UrlRankingCommand(USER_ID, rankingSize);
      var result = new UrlRankingResult(List.of());
      var response = new UrlRankingResponseDto(List.of());
      given(urlWebMapper.toUrlRankingCommand(USER_ID, rankingSize)).willReturn(command);
      given(findTopAccessedUrlsByUserIdUseCase.execute(command)).willReturn(result);
      given(urlWebMapper.toUrlRankingResponse(result, BASE_URL)).willReturn(response);

      // 2. Act
      ResultActions resultActions = mockMvc.perform(get(URL_BASE_PATH + "/me/ranking")
          .queryParam("rankingSize", String.valueOf(rankingSize))
          .with(authenticatedUser("url:ranking:own")));

      // 3. Assert
      resultActions
          .andExpect(status().isOk())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
          .andExpect(jsonPath("$.urls").isArray());

      verify(urlWebMapper).toUrlRankingCommand(USER_ID, rankingSize);
      verify(findTopAccessedUrlsByUserIdUseCase).execute(command);
      verify(urlWebMapper).toUrlRankingResponse(result, BASE_URL);
      verifyNoMoreInteractions(urlWebMapper, findTopAccessedUrlsByUserIdUseCase);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 5, 11})
    @DisplayName("Deve retornar 400 quando rankingSize for inválido")
    void shouldReturnBadRequestWhenRankingSizeIsInvalid(int rankingSize) throws Exception {
      // 1. Arrange

      // 2. Act
      ResultActions resultActions = mockMvc.perform(get(URL_BASE_PATH + "/me/ranking")
          .queryParam("rankingSize", String.valueOf(rankingSize))
          .with(authenticatedUser("url:ranking:own")));

      // 3. Assert
      resultActions
          .andExpect(status().isBadRequest())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(jsonPath("$.errorCode").value(CommonErrorCode.REQUEST_VALIDATION_FAILED.getCode()))
          .andExpect(jsonPath("$.errors[0].field").value("rankingSize"))
          .andExpect(jsonPath("$.errors[0].message").value("O tamanho do ranking deve ser 3 ou 10"));
      verifyNoInteractions(urlWebMapper, findTopAccessedUrlsByUserIdUseCase);
    }
  }

  @Nested
  @DisplayName("Listagem por usuário")
  class FindAllByUserTests {

    @Test
    @DisplayName("Deve retornar 403 quando autoridade url:list:any estiver ausente")
    void shouldReturnForbiddenWhenListAnyAuthorityIsMissing() throws Exception {
      // 1. Arrange

      // 2. Act
      ResultActions resultActions = mockMvc.perform(get(URL_BASE_PATH + "/users/{userId}", TARGET_USER_ID)
          .with(authenticatedUser("url:list:own")));

      // 3. Assert
      resultActions.andExpect(status().isForbidden());
      verifyNoInteractions(urlWebMapper, findAllUrlsByUserIdUseCase);
    }

    @Test
    @DisplayName("Deve retornar URLs do usuário informado")
    void shouldReturnUrlsForRequestedUser() throws Exception {
      // 1. Arrange
      var limit = 20;
      var command = new FindAllUrlsByUserIdCommand(TARGET_USER_ID, limit, null, UrlStatusFilter.ACTIVE);
      var createdAt = Instant.parse("2026-05-10T14:30:00Z");
      var pageResult = new UrlPageResult(List.of(new UrlListItemResult("https://google.com", "aB3dE", createdAt, UrlStatus.ACTIVE)), "next");
      var response = new UrlPageResponseDto(List.of(
          new UrlResponseDto("https://google.com", "aB3dE", BASE_URL + "/r/aB3dE", createdAt, UrlStatus.ACTIVE)
      ), "next");
      given(urlWebMapper.toFindAllUrlsByUserIdCommand(TARGET_USER_ID, limit, null, UrlStatusFilter.ACTIVE)).willReturn(command);
      given(findAllUrlsByUserIdUseCase.execute(command)).willReturn(pageResult);
      given(urlWebMapper.toUrlPagelResponse(pageResult, BASE_URL)).willReturn(response);

      // 2. Act
      ResultActions resultActions = mockMvc.perform(get(URL_BASE_PATH + "/users/{userId}", TARGET_USER_ID)
          .with(authenticatedUser("url:list:any")));

      // 3. Assert
      resultActions
          .andExpect(status().isOk())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
          .andExpect(jsonPath("$.urls[0].originalUrl").value("https://google.com"))
          .andExpect(jsonPath("$.urls[0].shortCode").value("aB3dE"))
          .andExpect(jsonPath("$.urls[0].shortUrl").value(BASE_URL + "/r/aB3dE"))
          .andExpect(jsonPath("$.urls[0].status").value(UrlStatus.ACTIVE.name()))
          .andExpect(jsonPath("$.urls[0].accessCount").doesNotExist())
          .andExpect(jsonPath("$.urls[0].lastAccessedAt").doesNotExist())
          .andExpect(jsonPath("$.nextCursor").value("next"));

      verify(urlWebMapper).toFindAllUrlsByUserIdCommand(TARGET_USER_ID, limit, null, UrlStatusFilter.ACTIVE);
      verify(findAllUrlsByUserIdUseCase).execute(command);
      verify(urlWebMapper).toUrlPagelResponse(pageResult, BASE_URL);
      verifyNoMoreInteractions(urlWebMapper, findAllUrlsByUserIdUseCase);
    }

    @Test
    @DisplayName("Deve retornar todas as URLs do usuário informado quando filtro for ALL")
    void shouldReturnAllUrlsForRequestedUserWhenStatusFilterIsAll() throws Exception {
      // 1. Arrange
      var limit = 20;
      var status = UrlStatusFilter.ALL;
      var command = new FindAllUrlsByUserIdCommand(TARGET_USER_ID, limit, null, status);
      var result = new UrlPageResult(List.of(), null);
      var response = new UrlPageResponseDto(List.of(), null);
      given(urlWebMapper.toFindAllUrlsByUserIdCommand(TARGET_USER_ID, limit, null, status)).willReturn(command);
      given(findAllUrlsByUserIdUseCase.execute(command)).willReturn(result);
      given(urlWebMapper.toUrlPagelResponse(result, BASE_URL)).willReturn(response);

      // 2. Act
      ResultActions resultActions = mockMvc.perform(get(URL_BASE_PATH + "/users/{userId}", TARGET_USER_ID)
          .queryParam("status", status.name())
          .with(authenticatedUser("url:list:any")));

      // 3. Assert
      resultActions
          .andExpect(status().isOk())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
          .andExpect(jsonPath("$.urls").isArray());

      verify(urlWebMapper).toFindAllUrlsByUserIdCommand(TARGET_USER_ID, limit, null, status);
      verify(findAllUrlsByUserIdUseCase).execute(command);
      verify(urlWebMapper).toUrlPagelResponse(result, BASE_URL);
      verifyNoMoreInteractions(urlWebMapper, findAllUrlsByUserIdUseCase);
    }

    @Test
    @DisplayName("Deve retornar 400 quando limite for maior que o permitido")
    void shouldReturnBadRequestWhenUserLimitIsAboveMaximum() throws Exception {
      // 1. Arrange

      // 2. Act
      ResultActions resultActions = mockMvc.perform(get(URL_BASE_PATH + "/users/{userId}", TARGET_USER_ID)
          .queryParam("limit", "101")
          .with(authenticatedUser("url:list:any")));

      // 3. Assert
      resultActions
          .andExpect(status().isBadRequest())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
      verifyNoInteractions(urlWebMapper, findAllUrlsByUserIdUseCase);
    }
  }

  private MockHttpServletRequestBuilder jsonPost(String path, Object body) {
    return post(URL_BASE_PATH + path)
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(body));
  }

  private RequestPostProcessor authenticatedUser(String... authorities) {
    return request -> {
      jwt()
          .jwt(jwt -> jwt
              .subject(USER_ID.toString())
              .claim("plan", "FREE")
              .claim("authorities", List.of(authorities)))
          .authorities(grantedAuthorities(authorities))
          .postProcessRequest(request);

      return authentication(new UsernamePasswordAuthenticationToken(
          userPrincipal(authorities),
          null,
          grantedAuthorities(authorities)
      )).postProcessRequest(request);
    };
  }

  private UserPrincipal userPrincipal(String... authorities) {
    return new UserPrincipal(
        USER_ID,
        "User Name",
        "user@email.com",
        null,
        PlanType.FREE,
        UserStatus.ACTIVE,
        grantedAuthorities(authorities)
    );
  }

  private List<GrantedAuthority> grantedAuthorities(String... authorities) {
    return Arrays.stream(authorities)
        .map(SimpleGrantedAuthority::new)
        .map(GrantedAuthority.class::cast)
        .toList();
  }
}
