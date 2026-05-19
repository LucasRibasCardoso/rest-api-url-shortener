package com.app.url_shortener.iam.infrastructure.adapter;

import com.app.url_shortener.iam.domain.enums.PlanType;
import com.app.url_shortener.iam.domain.enums.UserStatus;
import com.app.url_shortener.iam.domain.model.UserAccount;
import com.app.url_shortener.iam.infrastructure.persistence.entity.UserEntity;
import com.app.url_shortener.iam.infrastructure.persistence.mapper.UserAccountPersistenceMapper;
import com.app.url_shortener.iam.infrastructure.persistence.repository.UserJpaRepository;
import com.app.url_shortener.shared.exception.conflict.DataIntegrityConflictException;
import com.app.url_shortener.shared.infrastructure.persistence.DataIntegrityExceptionTranslator;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("Testes de Unidade - UserAccountRepositoryAdapter")
class UserAccountRepositoryAdapterUnitTest {

  @Mock
  private UserJpaRepository userJpaRepository;

  @Mock
  private DataIntegrityExceptionTranslator dataIntegrityExceptionTranslator;

  @Mock
  private UserAccountPersistenceMapper userAccountPersistenceMapper;

  @InjectMocks
  private UserAccountRepositoryAdapter adapter;

  @Nested
  @DisplayName("Persistência")
  class SaveTests {

    @Test
    @DisplayName("Deve salvar conta e mapear entidade salva para domínio sem roles")
    void shouldSaveUserAccountAndMapSavedEntityToDomain() {
      // 1. Arrange
      var domain = userAccount("save@email.com");
      var entity = userEntity(domain.getId(), domain.getEmail());
      var savedEntity = userEntity(domain.getId(), domain.getEmail());
      var savedDomain = userAccount("save@email.com");

      given(userAccountPersistenceMapper.toEntity(domain)).willReturn(entity);
      given(userJpaRepository.saveAndFlush(entity)).willReturn(savedEntity);
      given(userAccountPersistenceMapper.toDomain(savedEntity)).willReturn(savedDomain);

      // 2. Act
      var result = adapter.save(domain);

      // 3. Assert
      assertThat(result).isEqualTo(savedDomain);

      verify(userAccountPersistenceMapper).toEntity(domain);
      verify(userJpaRepository).saveAndFlush(entity);
      verify(userAccountPersistenceMapper).toDomain(savedEntity);
      verifyNoInteractions(dataIntegrityExceptionTranslator);
      verifyNoMoreInteractions(userAccountPersistenceMapper, userJpaRepository);
    }

    @Test
    @DisplayName("Deve traduzir violação de integridade ao salvar nova conta")
    void shouldTranslateDataIntegrityViolationWhenSavingNewUserAccount() {
      // 1. Arrange
      var domain = userAccount("duplicate@email.com");
      var entity = userEntity(domain.getId(), domain.getEmail());
      var dataIntegrityViolation = new DataIntegrityViolationException("duplicate email");
      var translatedException = new DataIntegrityConflictException();

      given(userAccountPersistenceMapper.toEntity(domain)).willReturn(entity);
      given(userJpaRepository.saveAndFlush(entity)).willThrow(dataIntegrityViolation);
      given(dataIntegrityExceptionTranslator.translate(dataIntegrityViolation))
          .willReturn(translatedException);

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> adapter.saveNewUserAccount(domain));

      // 3. Assert
      throwableAssert.isSameAs(translatedException);

      verify(userAccountPersistenceMapper).toEntity(domain);
      verify(userJpaRepository).saveAndFlush(entity);
      verify(dataIntegrityExceptionTranslator).translate(dataIntegrityViolation);
      verifyNoMoreInteractions(userAccountPersistenceMapper, userJpaRepository, dataIntegrityExceptionTranslator);
    }
  }

  @Nested
  @DisplayName("Busca por Email")
  class FindByEmailTests {

    @Test
    @DisplayName("Deve buscar por email e mapear sem roles")
    void shouldFindByEmailAndMapWithoutRoles() {
      // 1. Arrange
      var email = "user@email.com";
      var entity = userEntity(UUID.randomUUID(), email);
      var domain = userAccount(email);

      given(userJpaRepository.findByEmail(email)).willReturn(Optional.of(entity));
      given(userAccountPersistenceMapper.toDomainWithoutRoles(entity)).willReturn(domain);

      // 2. Act
      var result = adapter.findByEmail(email);

      // 3. Assert
      assertThat(result).contains(domain);

      verify(userJpaRepository).findByEmail(email);
      verify(userAccountPersistenceMapper).toDomainWithoutRoles(entity);
      verifyNoMoreInteractions(userJpaRepository, userAccountPersistenceMapper);
      verifyNoInteractions(dataIntegrityExceptionTranslator);
    }

    @Test
    @DisplayName("Deve retornar vazio quando email não existir")
    void shouldReturnEmptyWhenEmailDoesNotExist() {
      // 1. Arrange
      var email = "missing@email.com";

      given(userJpaRepository.findByEmail(email)).willReturn(Optional.empty());

      // 2. Act
      var result = adapter.findByEmail(email);

      // 3. Assert
      assertThat(result).isEmpty();

      verify(userJpaRepository).findByEmail(email);
      verifyNoInteractions(userAccountPersistenceMapper, dataIntegrityExceptionTranslator);
      verifyNoMoreInteractions(userJpaRepository);
    }

    @Test
    @DisplayName("Deve buscar por email com roles e mapear sem permissões")
    void shouldFindByEmailWithRolesAndMapWithRolesWithoutPermissions() {
      // 1. Arrange
      var email = "roles@email.com";
      var entity = userEntity(UUID.randomUUID(), email);
      var domain = userAccount(email);

      given(userJpaRepository.findByEmailWithRoles(email)).willReturn(Optional.of(entity));
      given(userAccountPersistenceMapper.toDomainWithRoles(entity)).willReturn(domain);

      // 2. Act
      var result = adapter.findByEmailWithRoles(email);

      // 3. Assert
      assertThat(result).contains(domain);

      verify(userJpaRepository).findByEmailWithRoles(email);
      verify(userAccountPersistenceMapper).toDomainWithRoles(entity);
      verifyNoMoreInteractions(userJpaRepository, userAccountPersistenceMapper);
      verifyNoInteractions(dataIntegrityExceptionTranslator);
    }
  }

  @Nested
  @DisplayName("Busca por Id")
  class FindByIdTests {

    @Test
    @DisplayName("Deve buscar por id com roles e permissões")
    void shouldFindByIdWithRolesAndPermissions() {
      // 1. Arrange
      var id = UUID.randomUUID();
      var entity = userEntity(id, "permissions@email.com");
      var domain = userAccount("permissions@email.com");

      given(userJpaRepository.findByIdWithRolesAndPermissions(id)).willReturn(Optional.of(entity));
      given(userAccountPersistenceMapper.toDomainWithRolesAndPermissions(entity)).willReturn(domain);

      // 2. Act
      var result = adapter.findByIdWithRolesAndPermissions(id);

      // 3. Assert
      assertThat(result).contains(domain);

      verify(userJpaRepository).findByIdWithRolesAndPermissions(id);
      verify(userAccountPersistenceMapper).toDomainWithRolesAndPermissions(entity);
      verifyNoMoreInteractions(userJpaRepository, userAccountPersistenceMapper);
      verifyNoInteractions(dataIntegrityExceptionTranslator);
    }
  }

  private static UserAccount userAccount(String email) {
    return UserAccount.restore(
        UUID.randomUUID(),
        "User Name",
        email,
        "password-hash",
        UserStatus.ACTIVE,
        PlanType.FREE,
        true,
        Set.of());
  }

  private static UserEntity userEntity(UUID id, String email) {
    return new UserEntity(
        id,
        "User Name",
        email,
        "password-hash",
        UserStatus.ACTIVE,
        PlanType.FREE,
        true,
        null,
        null,
        Set.of());
  }
}
