package com.app.url_shortener.iam.infrastructure.repository;

import com.app.url_shortener.iam.infrastructure.entity.PermissionEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionJpaRepository extends JpaRepository<PermissionEntity, UUID> {
}
