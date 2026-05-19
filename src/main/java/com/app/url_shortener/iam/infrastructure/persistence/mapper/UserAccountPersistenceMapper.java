package com.app.url_shortener.iam.infrastructure.persistence.mapper;

import com.app.url_shortener.iam.domain.model.UserAccount;
import com.app.url_shortener.iam.infrastructure.persistence.entity.UserEntity;
import java.util.Collections;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import org.springframework.beans.factory.annotation.Autowired;

@Mapper(
    componentModel = "spring",
    unmappedTargetPolicy = ReportingPolicy.ERROR,
    uses = RolePersistenceMapper.class)
public abstract class UserAccountPersistenceMapper {

  @Autowired
  protected RolePersistenceMapper rolePersistenceMapper;

  public UserAccount toDomainWithoutRoles(UserEntity entity) {
    if (entity == null) {
      return null;
    }

    return UserAccount.restore(
        entity.getId(),
        entity.getName(),
        entity.getEmail(),
        entity.getPasswordHash(),
        entity.getStatus(),
        entity.getPlan(),
        entity.isEmailVerified(),
        Collections.emptySet());
  }

  public UserAccount toDomainWithRoles(UserEntity entity) {
    if (entity == null) {
      return null;
    }

    return UserAccount.restore(
        entity.getId(),
        entity.getName(),
        entity.getEmail(),
        entity.getPasswordHash(),
        entity.getStatus(),
        entity.getPlan(),
        entity.isEmailVerified(),
        rolePersistenceMapper.toDomainWithoutPermissions(entity.getRoles()));
  }

  public UserAccount toDomainWithRolesAndPermissions(UserEntity entity) {
    if (entity == null) {
      return null;
    }

    return UserAccount.restore(
        entity.getId(),
        entity.getName(),
        entity.getEmail(),
        entity.getPasswordHash(),
        entity.getStatus(),
        entity.getPlan(),
        entity.isEmailVerified(),
        rolePersistenceMapper.toDomainWithPermissions(entity.getRoles()));
  }

  public UserAccount toDomain(UserEntity entity) {
    return toDomainWithoutRoles(entity);
  }

  public UserEntity toEntity(UserAccount domain) {
    if (domain == null) {
      return null;
    }

    return new UserEntity(
        domain.getId(),
        domain.getName(),
        domain.getEmail(),
        domain.getPasswordHash(),
        domain.getStatus(),
        domain.getPlan(),
        domain.isEmailVerified(),
        null,
        null,
        rolePersistenceMapper.toEntity(domain.getRoles()));
  }
}
