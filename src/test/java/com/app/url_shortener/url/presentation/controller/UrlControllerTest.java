package com.app.url_shortener.url.presentation.controller;

import com.app.url_shortener.config.BaseWebSliceTest;
import com.app.url_shortener.iam.domain.enums.PlanType;
import com.app.url_shortener.iam.domain.enums.UserStatus;
import com.app.url_shortener.security.config.SecurityConfig;
import com.app.url_shortener.security.exception.handler.CustomAccessDeniedHandler;
import com.app.url_shortener.security.exception.handler.CustomAuthenticationEntryPoint;
import com.app.url_shortener.security.principal.UserPrincipal;
import com.app.url_shortener.shared.config.properties.IdempotencyProperties;
import com.app.url_shortener.shared.config.JacksonConfig;
import com.app.url_shortener.shared.exception.CommonErrorCode;
import com.app.url_shortener.shared.exception.ratelimit.TooManyRequestsException;
import com.app.url_shortener.shared.infrastructure.idempotency.IdempotencyStore;
import com.app.url_shortener.shared.presentation.error.GlobalExceptionHandler;
import com.app.url_shortener.shared.presentation.error.ProblemDetailFactory;
import com.app.url_shortener.shared.presentation.error.ProblemDetailResponseWriter;
import com.app.url_shortener.shared.presentation.error.ProblemType;
import com.app.url_shortener.url.application.command.DeleteUrlCommand;
import com.app.url_shortener.url.application.command.FindAllUrlsByUserIdCommand;
import com.app.url_shortener.url.application.command.ShortenUrlCommand;
import com.app.url_shortener.url.application.command.UrlDetailsCommand;
import com.app.url_shortener.url.application.result.PageUrlResult;
import com.app.url_shortener.url.application.result.ShortenUrlResult;
import com.app.url_shortener.url.application.result.UrlDetailsResult;
import com.app.url_shortener.url.application.usecase.DeleteUrlUseCase;
import com.app.url_shortener.url.application.usecase.FindAllUrlsByUserIdUseCase;
import com.app.url_shortener.url.application.usecase.FindUrlDetailsUseCase;
import com.app.url_shortener.url.application.usecase.ShortenUrlUseCase;
import com.app.url_shortener.url.presentation.dto.request.ShortenUrlRequestDto;
import com.app.url_shortener.url.presentation.dto.response.PageUrlResponseDto;
import com.app.url_shortener.url.presentation.dto.response.UrlResponseDto;
import com.app.url_shortener.url.presentation.mapper.UrlWebMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
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
import java.time.LocalDateTime;
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
  private IdempotencyStore idempotencyStore;

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
      var createdAt = LocalDateTime.of(2026, 5, 10, 14, 30);
      var result = new ShortenUrlResult(request.originalUrl(), "aB3dE", createdAt);
      var response = new UrlResponseDto(request.originalUrl(), BASE_URL + "/r/aB3dE", createdAt);
      given(urlWebMapper.toCommand(request, USER_ID, planType)).willReturn(command);
      given(shortenUrlUseCase.execute(command)).willReturn(result);
      given(urlWebMapper.toResponse(result, BASE_URL)).willReturn(response);

      // 2. Act
      ResultActions resultActions = mockMvc.perform(jsonPost("", request)
          .with(authenticatedUser("url:create")));

      // 3. Assert
      resultActions
          .andExpect(status().isCreated())
          .andExpect(header().string(HttpHeaders.LOCATION, response.shortUrl()))
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
          .andExpect(jsonPath("$.originalUrl").value(response.originalUrl()))
          .andExpect(jsonPath("$.shortUrl").value(response.shortUrl()))
          .andExpect(jsonPath("$.createdAt").value("2026-05-10T14:30:00"));

      verify(urlWebMapper).toCommand(request, USER_ID, planType);
      verify(shortenUrlUseCase).execute(command);
      verify(urlWebMapper).toResponse(result, BASE_URL);
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

      given(urlWebMapper.toCommand(request, USER_ID, planType)).willReturn(command);
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

      verify(urlWebMapper).toCommand(request, USER_ID, planType);
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
      var createdAt = LocalDateTime.of(2026, 5, 10, 14, 30);
      var result = new UrlDetailsResult("https://google.com", shortCode, createdAt);
      var response = new UrlResponseDto(result.originalUrl(), BASE_URL + "/r/" + shortCode, createdAt);
      given(urlWebMapper.toCommand(USER_ID, shortCode, false)).willReturn(command);
      given(findUrlDetailsUseCase.execute(command)).willReturn(result);
      given(urlWebMapper.toResponse(result, BASE_URL)).willReturn(response);

      // 2. Act
      ResultActions resultActions = mockMvc.perform(get(URL_BASE_PATH + "/{shortcode}", shortCode)
          .with(authenticatedUser("url:read:own")));

      // 3. Assert
      resultActions
          .andExpect(status().isOk())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
          .andExpect(jsonPath("$.originalUrl").value(response.originalUrl()))
          .andExpect(jsonPath("$.shortUrl").value(response.shortUrl()));

      verify(urlWebMapper).toCommand(USER_ID, shortCode, false);
      verify(findUrlDetailsUseCase).execute(command);
      verify(urlWebMapper).toResponse(result, BASE_URL);
      verifyNoMoreInteractions(urlWebMapper, findUrlDetailsUseCase);
    }

    @Test
    @DisplayName("Deve retornar detalhes quando usuário puder ler qualquer URL")
    void shouldReturnDetailsWhenUserCanReadAnyUrl() throws Exception {
      // 1. Arrange
      var shortCode = "aB3dE";
      var command = new UrlDetailsCommand(USER_ID, shortCode, true);
      var createdAt = LocalDateTime.of(2026, 5, 10, 14, 30);
      var result = new UrlDetailsResult("https://google.com", shortCode, createdAt);
      var response = new UrlResponseDto(result.originalUrl(), BASE_URL + "/r/" + shortCode, createdAt);
      given(urlWebMapper.toCommand(USER_ID, shortCode, true)).willReturn(command);
      given(findUrlDetailsUseCase.execute(command)).willReturn(result);
      given(urlWebMapper.toResponse(result, BASE_URL)).willReturn(response);

      // 2. Act
      ResultActions resultActions = mockMvc.perform(get(URL_BASE_PATH + "/{shortcode}", shortCode)
          .with(authenticatedUser("url:read:any")));

      // 3. Assert
      resultActions
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.shortUrl").value(response.shortUrl()));

      verify(urlWebMapper).toCommand(USER_ID, shortCode, true);
      verify(findUrlDetailsUseCase).execute(command);
      verify(urlWebMapper).toResponse(result, BASE_URL);
      verifyNoMoreInteractions(urlWebMapper, findUrlDetailsUseCase);
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
      given(urlWebMapper.toCommandDelete(USER_ID, shortCode, false)).willReturn(command);

      // 2. Act
      ResultActions resultActions = mockMvc.perform(delete(URL_BASE_PATH + "/{shortcode}", shortCode)
          .with(authenticatedUser("url:delete:own")));

      // 3. Assert
      resultActions
          .andExpect(status().isNoContent())
          .andExpect(content().string(""));

      verify(urlWebMapper).toCommandDelete(USER_ID, shortCode, false);
      verify(deleteUrlUseCase).execute(command);
      verifyNoMoreInteractions(urlWebMapper, deleteUrlUseCase);
    }

    @Test
    @DisplayName("Deve retornar 204 quando usuário puder excluir qualquer URL")
    void shouldReturnNoContentWhenUserCanDeleteAnyUrl() throws Exception {
      // 1. Arrange
      var shortCode = "aB3dE";
      var command = new DeleteUrlCommand(USER_ID, shortCode, true);
      given(urlWebMapper.toCommandDelete(USER_ID, shortCode, true)).willReturn(command);

      // 2. Act
      ResultActions resultActions = mockMvc.perform(delete(URL_BASE_PATH + "/{shortcode}", shortCode)
          .with(authenticatedUser("url:delete:any")));

      // 3. Assert
      resultActions
          .andExpect(status().isNoContent())
          .andExpect(content().string(""));

      verify(urlWebMapper).toCommandDelete(USER_ID, shortCode, true);
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
      var command = new FindAllUrlsByUserIdCommand(USER_ID, limit, cursor);
      var result = new PageUrlResult(List.of(), null);
      var response = new PageUrlResponseDto(List.of(), null);
      given(urlWebMapper.toCommand(USER_ID, limit, cursor)).willReturn(command);
      given(findAllUrlsByUserIdUseCase.execute(command)).willReturn(result);
      given(urlWebMapper.toResponse(result, BASE_URL)).willReturn(response);

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

      verify(urlWebMapper).toCommand(USER_ID, limit, cursor);
      verify(findAllUrlsByUserIdUseCase).execute(command);
      verify(urlWebMapper).toResponse(result, BASE_URL);
      verifyNoMoreInteractions(urlWebMapper, findAllUrlsByUserIdUseCase);
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
      var command = new FindAllUrlsByUserIdCommand(TARGET_USER_ID, limit, null);
      var createdAt = LocalDateTime.of(2026, 5, 10, 14, 30);
      var pageResult = new PageUrlResult(List.of(new UrlDetailsResult("https://google.com", "aB3dE", createdAt)), "next");
      var response = new PageUrlResponseDto(List.of(
          new UrlResponseDto("https://google.com", BASE_URL + "/r/aB3dE", createdAt)
      ), "next");
      given(urlWebMapper.toCommand(TARGET_USER_ID, limit, null)).willReturn(command);
      given(findAllUrlsByUserIdUseCase.execute(command)).willReturn(pageResult);
      given(urlWebMapper.toResponse(pageResult, BASE_URL)).willReturn(response);

      // 2. Act
      ResultActions resultActions = mockMvc.perform(get(URL_BASE_PATH + "/users/{userId}", TARGET_USER_ID)
          .with(authenticatedUser("url:list:any")));

      // 3. Assert
      resultActions
          .andExpect(status().isOk())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
          .andExpect(jsonPath("$.urls[0].originalUrl").value("https://google.com"))
          .andExpect(jsonPath("$.urls[0].shortUrl").value(BASE_URL + "/r/aB3dE"))
          .andExpect(jsonPath("$.nextCursor").value("next"));

      verify(urlWebMapper).toCommand(TARGET_USER_ID, limit, null);
      verify(findAllUrlsByUserIdUseCase).execute(command);
      verify(urlWebMapper).toResponse(pageResult, BASE_URL);
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
