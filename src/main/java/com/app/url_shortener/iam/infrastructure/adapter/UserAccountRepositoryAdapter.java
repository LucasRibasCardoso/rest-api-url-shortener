package com.app.url_shortener.iam.infrastructure.adapter;

import com.app.url_shortener.iam.application.port.output.UserAccountRepositoryPort;
import com.app.url_shortener.iam.domain.model.UserAccount;
import com.app.url_shortener.iam.infrastructure.persistence.entity.UserEntity;
import com.app.url_shortener.iam.infrastructure.persistence.mapper.UserAccountPersistenceMapper;
import com.app.url_shortener.iam.infrastructure.persistence.repository.UserJpaRepository;
import com.app.url_shortener.shared.infrastructure.persistence.DataIntegrityExceptionTranslator;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class UserAccountRepositoryAdapter implements UserAccountRepositoryPort {

  private final UserJpaRepository userJpaRepository;
  private final DataIntegrityExceptionTranslator dataIntegrityExceptionTranslator;
  private final UserAccountPersistenceMapper userAccountPersistenceMapper;

  @Override
  public UserAccount save(UserAccount userAccount) {
    UserEntity userEntity = userAccountPersistenceMapper.toEntity(userAccount);
    UserEntity savedEntity = userJpaRepository.saveAndFlush(userEntity);
    return userAccountPersistenceMapper.toDomain(savedEntity);
  }

  @Override
  public UserAccount saveNewUserAccount(UserAccount userAccount) {
    try {
      UserEntity userEntity = userAccountPersistenceMapper.toEntity(userAccount);
      UserEntity savedEntity = userJpaRepository.saveAndFlush(userEntity);
      return userAccountPersistenceMapper.toDomain(savedEntity);

    } catch (DataIntegrityViolationException exception) {
      throw dataIntegrityExceptionTranslator.translate(exception);
    }
  }

  @Override
  public Optional<UserAccount> findByEmail(String email) {
    return userJpaRepository
        .findByEmailWithRolesAndPermissions(email)
        .map(userAccountPersistenceMapper::toDomain);
  }

  @Override
  public Optional<UserAccount> findByIdWithRolesAndPermissions(UUID id) {
    return userJpaRepository
        .findByIdWithRolesAndPermissions(id)
        .map(userAccountPersistenceMapper::toDomain);
  }
}
