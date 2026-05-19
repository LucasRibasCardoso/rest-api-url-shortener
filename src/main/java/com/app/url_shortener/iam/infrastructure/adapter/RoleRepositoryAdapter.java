package com.app.url_shortener.iam.infrastructure.adapter;

import com.app.url_shortener.iam.application.port.output.RoleRepositoryPort;
import com.app.url_shortener.iam.domain.exception.rbac.DefaultRoleNotFoundException;
import com.app.url_shortener.iam.domain.model.Role;
import com.app.url_shortener.iam.infrastructure.persistence.mapper.RolePersistenceMapper;
import com.app.url_shortener.iam.infrastructure.persistence.repository.RoleJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RoleRepositoryAdapter implements RoleRepositoryPort {

  private final RoleJpaRepository roleJpaRepository;
  private final RolePersistenceMapper rolePersistenceMapper;

  @Override
  public Role findDefaultRole() {
    return roleJpaRepository
        .findDefaultRole()
        .map(rolePersistenceMapper::toDomainWithoutPermissions)
        .orElseThrow(DefaultRoleNotFoundException::new);
  }
}
