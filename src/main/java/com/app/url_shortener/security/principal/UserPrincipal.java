package com.app.url_shortener.security.principal;

import com.app.url_shortener.iam.domain.enums.PlanType;
import com.app.url_shortener.iam.domain.enums.UserStatus;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

@Getter
public class UserPrincipal implements UserDetails {

  private final UUID id;
  private final String name;
  private final String email;
  private final String passwordHash;
  private final PlanType plan;
  private final UserStatus status;
  private final List<GrantedAuthority> authorities;

  public UserPrincipal(
      UUID id,
      String name,
      String email,
      String passwordHash,
      PlanType plan,
      UserStatus status,
      List<GrantedAuthority> authorities) {
    this.id = id;
    this.name = name;
    this.email = email;
    this.passwordHash = passwordHash;
    this.plan = plan;
    this.status = status;
    this.authorities = List.copyOf(authorities);
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return authorities;
  }

  @Override
  public String getPassword() {
    return passwordHash;
  }

  @Override
  public String getUsername() {
    return email;
  }

  @Override
  public boolean isAccountNonExpired() {
    return status != UserStatus.DISABLED;
  }

  @Override
  public boolean isAccountNonLocked() {
    return status != UserStatus.LOCKED;
  }

  @Override
  public boolean isCredentialsNonExpired() {
    return true;
  }

  @Override
  public boolean isEnabled() {
    return status != UserStatus.PENDING_EMAIL_VERIFICATION;
  }
}
