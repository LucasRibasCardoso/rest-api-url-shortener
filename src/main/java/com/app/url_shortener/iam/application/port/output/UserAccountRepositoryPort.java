package com.app.url_shortener.iam.application.port.output;

import com.app.url_shortener.iam.domain.model.UserAccount;
import java.util.Optional;
import java.util.UUID;

public interface UserAccountRepositoryPort {

  UserAccount save(UserAccount userAccount);

  UserAccount create(UserAccount userAccount);

  Optional<UserAccount> findByEmail(String email);

  Optional<UserAccount> findByEmailWithRoles(String email);

  Optional<UserAccount> findById(UUID id);

  Optional<UserAccount> findByIdWithRolesAndPermissions(UUID id);
}
