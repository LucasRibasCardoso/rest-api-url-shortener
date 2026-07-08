package com.app.url_shortener.shared.config;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.app.url_shortener.config.BaseWebSliceTest;
import com.app.url_shortener.security.config.SecurityConfig;
import com.app.url_shortener.security.exception.handler.CustomAccessDeniedHandler;
import com.app.url_shortener.security.exception.handler.CustomAuthenticationEntryPoint;
import com.app.url_shortener.shared.error.ProblemDetailFactory;
import com.app.url_shortener.shared.error.ProblemDetailResponseWriter;
import com.app.url_shortener.shared.idempotency.config.IdempotencyProperties;
import com.app.url_shortener.shared.idempotency.port.IdempotencyPort;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.RestController;

@Tag("web-slice")
@WebMvcTest(DocumentationControllerDisabledTest.TestController.class)
@TestPropertySource(properties = "app.openapi.enabled=false")
@EnableConfigurationProperties(OpenApiProperties.class)
@Import({
  SecurityConfig.class,
  CustomAccessDeniedHandler.class,
  CustomAuthenticationEntryPoint.class,
  ProblemDetailFactory.class,
  ProblemDetailResponseWriter.class
})
@DisplayName("Slice Web MVC - Documentação desabilitada")
class DocumentationControllerDisabledTest extends BaseWebSliceTest {

  @MockitoBean private IdempotencyPort idempotencyStore;

  @MockitoBean private IdempotencyProperties idempotencyProperties;

  @MockitoBean private Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter;

  @BeforeEach
  void setUp() {
    given(idempotencyProperties.protectedUris()).willReturn(List.of());
  }

  @Nested
  @DisplayName("Documentação desabilitada")
  class DisabledDocumentationTests {

    @ParameterizedTest
    @ValueSource(
        strings = {
          "/docs",
          "/docs/openapi.json",
          "/v3/api-docs",
          "/v3/api-docs/users",
          "/swagger-ui.html"
        })
    @DisplayName("Deve negar todos os caminhos da documentação")
    void shouldDenyAllDocumentationPaths(String path) throws Exception {
      // 1. Arrange

      // 2. Act
      ResultActions resultActions = mockMvc.perform(get(path).with(jwt()));

      // 3. Assert
      resultActions.andExpect(status().isForbidden());
    }
  }

  @RestController
  static class TestController {}
}
