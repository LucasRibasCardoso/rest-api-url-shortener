package com.app.url_shortener.security.principal;

import static org.assertj.core.api.Assertions.assertThat;

import com.app.url_shortener.iam.domain.enums.PlanType;
import com.app.url_shortener.iam.domain.enums.UserStatus;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

@Tag("unit")
@DisplayName("Testes de Unidade - UserPrincipal")
class UserPrincipalTest {

  @Nested
  @DisplayName("Flags do UserDetails")
  class UserDetailsFlagsTests {

    @ParameterizedTest
    @EnumSource(UserStatus.class)
    @DisplayName("Deve habilitar apenas usuários que não estão pendentes de verificação de email")
    void shouldEnableOnlyUsersThatAreNotPendingEmailVerification(UserStatus status) {
      // 1. Arrange
      var principal = userPrincipal(status);

      // 2. Act
      var enabled = principal.isEnabled();

      // 3. Assert
      assertThat(enabled).isEqualTo(status != UserStatus.PENDING_EMAIL_VERIFICATION);
    }

    @ParameterizedTest
    @EnumSource(UserStatus.class)
    @DisplayName("Deve considerar não bloqueados apenas usuários que não estão bloqueados")
    void shouldConsiderAccountNonLockedOnlyWhenUserIsNotLocked(UserStatus status) {
      // 1. Arrange
      var principal = userPrincipal(status);

      // 2. Act
      var accountNonLocked = principal.isAccountNonLocked();

      // 3. Assert
      assertThat(accountNonLocked).isEqualTo(status != UserStatus.LOCKED);
    }

    @ParameterizedTest
    @EnumSource(UserStatus.class)
    @DisplayName("Deve considerar não expirados apenas usuários que não estão desabilitados")
    void shouldConsiderAccountNonExpiredOnlyWhenUserIsNotDisabled(UserStatus status) {
      // 1. Arrange
      var principal = userPrincipal(status);

      // 2. Act
      var accountNonExpired = principal.isAccountNonExpired();

      // 3. Assert
      assertThat(accountNonExpired).isEqualTo(status != UserStatus.DISABLED);
    }

    @ParameterizedTest
    @MethodSource("allStatuses")
    @DisplayName("Deve manter credenciais sempre não expiradas")
    void shouldAlwaysConsiderCredentialsNonExpired(UserStatus status) {
      // 1. Arrange
      var principal = userPrincipal(status);

      // 2. Act
      var credentialsNonExpired = principal.isCredentialsNonExpired();

      // 3. Assert
      assertThat(credentialsNonExpired).isTrue();
    }

    static Stream<UserStatus> allStatuses() {
      return Stream.of(UserStatus.values());
    }
  }

  private static UserPrincipal userPrincipal(UserStatus status) {
    return new UserPrincipal(
        UUID.randomUUID(),
        "John Doe",
        "john.doe@email.com",
        "password-hash",
        PlanType.FREE,
        status,
        List.of());
  }
}
