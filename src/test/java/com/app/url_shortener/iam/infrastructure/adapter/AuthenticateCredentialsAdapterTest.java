package com.app.url_shortener.iam.infrastructure.adapter;

import com.app.url_shortener.config.BaseDataJpaSliceTest;
import com.app.url_shortener.iam.application.result.AuthenticatedUserResult;
import com.app.url_shortener.iam.domain.enums.PlanType;
import com.app.url_shortener.iam.domain.enums.UserStatus;
import com.app.url_shortener.iam.domain.exception.auth.AccountDisabledException;
import com.app.url_shortener.iam.domain.exception.auth.AccountLockedException;
import com.app.url_shortener.iam.domain.exception.auth.AccountNotFoundException;
import com.app.url_shortener.iam.domain.exception.auth.InvalidCredentialsException;
import com.app.url_shortener.iam.domain.exception.user.EmailAlreadyRegisteredException;
import com.app.url_shortener.iam.domain.model.UserAccount;
import com.app.url_shortener.iam.infrastructure.persistence.mapper.PermissionPersistenceMapperImpl;
import com.app.url_shortener.iam.infrastructure.persistence.mapper.RolePersistenceMapperImpl;
import com.app.url_shortener.iam.infrastructure.persistence.mapper.UserAccountPersistenceMapperImpl;
import com.app.url_shortener.security.principal.UserPrincipal;
import com.app.url_shortener.shared.config.JpaAuditingConfig;
import com.app.url_shortener.shared.infrastructure.persistence.DataIntegrityExceptionTranslator;
import com.app.url_shortener.shared.infrastructure.persistence.PostgresConstraintExtractor;
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
import org.postgresql.util.PSQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
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
import java.util.Set;
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
                      AccountNotFoundException.class,
                      "Conta não existe ou foi removida."
              ),
              Arguments.of(
                      new DisabledException("account disabled"),
                      AccountDisabledException.class,
                      "Conta pendente de verificação."
              ),
              Arguments.of(
                      new LockedException("account locked"),
                      AccountLockedException.class,
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

  @Tag("jpa-slice")
  @Import({
          UserAccountRepositoryAdapter.class,
          JpaAuditingConfig.class,
          DataIntegrityExceptionTranslator.class,
          PostgresConstraintExtractor.class,
          UserAccountPersistenceMapperImpl.class,
          RolePersistenceMapperImpl.class,
          PermissionPersistenceMapperImpl.class
  })
  @DisplayName("Slice Data JPA - Adaptador de Repositório de Contas de Usuário")
  class UserAccountRepositoryAdapterTest extends BaseDataJpaSliceTest {

    private static final UUID DEFAULT_ROLE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    private UserAccountRepositoryAdapter adapter;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Nested
    @DisplayName("Constraints únicas")
    class UniqueConstraintTests {

      @Test
      @DisplayName("Deve traduzir violação de email duplicado para exceção de domínio")
      void shouldTranslateDuplicateEmailViolationToDomainException() {
        // 1. Arrange
        adapter.saveNewUserAccount(userAccount(
                UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac101"),
                "duplicate@email.com",
                UserStatus.PENDING_EMAIL_VERIFICATION,
                PlanType.FREE
        ));

        var duplicateUser = userAccount(
                UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac102"),
                "duplicate@email.com",
                UserStatus.PENDING_EMAIL_VERIFICATION,
                PlanType.FREE
        );

        // 2. Act
        var throwableAssert = assertThatThrownBy(() -> adapter.saveNewUserAccount(duplicateUser));

        // 3. Assert
        throwableAssert
                .isInstanceOf(EmailAlreadyRegisteredException.class)
                .hasMessage("Email já cadastrado.");
      }

      @Test
      @DisplayName("Deve aplicar unicidade case-insensitive do CITEXT criado pelo Flyway")
      void shouldApplyCaseInsensitiveUniqueEmailFromCitextColumn() {
        // 1. Arrange
        adapter.saveNewUserAccount(userAccount(
                UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac201"),
                "CaseSensitive@email.com",
                UserStatus.PENDING_EMAIL_VERIFICATION,
                PlanType.FREE
        ));

        var duplicateUser = userAccount(
                UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac202"),
                "casesensitive@email.com",
                UserStatus.PENDING_EMAIL_VERIFICATION,
                PlanType.FREE
        );

        // 2. Act
        var throwableAssert = assertThatThrownBy(() -> adapter.saveNewUserAccount(duplicateUser));

        // 3. Assert
        throwableAssert.isInstanceOf(EmailAlreadyRegisteredException.class);
      }
    }

    @Nested
    @DisplayName("Constraints not-null")
    class NotNullConstraintTests {

      @Test
      @DisplayName("Deve lançar DataIntegrityViolationException quando status obrigatório estiver nulo")
      void shouldThrowDataIntegrityViolationWhenRequiredStatusIsNull() {
        // 1. Arrange
        var user = userAccount(
                UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac301"),
                "null-status@email.com",
                null,
                PlanType.FREE
        );

        // 2. Act
        var throwableAssert = assertThatThrownBy(() -> adapter.save(user));

        // 3. Assert
        throwableAssert
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasRootCauseInstanceOf(PSQLException.class)
                .hasMessageContaining("not-null");
      }
    }

    @Nested
    @DisplayName("Constraints de enum")
    class EnumConstraintTests {

      @Test
      @DisplayName("Deve rejeitar PlanType não permitido pelo check constraint do banco")
      void shouldRejectPlanTypeNotAllowedByDatabaseCheckConstraint() {
        // 1. Arrange
        var userId = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac401");

        // 2. Act
        var throwableAssert = assertThatThrownBy(() -> jdbcTemplate.update(
                """
                        INSERT INTO users (id, name, email, password_hash, status, plan, email_verified)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                userId,
                "Invalid Plan",
                "invalid-plan@email.com",
                "password-hash",
                "PENDING_EMAIL_VERIFICATION",
                "INVALID_PLAN",
                false
        ));

        // 3. Assert
        throwableAssert
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasRootCauseInstanceOf(PSQLException.class)
                .hasMessageContaining("chk_users_plan");
      }

      @Test
      @DisplayName("Deve rejeitar status fora do enum permitido pelo schema Flyway")
      void shouldRejectStatusOutsideFlywayCheckConstraint() {
        // 1. Arrange
        var userId = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac402");

        // 2. Act
        var throwableAssert = assertThatThrownBy(() -> jdbcTemplate.update(
                """
                        INSERT INTO users (id, name, email, password_hash, status, plan, email_verified)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                userId,
                "Invalid Status",
                "invalid-status@email.com",
                "password-hash",
                "SUSPENDED",
                "FREE",
                false
        ));

        // 3. Assert
        throwableAssert
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasRootCauseInstanceOf(PSQLException.class)
                .hasMessageContaining("chk_users_status");
      }
    }

    @Nested
    @DisplayName("Constraints de chave estrangeira")
    class ForeignKeyConstraintTests {

      @Test
      @DisplayName("Deve rejeitar vínculo user_roles com usuário inexistente")
      void shouldRejectUserRoleWhenUserDoesNotExist() {
        // 1. Arrange
        var missingUserId = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac501");

        // 2. Act
        var throwableAssert = assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO user_roles (user_id, role_id) VALUES (?, ?)",
                missingUserId,
                DEFAULT_ROLE_ID
        ));

        // 3. Assert
        throwableAssert
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasRootCauseInstanceOf(PSQLException.class)
                .hasMessageContaining("user_roles_user_id_fkey");
      }

      @Test
      @DisplayName("Deve remover vínculos user_roles por ON DELETE CASCADE ao excluir usuário")
      void shouldCascadeDeleteUserRolesWhenUserIsDeleted() {
        // 1. Arrange
        var user = adapter.saveNewUserAccount(userAccount(
                UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac502"),
                "cascade@email.com",
                UserStatus.PENDING_EMAIL_VERIFICATION,
                PlanType.FREE
        ));

        jdbcTemplate.update(
                "INSERT INTO user_roles (user_id, role_id) VALUES (?, ?)",
                user.getId(),
                DEFAULT_ROLE_ID
        );

        // 2. Act
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", user.getId());
        entityManager.flush();

        // 3. Assert
        var joinRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_roles WHERE user_id = ?",
                Integer.class,
                user.getId()
        );

        assertThat(joinRows).isZero();
      }
    }

    private UserAccount userAccount(UUID id, String email, UserStatus status, PlanType plan) {
      return UserAccount.restore(
              id,
              "User Name",
              email,
              "password-hash",
              status,
              plan,
              false,
              Set.of()
      );
    }
  }
}
