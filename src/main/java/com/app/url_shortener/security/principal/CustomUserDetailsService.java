package com.app.url_shortener.security.principal;

import com.app.url_shortener.iam.infrastructure.repository.UserJpaRepository;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

  private final UserJpaRepository userRepository;
  private final UserPrincipalFactory userPrincipalFactory;

  @Override
  @Transactional(readOnly = true)
  public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
    String normalizedEmail = email == null ? null : email.trim().toLowerCase(Locale.ROOT);

    return userRepository
        .findByEmailWithRolesAndPermissions(normalizedEmail)
        .map(userPrincipalFactory::from)
        .orElseThrow(() -> new UsernameNotFoundException("User not found"));
  }
}
