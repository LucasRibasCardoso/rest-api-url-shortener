package com.app.url_shortener.config;

import com.app.url_shortener.iam.domain.enums.PlanType;
import com.app.url_shortener.iam.domain.enums.UserStatus;
import com.app.url_shortener.iam.infrastructure.entity.RoleEntity;
import com.app.url_shortener.iam.infrastructure.entity.UserEntity;
import com.app.url_shortener.iam.infrastructure.repository.RoleJpaRepository;
import com.app.url_shortener.iam.infrastructure.repository.UserJpaRepository;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserTestDataFactory {

  private static final String DEFAULT_NAME = "Integration User";

  private final UserJpaRepository userJpaRepository;
  private final RoleJpaRepository roleJpaRepository;
  private final PasswordEncoder passwordEncoder;

  public UserEntity createActiveUser(String email, String rawPassword) {
    return createActiveUser(email, rawPassword, PlanType.FREE);
  }

  public UserEntity createActiveUser(String email, String rawPassword, PlanType planType) {
    RoleEntity defaultRole = roleJpaRepository.findDefaultRole().orElseThrow();
    return createUser(email, rawPassword, planType, UserStatus.ACTIVE, true, Set.of(defaultRole));
  }

  public UserEntity createAdminUser(String email, String rawPassword) {
    return createAdminUser(email, rawPassword, PlanType.FREE);
  }

  public UserEntity createAdminUser(String email, String rawPassword, PlanType planType) {
    RoleEntity adminRole =
        roleJpaRepository.findAll().stream()
            .filter(role -> "ADMIN".equals(role.getName()))
            .findFirst()
            .orElseThrow();
    return createUser(email, rawPassword, planType, UserStatus.ACTIVE, true, Set.of(adminRole));
  }

  public UserEntity createPendingUser(String email, String rawPassword) {
    return createUser(
        email,
        rawPassword,
        PlanType.FREE,
        UserStatus.PENDING_EMAIL_VERIFICATION,
        false,
        Collections.emptySet());
  }

  private UserEntity createUser(
      String email,
      String rawPassword,
      PlanType planType,
      UserStatus status,
      boolean emailVerified,
      Set<RoleEntity> roles) {
    UserEntity user =
        new UserEntity(
            UUID.randomUUID(),
            DEFAULT_NAME,
            email,
            passwordEncoder.encode(rawPassword),
            status,
            planType,
            emailVerified,
            null,
            null,
            roles);
    return userJpaRepository.saveAndFlush(user);
  }
}
