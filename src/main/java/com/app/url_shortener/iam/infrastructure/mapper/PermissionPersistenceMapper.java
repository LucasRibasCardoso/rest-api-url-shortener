package com.app.url_shortener.iam.infrastructure.mapper;

import com.app.url_shortener.iam.domain.model.Permission;
import com.app.url_shortener.iam.infrastructure.entity.PermissionEntity;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface PermissionPersistenceMapper {

  default Permission toDomain(PermissionEntity entity) {
    if (entity == null) return null;
    return Permission.restore(entity.getId(), entity.getName(), entity.getDescription());
  }

  default PermissionEntity toEntity(Permission domain) {
    if (domain == null) return null;
    return new PermissionEntity(domain.getId(), domain.getName(), domain.getDescription());
  }
}
