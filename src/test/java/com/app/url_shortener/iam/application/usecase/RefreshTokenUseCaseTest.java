package com.app.url_shortener.iam.application.usecase;

import com.app.url_shortener.iam.application.command.RefreshTokenCommand;
import com.app.url_shortener.iam.application.port.output.IssueAccessTokenPort;
import com.app.url_shortener.iam.application.port.output.RefreshTokenRepositoryPort;
import com.app.url_shortener.iam.application.port.output.SecureTokenGeneratorPort;
import com.app.url_shortener.iam.application.port.output.UserAccountRepositoryPort;
import com.app.url_shortener.iam.application.result.AuthenticatedUserResult;
import com.app.url_shortener.iam.application.service.RefreshTokenSecurityService;
import com.app.url_shortener.iam.application.usecase.impl.RefreshTokenUseCaseImpl;
import com.app.url_shortener.iam.domain.enums.PlanType;
import com.app.url_shortener.iam.domain.enums.UserStatus;
import com.app.url_shortener.iam.domain.exception.auth.RefreshTokenExpiredException;
import com.app.url_shortener.iam.domain.exception.auth.TokenCompromisedException;
import com.app.url_shortener.iam.domain.exception.user.UserNotFoundException;
import com.app.url_shortener.iam.domain.model.Permission;
import com.app.url_shortener.iam.domain.model.RefreshToken;
import com.app.url_shortener.iam.domain.model.Role;
import com.app.url_shortener.iam.domain.model.UserAccount;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("Testes de Unidade - Caso de Uso Refresh Token")
class RefreshTokenUseCaseTest {

  @Mock
  private RefreshTokenRepositoryPort tokenRepository;

  @Mock
  private SecureTokenGeneratorPort tokenGenerator;

  @Mock
  private IssueAccessTokenPort accessTokenPort;

  @Mock
  private UserAccountRepositoryPort userRepository;

  @Mock
  private RefreshTokenSecurityService refreshTokenSecurityService;

  @Captor
  private ArgumentCaptor<RefreshToken> refreshTokenCaptor;

  @Captor
  private ArgumentCaptor<AuthenticatedUserResult> authenticatedUserCaptor;

  @InjectMocks
  private RefreshTokenUseCaseImpl refreshTokenUseCase;

  @Nested
  @DisplayName("Execução da renovação de token")
  class ExecuteTests {

    @Test
    @DisplayName("Deve rotacionar refresh token ativo, salvar tokens e emitir novo access token")
    void shouldRotateActiveRefreshTokenSaveTokensAndIssueNewAccessToken() {
      // 1. Arrange
      var userId = UUID.fromString("019a1744-9a1f-7f0b-b7a4-9b2ab723c001");
      var command = new RefreshTokenCommand("raw-refresh-token");
      var incomingHash = "incoming-refresh-token-hash";
      var newRawToken = "new-raw-refresh-token";
      var newTokenHash = "new-refresh-token-hash";
      var newAccessToken = "new-jwt-access-token";
      var oldToken = RefreshToken.create(userId, incomingHash);
      var userAccount = activeUserAccountWithRoles(userId);

      given(tokenGenerator.hashToken(command.refreshToken())).willReturn(incomingHash);
      given(tokenRepository.findByTokenHash(incomingHash)).willReturn(Optional.of(oldToken));
      given(tokenGenerator.generateRandomToken()).willReturn(newRawToken);
      given(tokenGenerator.hashToken(newRawToken)).willReturn(newTokenHash);
      given(tokenRepository.markTokenAsRotatedIfActive(
              eq(incomingHash),
              any(Instant.class),
              any(UUID.class)
      )).willReturn(1);
      given(userRepository.findByIdWithRolesAndPermissions(userId)).willReturn(Optional.of(userAccount));
      given(accessTokenPort.getToken(any(AuthenticatedUserResult.class))).willReturn(newAccessToken);

      // 2. Act
      var result = refreshTokenUseCase.execute(command);

      // 3. Assert
      assertAll(
              () -> assertThat(result.newRefreshToken()).isEqualTo(newRawToken),
              () -> assertThat(result.newAccessToken()).isEqualTo(newAccessToken)
      );

      verify(tokenRepository).save(refreshTokenCaptor.capture());
      var savedNewToken = refreshTokenCaptor.getValue();

      assertAll(
              () -> assertThat(savedNewToken.getUserId()).isEqualTo(userId),
              () -> assertThat(savedNewToken.getTokenHash()).isEqualTo(newTokenHash),
              () -> assertThat(savedNewToken.isRevoked()).isFalse()
      );

      verify(tokenRepository).markTokenAsRotatedIfActive(
              eq(incomingHash),
              any(Instant.class),
              eq(savedNewToken.getId())
      );
      verify(accessTokenPort).getToken(authenticatedUserCaptor.capture());
      var authenticatedUser = authenticatedUserCaptor.getValue();

      assertAll(
              () -> assertThat(authenticatedUser.id()).isEqualTo(userId),
              () -> assertThat(authenticatedUser.name()).isEqualTo("User Name"),
              () -> assertThat(authenticatedUser.email()).isEqualTo("user@email.com"),
              () -> assertThat(authenticatedUser.plan()).isEqualTo("FREE"),
              () -> assertThat(authenticatedUser.roles()).containsExactlyInAnyOrder("USER", "ADMIN"),
              () -> assertThat(authenticatedUser.authorities())
                      .containsExactlyInAnyOrder("url:create", "url:read", "user:manage")
      );

      verify(tokenGenerator).hashToken(command.refreshToken());
      verify(tokenRepository).findByTokenHash(incomingHash);
      verify(tokenGenerator).generateRandomToken();
      verify(tokenGenerator).hashToken(newRawToken);
      verify(userRepository).findByIdWithRolesAndPermissions(userId);
      verify(refreshTokenSecurityService, never()).revokeAllTokensDueToCompromise(any());
      verifyNoMoreInteractions(
              tokenRepository,
              tokenGenerator,
              accessTokenPort,
              userRepository,
              refreshTokenSecurityService
      );
    }

    @Test
    @DisplayName("Deve lançar exceção quando o refresh token não for encontrado")
    void shouldThrowExceptionWhenRefreshTokenIsNotFound() {
      // 1. Arrange
      var command = new RefreshTokenCommand("unknown-refresh-token");
      var incomingHash = "unknown-refresh-token-hash";

      given(tokenGenerator.hashToken(command.refreshToken())).willReturn(incomingHash);
      given(tokenRepository.findByTokenHash(incomingHash)).willReturn(Optional.empty());

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> refreshTokenUseCase.execute(command));

      // 3. Assert
      throwableAssert
              .isInstanceOf(RefreshTokenExpiredException.class)
              .hasMessage("Refresh token expirado.");

      verify(tokenGenerator).hashToken(command.refreshToken());
      verify(tokenRepository).findByTokenHash(incomingHash);
      verifyNoInteractions(accessTokenPort, userRepository, refreshTokenSecurityService);
      verifyNoMoreInteractions(tokenRepository, tokenGenerator);
    }

    @Test
    @DisplayName("Deve propagar exceção e não consultar repositório quando o hash do refresh token falhar")
    void shouldPropagateExceptionAndNotQueryRepositoryWhenRefreshTokenHashingFails() {
      // 1. Arrange
      var command = new RefreshTokenCommand("invalid-refresh-token");
      var exception = new IllegalArgumentException("Refresh token inválido.");

      given(tokenGenerator.hashToken(command.refreshToken())).willThrow(exception);

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> refreshTokenUseCase.execute(command));

      // 3. Assert
      throwableAssert
              .isInstanceOf(IllegalArgumentException.class)
              .hasMessage("Refresh token inválido.");

      verify(tokenGenerator).hashToken(command.refreshToken());
      verifyNoInteractions(tokenRepository, accessTokenPort, userRepository);
      verifyNoMoreInteractions(tokenGenerator);
    }

    @Test
    @DisplayName("Deve revogar todos os tokens do usuário e lançar exceção quando o refresh token já estiver revogado")
    void shouldRevokeAllUserTokensAndThrowExceptionWhenRefreshTokenIsAlreadyRevoked() {
      // 1. Arrange
      var userId = UUID.fromString("019a1744-9a1f-7f0b-b7a4-9b2ab723c002");
      var command = new RefreshTokenCommand("revoked-refresh-token");
      var incomingHash = "revoked-refresh-token-hash";
      var revokedToken = revokedRefreshToken(userId, incomingHash);

      given(tokenGenerator.hashToken(command.refreshToken())).willReturn(incomingHash);
      given(tokenRepository.findByTokenHash(incomingHash)).willReturn(Optional.of(revokedToken));

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> refreshTokenUseCase.execute(command));

      // 3. Assert
      throwableAssert
              .isInstanceOf(TokenCompromisedException.class)
              .hasMessage("Refresh token comprometido.");

      verify(tokenGenerator).hashToken(command.refreshToken());
      verify(tokenRepository).findByTokenHash(incomingHash);
      verify(refreshTokenSecurityService).revokeAllTokensDueToCompromise(userId);
      verify(tokenRepository, never()).save(any(RefreshToken.class));
      verify(tokenRepository, never()).markTokenAsRotatedIfActive(any(), any(), any());
      verify(accessTokenPort, never()).getToken(any());
      verifyNoInteractions(accessTokenPort, userRepository);
      verifyNoMoreInteractions(tokenRepository, tokenGenerator, refreshTokenSecurityService);
    }

    @Test
    @DisplayName("Deve lançar exceção e não salvar tokens quando o refresh token estiver expirado")
    void shouldThrowExceptionAndNotSaveTokensWhenRefreshTokenIsExpired() {
      // 1. Arrange
      var userId = UUID.fromString("019a1744-9a1f-7f0b-b7a4-9b2ab723c003");
      var command = new RefreshTokenCommand("expired-refresh-token");
      var incomingHash = "expired-refresh-token-hash";
      var expiredToken = expiredRefreshToken(userId, incomingHash);

      given(tokenGenerator.hashToken(command.refreshToken())).willReturn(incomingHash);
      given(tokenRepository.findByTokenHash(incomingHash)).willReturn(Optional.of(expiredToken));

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> refreshTokenUseCase.execute(command));

      // 3. Assert
      throwableAssert
              .isInstanceOf(RefreshTokenExpiredException.class)
              .hasMessage("Refresh token expirado.");

      verify(tokenGenerator).hashToken(command.refreshToken());
      verify(tokenRepository).findByTokenHash(incomingHash);
      verify(tokenGenerator, never()).generateRandomToken();
      verify(refreshTokenSecurityService, never()).revokeAllTokensDueToCompromise(any());
      verify(tokenRepository, never()).save(any(RefreshToken.class));
      verify(tokenRepository, never()).markTokenAsRotatedIfActive(any(), any(), any());
      verifyNoInteractions(accessTokenPort, userRepository);
      verifyNoMoreInteractions(tokenRepository, tokenGenerator, refreshTokenSecurityService);
    }

    @Test
    @DisplayName("Deve propagar exceção quando o usuário do refresh token não for encontrado")
    void shouldPropagateExceptionWhenRefreshTokenUserIsNotFound() {
      // 1. Arrange
      var userId = UUID.fromString("019a1744-9a1f-7f0b-b7a4-9b2ab723c004");
      var command = new RefreshTokenCommand("raw-refresh-token");
      var incomingHash = "incoming-refresh-token-hash";
      var newRawToken = "new-raw-refresh-token";
      var newTokenHash = "new-refresh-token-hash";
      var oldToken = RefreshToken.create(userId, incomingHash);

      given(tokenGenerator.hashToken(command.refreshToken())).willReturn(incomingHash);
      given(tokenRepository.findByTokenHash(incomingHash)).willReturn(Optional.of(oldToken));
      given(tokenGenerator.generateRandomToken()).willReturn(newRawToken);
      given(tokenGenerator.hashToken(newRawToken)).willReturn(newTokenHash);
      given(tokenRepository.markTokenAsRotatedIfActive(
              eq(incomingHash),
              any(Instant.class),
              any(UUID.class)
      )).willReturn(1);
      given(userRepository.findByIdWithRolesAndPermissions(userId)).willReturn(Optional.empty());

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> refreshTokenUseCase.execute(command));

      // 3. Assert
      throwableAssert
              .isInstanceOf(UserNotFoundException.class)
              .hasMessage("Usuário não encontrado.");

      verify(tokenRepository).save(refreshTokenCaptor.capture());
      verify(tokenRepository).markTokenAsRotatedIfActive(
              eq(incomingHash),
              any(Instant.class),
              eq(refreshTokenCaptor.getValue().getId())
      );
      verify(tokenGenerator).hashToken(command.refreshToken());
      verify(tokenRepository).findByTokenHash(incomingHash);
      verify(tokenGenerator).generateRandomToken();
      verify(tokenGenerator).hashToken(newRawToken);
      verify(userRepository).findByIdWithRolesAndPermissions(userId);
      verify(refreshTokenSecurityService, never()).revokeAllTokensDueToCompromise(any());
      verifyNoInteractions(accessTokenPort);
      verifyNoMoreInteractions(tokenRepository, tokenGenerator, userRepository, refreshTokenSecurityService);
    }
  }

  private UserAccount activeUserAccountWithRoles(UUID userId) {
    var createUrlPermission = Permission.restore(
            UUID.fromString("019a1744-9a1f-7f0b-b7a4-9b2ab723d001"),
            "url:create",
            "Criar URLs encurtadas"
    );
    var readUrlPermission = Permission.restore(
            UUID.fromString("019a1744-9a1f-7f0b-b7a4-9b2ab723d002"),
            "url:read",
            "Consultar URLs encurtadas"
    );
    var manageUserPermission = Permission.restore(
            UUID.fromString("019a1744-9a1f-7f0b-b7a4-9b2ab723d003"),
            "user:manage",
            "Gerenciar usuários"
    );

    var userRole = Role.restore(
            UUID.fromString("019a1744-9a1f-7f0b-b7a4-9b2ab723e001"),
            "USER",
            true,
            Set.of(createUrlPermission, readUrlPermission)
    );
    var adminRole = Role.restore(
            UUID.fromString("019a1744-9a1f-7f0b-b7a4-9b2ab723e002"),
            "ADMIN",
            false,
            Set.of(readUrlPermission, manageUserPermission)
    );

    return UserAccount.restore(
            userId,
            "User Name",
            "user@email.com",
            "encoded-password",
            UserStatus.ACTIVE,
            PlanType.FREE,
            true,
            Set.of(userRole, adminRole)
    );
  }

  private RefreshToken revokedRefreshToken(UUID userId, String tokenHash) {
    return RefreshToken.restore(
            UUID.fromString("019a1744-9a1f-7f0b-b7a4-9b2ab723f001"),
            userId,
            tokenHash,
            Instant.now().minus(1, ChronoUnit.DAYS),
            Instant.now().plus(6, ChronoUnit.DAYS),
            Instant.now().minus(1, ChronoUnit.HOURS),
            null
    );
  }

  private RefreshToken expiredRefreshToken(UUID userId, String tokenHash) {
    return RefreshToken.restore(
            UUID.fromString("019a1744-9a1f-7f0b-b7a4-9b2ab723f002"),
            userId,
            tokenHash,
            Instant.now().minus(8, ChronoUnit.DAYS),
            Instant.now().minus(1, ChronoUnit.DAYS),
            null,
            null
    );
  }
}
