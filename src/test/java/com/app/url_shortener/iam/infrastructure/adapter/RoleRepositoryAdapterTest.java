package com.app.url_shortener.iam.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.app.url_shortener.iam.domain.exception.rbac.DefaultRoleNotFoundException;
import com.app.url_shortener.iam.domain.model.Role;
import com.app.url_shortener.iam.infrastructure.entity.PermissionEntity;
import com.app.url_shortener.iam.infrastructure.entity.RoleEntity;
import com.app.url_shortener.iam.infrastructure.mapper.RolePersistenceMapper;
import com.app.url_shortener.iam.infrastructure.repository.RoleJpaRepository;
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

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("Testes de Unidade - Adaptador de Repositório de Roles")
class RoleRepositoryAdapterTest {

  @Mock
  private RoleJpaRepository roleJpaRepository;

  @Mock
  private RolePersistenceMapper rolePersistenceMapper;

  @InjectMocks
  private RoleRepositoryAdapter adapter;

  @Nested
  @DisplayName("Busca da Role Padrão")
  class FindDefaultRoleTests {

    @Test
    @DisplayName("Deve retornar role padrão quando entidade existir")
    void shouldReturnDefaultRoleWhenEntityExists() {
      // 1. Arrange
      var entity = roleEntity("USER", true);
      var domain = roleDomain(entity.getId(), "USER", true);

      given(roleJpaRepository.findDefaultRole()).willReturn(Optional.of(entity));
      given(rolePersistenceMapper.toDomainWithoutPermissions(entity)).willReturn(domain);

      // 2. Act
      var result = adapter.findDefaultRole();

      // 3. Assert
      assertThat(result).isEqualTo(domain);

      verify(roleJpaRepository).findDefaultRole();
      verify(rolePersistenceMapper).toDomainWithoutPermissions(entity);
      verifyNoMoreInteractions(roleJpaRepository, rolePersistenceMapper);
    }

    @Test
    @DisplayName("Deve lançar exceção quando role padrão não existir")
    void shouldThrowExceptionWhenDefaultRoleDoesNotExist() {
      // 1. Arrange
      given(roleJpaRepository.findDefaultRole()).willReturn(Optional.empty());

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> adapter.findDefaultRole());

      // 3. Assert
      throwableAssert
              .isInstanceOf(DefaultRoleNotFoundException.class)
              .hasMessage("Permissão padrão não encontrada.");

      verify(roleJpaRepository).findDefaultRole();
      verifyNoInteractions(rolePersistenceMapper);
      verifyNoMoreInteractions(roleJpaRepository);
    }
  }

  private RoleEntity roleEntity(String name, boolean isDefault) {
    return new RoleEntity(
            UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac123"),
            name,
            isDefault,
            Set.of(new PermissionEntity(
                    UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac456"),
                    "url:create",
                    "Criar URLs encurtadas"
            ))
    );
  }

  private Role roleDomain(UUID id, String name, boolean isDefault) {
    return Role.restore(
            id,
            name,
            isDefault,
            Set.of()
    );
  }
}
