package com.app.url_shortener.security.principal;

import com.app.url_shortener.iam.domain.enums.PlanType;
import com.app.url_shortener.iam.domain.enums.UserStatus;
import com.app.url_shortener.iam.infrastructure.entity.UserEntity;
import com.app.url_shortener.iam.infrastructure.repository.UserJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("Testes de Unidade - CustomUserDetailsService")
class CustomUserDetailsServiceTest {

  @Mock
  private UserJpaRepository userRepository;

  @Mock
  private UserPrincipalFactory userPrincipalFactory;

  @InjectMocks
  private CustomUserDetailsService service;

  @Nested
  @DisplayName("Carregamento por Email")
  class LoadUserByUsernameTests {

    @Test
    @DisplayName("Deve carregar usuário com email normalizado")
    void shouldLoadUserWithNormalizedEmail() {
      // 1. Arrange
      var email = "  USER@EMAIL.COM  ";
      var normalizedEmail = "user@email.com";
      var user = userEntity(normalizedEmail);
      var principal = userPrincipal(user);

      given(userRepository.findByEmailWithRolesAndPermissions(normalizedEmail)).willReturn(Optional.of(user));
      given(userPrincipalFactory.from(user)).willReturn(principal);

      // 2. Act
      var result = service.loadUserByUsername(email);

      // 3. Assert
      assertThat(result).isSameAs(principal);

      verify(userRepository).findByEmailWithRolesAndPermissions(normalizedEmail);
      verify(userPrincipalFactory).from(user);
      verifyNoMoreInteractions(userRepository, userPrincipalFactory);
    }

    @Test
    @DisplayName("Deve lançar UsernameNotFoundException quando usuário não for encontrado")
    void shouldThrowUsernameNotFoundExceptionWhenUserIsNotFound() {
      // 1. Arrange
      var email = "missing@email.com";

      given(userRepository.findByEmailWithRolesAndPermissions(email)).willReturn(Optional.empty());

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> service.loadUserByUsername(email));

      // 3. Assert
      throwableAssert
              .isInstanceOf(UsernameNotFoundException.class)
              .hasMessage("User not found");

      verify(userRepository).findByEmailWithRolesAndPermissions(email);
      verifyNoInteractions(userPrincipalFactory);
      verifyNoMoreInteractions(userRepository);
    }

    @Test
    @DisplayName("Deve repassar email nulo para o repositório e lançar UsernameNotFoundException")
    void shouldPassNullEmailToRepositoryAndThrowUsernameNotFoundException() {
      // 1. Arrange
      String email = null;

      given(userRepository.findByEmailWithRolesAndPermissions(null)).willReturn(Optional.empty());

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> service.loadUserByUsername(email));

      // 3. Assert
      throwableAssert
              .isInstanceOf(UsernameNotFoundException.class)
              .hasMessage("User not found");

      verify(userRepository).findByEmailWithRolesAndPermissions(null);
      verifyNoInteractions(userPrincipalFactory);
      verifyNoMoreInteractions(userRepository);
    }
  }

  private static UserEntity userEntity(String email) {
    return new UserEntity(
            UUID.randomUUID(),
            "John Doe",
            email,
            "password-hash",
            UserStatus.ACTIVE,
            PlanType.FREE,
            true,
            null,
            null,
            Set.of()
    );
  }

  private static UserPrincipal userPrincipal(UserEntity user) {
    return new UserPrincipal(
            user.getId(),
            user.getName(),
            user.getEmail(),
            user.getPasswordHash(),
            user.getPlan(),
            user.getStatus(),
            List.of()
    );
  }
}
