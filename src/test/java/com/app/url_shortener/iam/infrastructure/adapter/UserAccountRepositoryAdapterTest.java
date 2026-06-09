package com.app.url_shortener.iam.infrastructure.adapter;

import com.app.url_shortener.config.BaseDataJpaSliceTest;
import com.app.url_shortener.iam.domain.enums.PlanType;
import com.app.url_shortener.iam.domain.enums.UserStatus;
import com.app.url_shortener.iam.domain.exception.user.EmailAlreadyRegisteredException;
import com.app.url_shortener.iam.domain.model.UserAccount;
import com.app.url_shortener.iam.infrastructure.mapper.PermissionPersistenceMapperImpl;
import com.app.url_shortener.iam.infrastructure.mapper.RolePersistenceMapperImpl;
import com.app.url_shortener.iam.infrastructure.mapper.UserAccountPersistenceMapperImpl;
import com.app.url_shortener.shared.config.JpaAuditingConfig;
import com.app.url_shortener.shared.database.DataIntegrityExceptionTranslator;
import com.app.url_shortener.shared.database.PostgresConstraintExtractor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
      adapter.create(userAccount(
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
      var throwableAssert = assertThatThrownBy(() -> adapter.create(duplicateUser));

      // 3. Assert
      throwableAssert
          .isInstanceOf(EmailAlreadyRegisteredException.class)
          .hasMessage("Email já cadastrado.");
    }

    @Test
    @DisplayName("Deve aplicar unicidade case-insensitive do CITEXT criado pelo Flyway")
    void shouldApplyCaseInsensitiveUniqueEmailFromCitextColumn() {
      // 1. Arrange
      adapter.create(userAccount(
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
      var throwableAssert = assertThatThrownBy(() -> adapter.create(duplicateUser));

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
      var userId = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac301");

      // 2. Act
      var throwableAssert =
          assertThatThrownBy(
              () ->
                  jdbcTemplate.update(
                      """
                          INSERT INTO users (id, name, email, password_hash, status, plan, email_verified)
                          VALUES (?, ?, ?, ?, ?, ?, ?)
                          """,
                      userId,
                      "Null Status",
                      "null-status@email.com",
                      "password-hash",
                      null,
                      "FREE",
                      false));

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
      var user = adapter.create(userAccount(
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
