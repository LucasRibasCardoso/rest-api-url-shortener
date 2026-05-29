package com.app.url_shortener.url.presentation.controller;

import com.app.url_shortener.config.BaseWebSliceTest;
import com.app.url_shortener.url.application.command.ResolveUrlCommand;
import com.app.url_shortener.url.application.result.ResolvedUrlResult;
import com.app.url_shortener.url.application.usecase.ResolveUrlUseCase;
import com.app.url_shortener.url.presentation.mapper.UrlWebMapper;
import com.app.url_shortener.shared.config.JacksonConfig;
import com.app.url_shortener.shared.config.properties.IdempotencyProperties;
import com.app.url_shortener.shared.infrastructure.idempotency.port.IdempotencyPort;
import com.app.url_shortener.shared.presentation.error.ProblemDetailFactory;
import com.app.url_shortener.shared.presentation.error.ProblemDetailResponseWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("web-slice")
@WebMvcTest(RedirectController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({
    JacksonConfig.class,
    ProblemDetailFactory.class,
    ProblemDetailResponseWriter.class
})
@DisplayName("Slice Web MVC - RedirectController")
class RedirectControllerTest extends BaseWebSliceTest {

  @MockitoBean
  private ResolveUrlUseCase resolveUrlUseCase;

  @MockitoBean
  private UrlWebMapper urlWebMapper;

  @MockitoBean
  private IdempotencyPort idempotencyStore;

  @MockitoBean
  private IdempotencyProperties idempotencyProperties;

  @BeforeEach
  void setUp() {
    given(idempotencyProperties.protectedUris()).willReturn(List.of());
  }

  @Nested
  @DisplayName("Redirecionamento")
  class RedirectTests {

    @Test
    @DisplayName("Deve retornar 302 e Location com URL original resolvida")
    void shouldReturnFoundAndLocationHeaderWithResolvedOriginalUrl() throws Exception {
      // 1. Arrange
      var shortCode = "aB3dE";
      var command = new ResolveUrlCommand(shortCode);
      var result = new ResolvedUrlResult("https://google.com");
      given(urlWebMapper.toCommand(shortCode)).willReturn(command);
      given(resolveUrlUseCase.execute(command)).willReturn(result);

      // 2. Act
      ResultActions resultActions = mockMvc.perform(get("/r/{shortCode}", shortCode));

      // 3. Assert
      resultActions
          .andExpect(status().isFound())
          .andExpect(header().string(HttpHeaders.LOCATION, result.originalUrl()));

      verify(urlWebMapper).toCommand(shortCode);
      verify(resolveUrlUseCase).execute(command);
      verifyNoMoreInteractions(urlWebMapper, resolveUrlUseCase);
    }
  }

  @Nested
  @DisplayName("Validação de rota")
  class RouteValidationTests {

    @Test
    @DisplayName("Deve retornar 404 sem delegar quando código curto tiver caracteres especiais")
    void shouldReturnNotFoundWithoutDelegatingWhenShortCodeHasSpecialCharacters() throws Exception {
      // 1. Arrange

      // 2. Act
      ResultActions resultActions = mockMvc.perform(get("/r/invalid-code!"));

      // 3. Assert
      resultActions.andExpect(status().isNotFound());
      verifyNoInteractions(urlWebMapper, resolveUrlUseCase);
    }
  }
}
