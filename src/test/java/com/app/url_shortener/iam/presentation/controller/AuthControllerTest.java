package com.app.url_shortener.iam.presentation.controller;

import com.app.url_shortener.iam.application.command.*;
import com.app.url_shortener.iam.application.result.*;
import com.app.url_shortener.iam.application.usecase.*;
import com.app.url_shortener.iam.domain.exception.IamErrorCode;
import com.app.url_shortener.iam.presentation.dto.request.LoginRequestDto;
import com.app.url_shortener.iam.presentation.dto.request.RegisterRequestDto;
import com.app.url_shortener.iam.presentation.dto.request.ResendVerificationRequestDto;
import com.app.url_shortener.iam.presentation.dto.request.VerifyEmailRequestDto;
import com.app.url_shortener.iam.presentation.dto.response.AuthenticatedUserResponseDto;
import com.app.url_shortener.iam.presentation.dto.response.GenericMessageResponseDto;
import com.app.url_shortener.iam.presentation.dto.response.LoginResponseDto;
import com.app.url_shortener.iam.presentation.dto.response.RefreshTokenResponseDto;
import com.app.url_shortener.iam.presentation.mapper.IamWebMapper;
import com.app.url_shortener.config.BaseWebSliceTest;
import com.app.url_shortener.security.config.SecurityConfig;
import com.app.url_shortener.security.exception.handler.CustomAccessDeniedHandler;
import com.app.url_shortener.security.exception.handler.CustomAuthenticationEntryPoint;
import com.app.url_shortener.shared.idempotency.config.IdempotencyProperties;
import com.app.url_shortener.shared.config.JacksonConfig;
import com.app.url_shortener.shared.exception.CommonErrorCode;
import com.app.url_shortener.shared.ratelimit.exception.TooManyRequestsException;
import com.app.url_shortener.shared.idempotency.port.IdempotencyPort;
import com.app.url_shortener.shared.presentation.error.GlobalExceptionHandler;
import com.app.url_shortener.shared.presentation.error.ProblemDetailFactory;
import com.app.url_shortener.shared.presentation.error.ProblemDetailResponseWriter;
import com.app.url_shortener.shared.presentation.error.ProblemType;
import com.app.url_shortener.shared.ratelimit.core.ClientIpResolver;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Tag("web-slice")
@WebMvcTest(AuthController.class)
@Import({
        SecurityConfig.class,
        CustomAccessDeniedHandler.class,
        CustomAuthenticationEntryPoint.class,
        GlobalExceptionHandler.class,
        JacksonConfig.class,
        ProblemDetailFactory.class,
        ProblemDetailResponseWriter.class
})
@DisplayName("Slice Web MVC - AuthController")
class AuthControllerTest extends BaseWebSliceTest {

  private static final String AUTH_BASE_PATH = "/api/v1/auth";

  @MockitoBean
  private IamWebMapper iamWebMapper;

  @MockitoBean
  private LoginUseCase loginUseCase;

  @MockitoBean
  private LogoutUseCase logoutUseCase;

  @MockitoBean
  private VerifyEmailUseCase verifyEmailUseCase;

  @MockitoBean
  private RegisterUserUseCase registerUserUseCase;

  @MockitoBean
  private RefreshTokenUseCase refreshTokenUseCase;

  @MockitoBean
  private ResendVerificationUseCase resendVerificationUseCase;

  @MockitoBean
  private ClientIpResolver clientIpResolver;

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
  @DisplayName("Cadastro")
  class RegisterTests {

    @Test
    @DisplayName("Deve retornar 201 e serializar resposta ao registrar usuário")
    void shouldReturnCreatedAndSerializeResponseWhenRegisteringUser() throws Exception {
      // 1. Arrange
      var request = new RegisterRequestDto("User Name", "user@email.com", "secure-password");
      var command = new RegisterUserCommand(request.name(), request.email(), request.password());
      var result = new RegisterUserResult("Usuário cadastrado com sucesso.");
      var response = new GenericMessageResponseDto(result.message());

      given(iamWebMapper.toRegisterUserCommand(request)).willReturn(command);
      given(registerUserUseCase.execute(command)).willReturn(result);
      given(iamWebMapper.toGenericMessageResponse(result)).willReturn(response);

      // 2. Act
      ResultActions resultActions = mockMvc.perform(jsonPost("/register", request));

      // 3. Assert
      resultActions
              .andExpect(status().isCreated())
              .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
              .andExpect(jsonPath("$.message").value(response.message()));

      verify(iamWebMapper).toRegisterUserCommand(request);
      verify(registerUserUseCase).execute(command);
      verify(iamWebMapper).toGenericMessageResponse(result);
      verifyNoMoreInteractions(iamWebMapper, registerUserUseCase);
    }
  }

  @Nested
  @DisplayName("Verificação de email")
  class VerifyEmailTests {

    @Test
    @DisplayName("Deve retornar 200 e delegar verificação de email")
    void shouldReturnOkAndDelegateEmailVerification() throws Exception {
      // 1. Arrange
      var request = new VerifyEmailRequestDto("user@email.com", "123456");
      var command = new VerifyEmailCommand(request.email(), null);
      var result = new VerifyEmailResult("Email verificado com sucesso.");
      var response = new GenericMessageResponseDto(result.message());

      given(iamWebMapper.toVerifyEmailCommand(request)).willReturn(command);
      given(verifyEmailUseCase.execute(command)).willReturn(result);
      given(iamWebMapper.toGenericMessageResponse(result)).willReturn(response);

      // 2. Act
      ResultActions resultActions = mockMvc.perform(jsonPost("/verify-email", request));

      // 3. Assert
      resultActions
              .andExpect(status().isOk())
              .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
              .andExpect(jsonPath("$.message").value(response.message()));

      verify(iamWebMapper).toVerifyEmailCommand(request);
      verify(verifyEmailUseCase).execute(command);
      verify(iamWebMapper).toGenericMessageResponse(result);
      verifyNoMoreInteractions(iamWebMapper, verifyEmailUseCase);
    }
  }

  @Nested
  @DisplayName("Login")
  class LoginTests {

    @Test
    @DisplayName("Deve retornar 200, serializar resposta e configurar cookie de refresh token")
    void shouldReturnOkSerializeResponseAndSetRefreshTokenCookie() throws Exception {
      // 1. Arrange
      var request = new LoginRequestDto("user@email.com", "secure-password");
      var clientIp = "203.0.113.10";
      var command = new LoginCommand(request.email(), request.password(), clientIp);
      var result = loginResult("raw-refresh-token", "jwt-access-token");
      var response = loginResponseDto();

      given(clientIpResolver.resolve(any(HttpServletRequest.class))).willReturn(clientIp);
      given(iamWebMapper.toLoginCommand(request, clientIp)).willReturn(command);
      given(loginUseCase.execute(command)).willReturn(result);
      given(iamWebMapper.toLoginResponse(result)).willReturn(response);

      // 2. Act
      ResultActions resultActions = mockMvc.perform(jsonPost("/login", request));

      // 3. Assert
      resultActions
              .andExpect(status().isOk())
              .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
              .andExpect(jsonPath("$.accessToken").value(response.accessToken()))
              .andExpect(jsonPath("$.tokenType").value(response.tokenType()))
              .andExpect(jsonPath("$.expiresInSeconds").value(response.expiresInSeconds()))
              .andExpect(jsonPath("$.user.id").value(response.user().id().toString()))
              .andExpect(jsonPath("$.user.email").value(response.user().email()))
              .andExpect(jsonPath("$.user.roles[0]").value("USER"))
              .andExpect(refreshTokenCookie("raw-refresh-token", "604800"));

      verify(clientIpResolver).resolve(any(HttpServletRequest.class));
      verify(iamWebMapper).toLoginCommand(request, clientIp);
      verify(loginUseCase).execute(command);
      verify(iamWebMapper).toLoginResponse(result);
      verifyNoMoreInteractions(clientIpResolver, iamWebMapper, loginUseCase);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            missing email | {"password":"secure-password"} | email
            blank email | {"email":"","password":"secure-password"} | email
            invalid email | {"email":"invalid-email","password":"secure-password"} | email
            missing password | {"email":"user@email.com"} | password
            short password | {"email":"user@email.com","password":"123"} | password
            """)
    @DisplayName("Deve retornar 400 com ProblemDetail quando login request for inválido")
    void shouldReturnBadRequestProblemDetailWhenLoginRequestIsInvalid(
            String scenario,
            String body,
            String invalidField
    ) throws Exception {
      // 1. Arrange

      // 2. Act
      ResultActions resultActions = mockMvc.perform(post(AUTH_BASE_PATH + "/login")
              .contentType(MediaType.APPLICATION_JSON)
              .content(body));

      // 3. Assert
      resultActions
              .andExpect(status().isBadRequest())
              .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
              .andExpect(jsonPath("$.type").value(ProblemType.VALIDATION))
              .andExpect(jsonPath("$.title").value("Validação"))
              .andExpect(jsonPath("$.status").value(400))
              .andExpect(jsonPath("$.detail").value(CommonErrorCode.REQUEST_VALIDATION_FAILED.getMessage()))
              .andExpect(jsonPath("$.errorCode").value(CommonErrorCode.REQUEST_VALIDATION_FAILED.getCode()))
              .andExpect(jsonPath("$.errors[?(@.field == '%s')]".formatted(invalidField)).exists())
              .andExpect(result -> {
                assertThat(scenario).isNotBlank();
                assertThat(result.getResolvedException())
                        .isInstanceOf(MethodArgumentNotValidException.class);

                var exception = (MethodArgumentNotValidException) result.getResolvedException();
                assertThat(exception.getBindingResult().getFieldErrors())
                        .extracting("field")
                        .contains(invalidField);
              });

      verifyNoInteractions(iamWebMapper, loginUseCase);
    }

    @Test
    @DisplayName("Deve retornar 429 com ProblemDetail quando rate limit do login for excedido")
    void shouldReturnTooManyRequestsProblemDetailWhenLoginRateLimitIsExceeded() throws Exception {
      // 1. Arrange
      var request = new LoginRequestDto("user@email.com", "secure-password");
      var clientIp = "203.0.113.10";
      var command = new LoginCommand(request.email(), request.password(), clientIp);

      given(clientIpResolver.resolve(any(HttpServletRequest.class))).willReturn(clientIp);
      given(iamWebMapper.toLoginCommand(request, clientIp)).willReturn(command);
      doThrow(new TooManyRequestsException(Duration.ofSeconds(30)))
              .when(loginUseCase).execute(command);

      // 2. Act
      ResultActions resultActions = mockMvc.perform(jsonPost("/login", request));

      // 3. Assert
      resultActions
              .andExpect(status().isTooManyRequests())
              .andExpect(header().string(HttpHeaders.RETRY_AFTER, "30"))
              .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
              .andExpect(jsonPath("$.type").value(ProblemType.TOO_MANY_REQUESTS))
              .andExpect(jsonPath("$.title").value("Muitas requisições"))
              .andExpect(jsonPath("$.status").value(429))
              .andExpect(jsonPath("$.detail").value(CommonErrorCode.TOO_MANY_REQUESTS.getMessage()))
              .andExpect(jsonPath("$.errorCode").value(CommonErrorCode.TOO_MANY_REQUESTS.getCode()));

      verify(clientIpResolver).resolve(any(HttpServletRequest.class));
      verify(iamWebMapper).toLoginCommand(request, clientIp);
      verify(loginUseCase).execute(command);
      verifyNoMoreInteractions(clientIpResolver, iamWebMapper, loginUseCase);
    }
  }

  @Nested
  @DisplayName("Refresh token")
  class RefreshTests {

    @Test
    @DisplayName("Deve retornar 200, serializar novo access token e configurar novo cookie")
    void shouldReturnOkSerializeNewAccessTokenAndSetNewRefreshTokenCookie() throws Exception {
      // 1. Arrange
      var refreshToken = "current-refresh-token";
      var command = new RefreshTokenCommand(refreshToken);
      var result = new RefreshTokenResult("new-refresh-token", "new-access-token");
      var response = new RefreshTokenResponseDto(result.newAccessToken());

      given(iamWebMapper.toRefreshTokenCommand(refreshToken)).willReturn(command);
      given(refreshTokenUseCase.execute(command)).willReturn(result);
      given(iamWebMapper.toRefreshTokenResponse(result)).willReturn(response);

      // 2. Act
      ResultActions resultActions = mockMvc.perform(post(AUTH_BASE_PATH + "/refresh")
              .cookie(new Cookie("refreshToken", refreshToken)));

      // 3. Assert
      resultActions
              .andExpect(status().isOk())
              .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
              .andExpect(jsonPath("$.newAccessToken").value(response.newAccessToken()))
              .andExpect(refreshTokenCookie("new-refresh-token", "604800"));

      verify(iamWebMapper).toRefreshTokenCommand(refreshToken);
      verify(refreshTokenUseCase).execute(command);
      verify(iamWebMapper).toRefreshTokenResponse(result);
      verifyNoMoreInteractions(refreshTokenUseCase, iamWebMapper);
    }

    @Test
    @DisplayName("Deve retornar 401 com ProblemDetail quando cookie de refresh token estiver ausente")
    void shouldReturnUnauthorizedProblemDetailWhenRefreshTokenCookieIsMissing() throws Exception {
      // 1. Arrange

      // 2. Act
      ResultActions resultActions = mockMvc.perform(post(AUTH_BASE_PATH + "/refresh"));

      // 3. Assert
      resultActions
              .andExpect(status().isUnauthorized())
              .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
              .andExpect(jsonPath("$.type").value(ProblemType.UNAUTHORIZED))
              .andExpect(jsonPath("$.title").value("Não autorizado"))
              .andExpect(jsonPath("$.status").value(401))
              .andExpect(jsonPath("$.detail").value(IamErrorCode.AUTH_REFRESH_TOKEN_INVALID.getMessage()))
              .andExpect(jsonPath("$.errorCode").value(IamErrorCode.AUTH_REFRESH_TOKEN_INVALID.getCode()));

      verifyNoInteractions(refreshTokenUseCase, iamWebMapper);
    }
  }

  @Nested
  @DisplayName("Logout")
  class LogoutTests {

    @Test
    @DisplayName("Deve exigir autenticação para logout")
    void shouldRequireAuthenticationForLogout() throws Exception {
      // 1. Arrange

      // 2. Act
      ResultActions resultActions = mockMvc.perform(post(AUTH_BASE_PATH + "/logout"));

      // 3. Assert
      resultActions
              .andExpect(status().isUnauthorized())
              .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
              .andExpect(jsonPath("$.type").value(ProblemType.UNAUTHORIZED))
              .andExpect(jsonPath("$.title").value("Não autorizado"))
              .andExpect(jsonPath("$.status").value(401))
              .andExpect(jsonPath("$.detail").value(CommonErrorCode.AUTH_UNAUTHORIZED.getMessage()))
              .andExpect(jsonPath("$.errorCode").value(CommonErrorCode.AUTH_UNAUTHORIZED.getCode()));

      verifyNoInteractions(logoutUseCase);
    }

    @Test
    @DisplayName("Deve retornar 204, delegar logout e expirar cookie de refresh token")
    void shouldReturnNoContentDelegateLogoutAndExpireRefreshTokenCookie() throws Exception {
      // 1. Arrange
      var refreshToken = "raw-refresh-token";
      var command = new LogoutCommand(refreshToken);
      given(iamWebMapper.toLogoutCommand(refreshToken)).willReturn(command);

      // 2. Act
      ResultActions resultActions = mockMvc.perform(post(AUTH_BASE_PATH + "/logout")
              .with(jwt())
              .cookie(new Cookie("refreshToken", refreshToken)));

      // 3. Assert
      resultActions
              .andExpect(status().isNoContent())
              .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refreshToken=")))
              .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/api/v1/auth")))
              .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")))
              .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Secure")))
              .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
              .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Strict")));

      verify(iamWebMapper).toLogoutCommand(refreshToken);
      verify(logoutUseCase).execute(command);
      verifyNoMoreInteractions(iamWebMapper, logoutUseCase);
    }
  }

  @Nested
  @DisplayName("Reenvio de verificação")
  class ResendTests {

    @Test
    @DisplayName("Deve exigir autenticação para reenviar verificação")
    void shouldRequireAuthenticationForResendVerification() throws Exception {
      // 1. Arrange
      var request = new ResendVerificationRequestDto("user@email.com");

      // 2. Act
      ResultActions resultActions = mockMvc.perform(jsonPost("/resend-verification", request));

      // 3. Assert
      resultActions
              .andExpect(status().isUnauthorized())
              .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
              .andExpect(jsonPath("$.errorCode").value(CommonErrorCode.AUTH_UNAUTHORIZED.getCode()));

      verifyNoInteractions(iamWebMapper, resendVerificationUseCase);
    }

    @Test
    @DisplayName("Deve retornar 200 e delegar reenvio quando autenticado")
    void shouldReturnOkAndDelegateResendVerificationWhenAuthenticated() throws Exception {
      // 1. Arrange
      var request = new ResendVerificationRequestDto("user@email.com");
      var command = new ResendVerificationCommand(request.email());
      var result = new ResendVerificationResult("Código reenviado com sucesso.");
      var response = new GenericMessageResponseDto(result.message());

      given(iamWebMapper.toResendVerificationCommand(request)).willReturn(command);
      given(resendVerificationUseCase.execute(command)).willReturn(result);
      given(iamWebMapper.toGenericMessageResponse(result)).willReturn(response);

      // 2. Act
      ResultActions resultActions = mockMvc.perform(jsonPost("/resend-verification", request).with(jwt()));

      // 3. Assert
      resultActions
              .andExpect(status().isOk())
              .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
              .andExpect(jsonPath("$.message").value(response.message()));

      verify(iamWebMapper).toResendVerificationCommand(request);
      verify(resendVerificationUseCase).execute(command);
      verify(iamWebMapper).toGenericMessageResponse(result);
      verifyNoMoreInteractions(iamWebMapper, resendVerificationUseCase);
    }
  }

  private MockHttpServletRequestBuilder jsonPost(String path, Object body) {
    return post(AUTH_BASE_PATH + path)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body));
  }

  private ResultMatcher refreshTokenCookie(String value, String maxAge) {
    return result -> {
      String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);

      assertThat(setCookie)
              .contains("refreshToken=" + value)
              .contains("Path=/api/v1/auth")
              .contains("Max-Age=" + maxAge)
              .contains("Secure")
              .contains("HttpOnly")
              .contains("SameSite=Strict");
    };
  }

  private LoginResult loginResult(String refreshToken, String accessToken) {
    return new LoginResult(
            refreshToken,
            accessToken,
            "Bearer",
            3_600L,
            new AuthenticatedUserResult(
                    userId(),
                    "User Name",
                    "user@email.com",
                    List.of("USER"),
                    List.of("ROLE_USER"),
                    "FREE"
            )
    );
  }

  private LoginResponseDto loginResponseDto() {
    return new LoginResponseDto(
            "jwt-access-token",
            "Bearer",
            3_600L,
            new AuthenticatedUserResponseDto(
                    userId(),
                    "User Name",
                    "user@email.com",
                    "FREE",
                    List.of("USER")
            )
    );
  }

  private UUID userId() {
    return UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac123");
  }
}
