package com.app.url_shortener.iam.infrastructure.mapper;

import com.app.url_shortener.iam.domain.model.Permission;
import com.app.url_shortener.iam.domain.model.Role;
import com.app.url_shortener.iam.infrastructure.entity.PermissionEntity;
import com.app.url_shortener.iam.infrastructure.entity.RoleEntity;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import org.mapstruct.IterableMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;
import org.springframework.beans.factory.annotation.Autowired;

@Mapper(
    componentModel = "spring",
    unmappedTargetPolicy = ReportingPolicy.ERROR,
    uses = PermissionPersistenceMapper.class)
public abstract class RolePersistenceMapper {

  @Autowired
  protected PermissionPersistenceMapper permissionPersistenceMapper;

  @Named("roleWithoutPermissions")
  public Role toDomainWithoutPermissions(RoleEntity entity) {
    if (entity == null) {
      return null;
    }

    return Role.restore(
        entity.getId(), entity.getName(), entity.isDefault(), Collections.emptySet());
  }

  @Named("roleWithPermissions")
  public Role toDomainWithPermissions(RoleEntity entity) {
    if (entity == null) {
      return null;
    }

    return Role.restore(
        entity.getId(),
        entity.getName(),
        entity.isDefault(),
        toDomainPermissions(entity.getPermissions()));
  }

  @IterableMapping(qualifiedByName = "roleWithoutPermissions")
  public abstract Set<Role> toDomainWithoutPermissions(Set<RoleEntity> entities);

  @IterableMapping(qualifiedByName = "roleWithPermissions")
  public abstract Set<Role> toDomainWithPermissions(Set<RoleEntity> entities);

  public abstract Set<Permission> toDomainPermissions(Set<PermissionEntity> entities);

  public RoleEntity toEntity(Role domain) {
    if (domain == null) {
      return null;
    }

    return new RoleEntity(
        domain.getId(),
        domain.getName(),
        domain.isDefault(),
        toEntityPermissions(domain.getPermissions()));
  }

  public Set<RoleEntity> toEntity(Set<Role> domains) {
    if (domains == null) {
      return null;
    }

    var entities = new LinkedHashSet<RoleEntity>(domains.size());
    for (Role domain : domains) {
      entities.add(toEntity(domain));
    }
    return entities;
  }

  protected Set<PermissionEntity> toEntityPermissions(Set<Permission> domains) {
    if (domains == null) {
      return null;
    }

    var entities = new LinkedHashSet<PermissionEntity>(domains.size());
    for (Permission domain : domains) {
      entities.add(permissionPersistenceMapper.toEntity(domain));
    }
    return entities;
  }
}
