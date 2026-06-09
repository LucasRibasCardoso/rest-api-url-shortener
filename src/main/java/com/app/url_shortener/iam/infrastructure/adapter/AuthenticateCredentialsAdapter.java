package com.app.url_shortener.iam.infrastructure.adapter;

import com.app.url_shortener.iam.application.port.output.AuthenticateCredentialsPort;
import com.app.url_shortener.iam.application.result.AuthenticatedUserResult;
import com.app.url_shortener.iam.domain.exception.auth.AccountPendingVerificationException;
import com.app.url_shortener.iam.domain.exception.auth.InvalidCredentialsException;
import com.app.url_shortener.iam.domain.exception.user.UserAccountDisabledException;
import com.app.url_shortener.iam.domain.exception.user.UserAccountLockedException;
import com.app.url_shortener.security.principal.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AccountExpiredException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class AuthenticateCredentialsAdapter implements AuthenticateCredentialsPort {

  private static final String ROLE_PREFIX = "ROLE_";

  private final AuthenticationManager authenticationManager;

  @Override
  public AuthenticatedUserResult authenticate(String email, String password) {
    try {
      Authentication authentication = authenticationManager.authenticate(
              new UsernamePasswordAuthenticationToken(email, password)
      );

      if (!(authentication.getPrincipal() instanceof UserPrincipal userPrincipal)) {
        throw new InvalidCredentialsException();
      }

      return toAuthenticatedUserResult(userPrincipal);

    } catch (AccountExpiredException e) {
      throw new UserAccountDisabledException();
    } catch (DisabledException e) {
      throw new AccountPendingVerificationException();
    } catch (LockedException e) {
      throw new UserAccountLockedException();
    } catch (AuthenticationException e) {
      throw new InvalidCredentialsException();
    }
  }

  private AuthenticatedUserResult toAuthenticatedUserResult(UserPrincipal principal) {

    List<String> authorities = principal.getAuthorities()
            .stream()
            .map(GrantedAuthority::getAuthority)
            .toList();

    List<String> roles = authorities.stream()
            .filter(authority -> authority.startsWith(ROLE_PREFIX))
            .map(authority -> authority.substring(ROLE_PREFIX.length()))
            .toList();

    return new AuthenticatedUserResult(
            principal.getId(),
            principal.getName(),
            principal.getEmail(),
            roles,
            authorities,
            principal.getPlan().name()
    );
  }
}
