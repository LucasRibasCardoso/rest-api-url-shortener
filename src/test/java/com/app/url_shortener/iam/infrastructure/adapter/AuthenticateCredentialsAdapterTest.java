package com.app.url_shortener.iam.infrastructure.adapter;

import com.app.url_shortener.iam.application.result.AuthenticatedUserResult;
import com.app.url_shortener.iam.domain.enums.PlanType;
import com.app.url_shortener.iam.domain.enums.UserStatus;
import com.app.url_shortener.iam.domain.exception.auth.AccountPendingVerificationException;
import com.app.url_shortener.iam.domain.exception.auth.InvalidCredentialsException;
import com.app.url_shortener.iam.domain.exception.user.UserAccountDisabledException;
import com.app.url_shortener.iam.domain.exception.user.UserAccountLockedException;
import com.app.url_shortener.security.principal.UserPrincipal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AccountExpiredException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("Testes de Unidade - Adaptador de Autenticação de Credenciais")
class AuthenticateCredentialsAdapterTest {

  @Mock
  private AuthenticationManager authenticationManager;

  @Captor
  private ArgumentCaptor<Authentication> authenticationCaptor;

  @InjectMocks
  private AuthenticateCredentialsAdapter adapter;

  @Nested
  @DisplayName("Autenticação")
  class AuthenticateTests {

    @Test
    @DisplayName("Deve autenticar credenciais e retornar usuário autenticado com roles extraídas das authorities")
    void shouldAuthenticateCredentialsAndReturnAuthenticatedUserWithRolesExtractedFromAuthorities() {
      // 1. Arrange
      var email = "user@email.com";
      var password = "secure-password";
      var principal = userPrincipal();
      var authentication = new UsernamePasswordAuthenticationToken(
              principal,
              null,
              principal.getAuthorities()
      );

      given(authenticationManager.authenticate(authenticationCaptor.capture())).willReturn(authentication);

      // 2. Act
      AuthenticatedUserResult result = adapter.authenticate(email, password);

      // 3. Assert
      assertAll(
              () -> assertThat(result.id()).isEqualTo(principal.getId()),
              () -> assertThat(result.name()).isEqualTo(principal.getName()),
              () -> assertThat(result.email()).isEqualTo(principal.getEmail()),
              () -> assertThat(result.roles()).containsExactly("USER", "ADMIN"),
              () -> assertThat(result.authorities())
                      .containsExactly("ROLE_USER", "url:create", "ROLE_ADMIN", "url:read"),
              () -> assertThat(result.plan()).isEqualTo(PlanType.PREMIUM.name())
      );

      var capturedAuthentication = authenticationCaptor.getValue();

      assertAll(
              () -> assertThat(capturedAuthentication)
                      .isInstanceOf(UsernamePasswordAuthenticationToken.class),
              () -> assertThat(capturedAuthentication.getPrincipal()).isEqualTo(email),
              () -> assertThat(capturedAuthentication.getCredentials()).isEqualTo(password)
      );

      verify(authenticationManager).authenticate(capturedAuthentication);
      verifyNoMoreInteractions(authenticationManager);
    }

    @Test
    @DisplayName("Deve lançar credenciais inválidas quando o principal autenticado não for UserPrincipal")
    void shouldThrowInvalidCredentialsWhenAuthenticatedPrincipalIsNotUserPrincipal() {
      // 1. Arrange
      var email = "user@email.com";
      var password = "secure-password";
      var authentication = new UsernamePasswordAuthenticationToken(
              "unexpected-principal",
              null,
              List.of()
      );

      given(authenticationManager.authenticate(authenticationCaptor.capture())).willReturn(authentication);

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> adapter.authenticate(email, password));

      // 3. Assert
      throwableAssert
              .isInstanceOf(InvalidCredentialsException.class)
              .hasMessage("Credenciais inválidas.");

      verify(authenticationManager).authenticate(authenticationCaptor.getValue());
      verifyNoMoreInteractions(authenticationManager);
    }

    @ParameterizedTest
    @MethodSource("authenticationExceptions")
    @DisplayName("Deve converter exceções do Spring Security para exceções de domínio")
    void shouldConvertSpringSecurityExceptionsToDomainExceptions(
            AuthenticationException springException,
            Class<? extends RuntimeException> expectedExceptionType,
            String expectedMessage
    ) {
      // 1. Arrange
      var email = "user@email.com";
      var password = "secure-password";

      given(authenticationManager.authenticate(authenticationCaptor.capture())).willThrow(springException);

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> adapter.authenticate(email, password));

      // 3. Assert
      throwableAssert
              .isInstanceOf(expectedExceptionType)
              .hasMessage(expectedMessage);

      verify(authenticationManager).authenticate(authenticationCaptor.getValue());
      verifyNoMoreInteractions(authenticationManager);
    }

    static Stream<Arguments> authenticationExceptions() {
      return Stream.of(
              Arguments.of(
                      new AccountExpiredException("account expired"),
                      UserAccountDisabledException.class,
                      "Conta desabilitada."
              ),
              Arguments.of(
                      new DisabledException("account disabled"),
                      AccountPendingVerificationException.class,
                      "Conta pendente de verificação."
              ),
              Arguments.of(
                      new LockedException("account locked"),
                      UserAccountLockedException.class,
                      "Conta bloqueada."
              ),
              Arguments.of(
                      new BadCredentialsException("bad credentials"),
                      InvalidCredentialsException.class,
                      "Credenciais inválidas."
              )
      );
    }
  }

  private UserPrincipal userPrincipal() {
    return new UserPrincipal(
            UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac123"),
            "User Name",
            "user@email.com",
            "encoded-password",
            PlanType.PREMIUM,
            UserStatus.ACTIVE,
            List.of(
                    new SimpleGrantedAuthority("ROLE_USER"),
                    new SimpleGrantedAuthority("url:create"),
                    new SimpleGrantedAuthority("ROLE_ADMIN"),
                    new SimpleGrantedAuthority("url:read")
            )
    );
  }
}
