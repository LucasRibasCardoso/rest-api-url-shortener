package com.app.url_shortener.iam.infrastructure.persistence.mapper;

import com.app.url_shortener.iam.domain.model.Permission;
import com.app.url_shortener.iam.infrastructure.entity.PermissionEntity;
import com.app.url_shortener.iam.infrastructure.mapper.PermissionPersistenceMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@DisplayName("Testes de Unidade - PermissionPersistenceMapper")
class PermissionPersistenceMapperTest {

  private final PermissionPersistenceMapper mapper =
          Mappers.getMapper(PermissionPersistenceMapper.class);

  @Nested
  @DisplayName("Mapeamento para Domínio")
  class ToDomainTests {

    @Test
    @DisplayName("Deve mapear entidade de permissão para domínio")
    void shouldMapPermissionEntityToDomain() {
      // 1. Arrange
      var id = UUID.randomUUID();
      var entity = new PermissionEntity(id, "url:create", "Criar URLs encurtadas");

      // 2. Act
      var domain = mapper.toDomain(entity);

      // 3. Assert
      assertThat(domain).isNotNull();
      assertThat(domain.getId()).isEqualTo(id);
      assertThat(domain.getName()).isEqualTo("url:create");
      assertThat(domain.getDescription()).isEqualTo("Criar URLs encurtadas");
    }

    @Test
    @DisplayName("Deve retornar nulo quando a entidade for nula")
    void shouldReturnNullWhenPermissionEntityIsNull() {
      // 1. Arrange
      PermissionEntity entity = null;

      // 2. Act
      var domain = mapper.toDomain(entity);

      // 3. Assert
      assertThat(domain).isNull();
    }
  }

  @Nested
  @DisplayName("Mapeamento para Entidade")
  class ToEntityTests {

    @Test
    @DisplayName("Deve mapear domínio de permissão para entidade")
    void shouldMapPermissionDomainToEntity() {
      // 1. Arrange
      var id = UUID.randomUUID();
      var domain = Permission.restore(id, "url:read", "Consultar URLs encurtadas");

      // 2. Act
      var entity = mapper.toEntity(domain);

      // 3. Assert
      assertThat(entity).isNotNull();
      assertThat(entity.getId()).isEqualTo(id);
      assertThat(entity.getName()).isEqualTo("url:read");
      assertThat(entity.getDescription()).isEqualTo("Consultar URLs encurtadas");
      assertThat(entity.getCreatedAt()).isNull();
      assertThat(entity.getUpdatedAt()).isNull();
    }

    @Test
    @DisplayName("Deve retornar nulo quando o domínio for nulo")
    void shouldReturnNullWhenPermissionDomainIsNull() {
      // 1. Arrange
      Permission domain = null;

      // 2. Act
      var entity = mapper.toEntity(domain);

      // 3. Assert
      assertThat(entity).isNull();
    }
  }
}
