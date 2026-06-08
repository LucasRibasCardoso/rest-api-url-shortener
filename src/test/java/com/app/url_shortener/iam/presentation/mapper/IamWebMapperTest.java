package com.app.url_shortener.iam.presentation.mapper;

import com.app.url_shortener.iam.application.result.AuthenticatedUserResult;
import com.app.url_shortener.iam.application.result.LoginResult;
import com.app.url_shortener.iam.application.result.RefreshTokenResult;
import com.app.url_shortener.iam.application.result.RegisterUserResult;
import com.app.url_shortener.iam.application.result.ResendVerificationResult;
import com.app.url_shortener.iam.application.result.VerifyEmailResult;
import com.app.url_shortener.iam.domain.valueobject.VerificationCode;
import com.app.url_shortener.iam.presentation.dto.request.LoginRequestDto;
import com.app.url_shortener.iam.presentation.dto.request.RegisterRequestDto;
import com.app.url_shortener.iam.presentation.dto.request.ResendVerificationRequest;
import com.app.url_shortener.iam.presentation.dto.request.VerifyEmailRequestDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@Tag("unit")
@DisplayName("Testes de Unidade - IamWebMapper")
class IamWebMapperTest {

  private final IamWebMapper mapper = Mappers.getMapper(IamWebMapper.class);

  @Nested
  @DisplayName("Mapeamento para Comandos")
  class ToCommandTests {

    @Test
    @DisplayName("Deve mapear request de cadastro para comando")
    void shouldMapRegisterRequestToCommand() {
      // 1. Arrange
      var request = new RegisterRequestDto(
              "  Maria   Silva  ",
              "  MARIA@EMAIL.COM  ",
              "secure-password"
      );

      // 2. Act
      var command = mapper.toRegisterUserCommand(request);

      // 3. Assert
      assertAll(
              () -> assertThat(command.name()).isEqualTo("Maria Silva"),
              () -> assertThat(command.email()).isEqualTo("maria@email.com"),
              () -> assertThat(command.password()).isEqualTo("secure-password")
      );
    }

    @Test
    @DisplayName("Deve mapear request de verificação de email para comando com código")
    void shouldMapVerifyEmailRequestToCommandWithVerificationCode() {
      // 1. Arrange
      var request = new VerifyEmailRequestDto("  USER@EMAIL.COM  ", "123456");

      // 2. Act
      var command = mapper.toVerifyEmailCommand(request);

      // 3. Assert
      assertAll(
              () -> assertThat(command.email()).isEqualTo("user@email.com"),
              () -> assertThat(command.code()).isEqualTo(VerificationCode.of("123456"))
      );
    }

    @Test
    @DisplayName("Deve mapear request de login para comando")
    void shouldMapLoginRequestToCommand() {
      // 1. Arrange
      var request = new LoginRequestDto("  USER@EMAIL.COM  ", "secure-password");
      var clientIp = "203.0.113.10";

      // 2. Act
      var command = mapper.toLoginCommand(request, clientIp);

      // 3. Assert
      assertAll(
              () -> assertThat(command.email()).isEqualTo("user@email.com"),
              () -> assertThat(command.password()).isEqualTo("secure-password"),
              () -> assertThat(command.clientIp()).isEqualTo(clientIp)
      );
    }

    @Test
    @DisplayName("Deve mapear request de reenvio de verificação para comando")
    void shouldMapResendVerificationRequestToCommand() {
      // 1. Arrange
      var request = new ResendVerificationRequest("  USER@EMAIL.COM  ");

      // 2. Act
      var command = mapper.toResendVerificationCommand(request);

      // 3. Assert
      assertThat(command.email()).isEqualTo("user@email.com");
    }
  }

  @Nested
  @DisplayName("Mapeamento para Respostas")
  class ToResponseTests {

    @Test
    @DisplayName("Deve mapear resultado de cadastro para resposta genérica")
    void shouldMapRegisterUserResultToGenericResponse() {
      // 1. Arrange
      var result = new RegisterUserResult("Usuário cadastrado com sucesso.");

      // 2. Act
      var response = mapper.toGenericMessageResponse(result);

      // 3. Assert
      assertThat(response.message()).isEqualTo(result.message());
    }

    @Test
    @DisplayName("Deve mapear resultado de verificação de email para resposta genérica")
    void shouldMapVerifyEmailResultToGenericResponse() {
      // 1. Arrange
      var result = new VerifyEmailResult("Email verificado com sucesso.");

      // 2. Act
      var response = mapper.toGenericMessageResponse(result);

      // 3. Assert
      assertThat(response.message()).isEqualTo(result.message());
    }

    @Test
    @DisplayName("Deve mapear resultado de reenvio de verificação para resposta genérica")
    void shouldMapResendVerificationResultToGenericResponse() {
      // 1. Arrange
      var result = new ResendVerificationResult("Código reenviado com sucesso.");

      // 2. Act
      var response = mapper.toGenericMessageResponse(result);

      // 3. Assert
      assertThat(response.message()).isEqualTo(result.message());
    }

    @Test
    @DisplayName("Deve mapear resultado de login para resposta sem refresh token")
    void shouldMapLoginResultToResponseWithoutRefreshToken() {
      // 1. Arrange
      var userId = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac123");
      var authenticatedUser = new AuthenticatedUserResult(
              userId,
              "User Name",
              "user@email.com",
              List.of("USER", "ADMIN"),
              List.of("ROLE_USER", "ROLE_ADMIN", "url:create"),
              "PREMIUM"
      );
      var result = new LoginResult(
              "raw-refresh-token",
              "jwt-access-token",
              "Bearer",
              3_600L,
              authenticatedUser
      );

      // 2. Act
      var response = mapper.toLoginResponse(result);

      // 3. Assert
      assertAll(
              () -> assertThat(response.accessToken()).isEqualTo(result.accessToken()),
              () -> assertThat(response.tokenType()).isEqualTo(result.tokenType()),
              () -> assertThat(response.expiresInSeconds()).isEqualTo(result.expiresInSeconds()),
              () -> assertThat(response.user().id()).isEqualTo(userId),
              () -> assertThat(response.user().name()).isEqualTo(authenticatedUser.name()),
              () -> assertThat(response.user().email()).isEqualTo(authenticatedUser.email()),
              () -> assertThat(response.user().plan()).isEqualTo(authenticatedUser.plan()),
              () -> assertThat(response.user().roles()).containsExactly("USER", "ADMIN")
      );
    }

    @Test
    @DisplayName("Deve mapear resultado de refresh token para resposta")
    void shouldMapRefreshTokenResultToResponse() {
      // 1. Arrange
      var result = new RefreshTokenResult("new-refresh-token", "new-access-token");

      // 2. Act
      var response = mapper.toRefreshTokenResponse(result);

      // 3. Assert
      assertThat(response.newAccessToken()).isEqualTo(result.newAccessToken());
    }
  }

  @Nested
  @DisplayName("Conversão de Código de Verificação")
  class VerificationCodeMappingTests {

    @Test
    @DisplayName("Deve converter string para código de verificação")
    void shouldMapStringToVerificationCode() {
      // 1. Arrange
      var code = "123456";

      // 2. Act
      var result = mapper.toVerificationCode(code);

      // 3. Assert
      assertThat(result).isEqualTo(VerificationCode.of(code));
    }

    @Test
    @DisplayName("Deve converter código de verificação para string")
    void shouldMapVerificationCodeToString() {
      // 1. Arrange
      var verificationCode = VerificationCode.of("123456");

      // 2. Act
      var result = mapper.toVerificationCodeValue(verificationCode);

      // 3. Assert
      assertThat(result).isEqualTo("123456");
    }

    @Test
    @DisplayName("Deve retornar nulo ao converter código de verificação nulo")
    void shouldReturnNullWhenMappingNullVerificationCode() {
      // 1. Arrange
      VerificationCode verificationCode = null;

      // 2. Act
      var result = mapper.toVerificationCodeValue(verificationCode);

      // 3. Assert
      assertThat(result).isNull();
    }

    @Test
    @DisplayName("Deve retornar nulo ao converter string nula para código de verificação")
    void shouldReturnNullWhenMappingNullStringToVerificationCode() {
      // 1. Arrange
      String code = null;

      // 2. Act
      var result = mapper.toVerificationCode(code);

      // 3. Assert
      assertThat(result).isNull();
    }
  }
}
