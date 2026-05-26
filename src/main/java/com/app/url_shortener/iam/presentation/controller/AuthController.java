package com.app.url_shortener.iam.presentation.controller;

import com.app.url_shortener.iam.application.command.*;
import com.app.url_shortener.iam.application.result.*;
import com.app.url_shortener.iam.application.usecase.*;
import com.app.url_shortener.iam.domain.exception.auth.InvalidRefreshTokenException;
import com.app.url_shortener.iam.presentation.dto.request.LoginRequestDto;
import com.app.url_shortener.iam.presentation.dto.request.RegisterRequestDto;
import com.app.url_shortener.iam.presentation.dto.request.ResendVerificationRequest;
import com.app.url_shortener.iam.presentation.dto.request.VerifyEmailRequestDto;
import com.app.url_shortener.iam.presentation.dto.response.GenericMessageResponse;
import com.app.url_shortener.iam.presentation.dto.response.LoginResponseDto;
import com.app.url_shortener.iam.presentation.dto.response.RefreshTokenResponseDto;
import com.app.url_shortener.iam.presentation.mapper.IamWebMapper;
import com.app.url_shortener.shared.ratelimit.key.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

  public static final Duration DEFAULT_MAX_AGE = Duration.ofDays(7);

  private final IamWebMapper iamWebMapper;
  private final LoginUseCase loginUseCase;
  private final LogoutUseCase logoutUseCase;
  private final ClientIpResolver clientIpResolver;
  private final VerifyEmailUseCase verifyEmailUseCase;
  private final RegisterUserUseCase registerUserUseCase;
  private final RefreshTokenUseCase refreshTokenUseCase;
  private final ResendVerificationUseCase resendVerificationUseCase;

  @PostMapping("/register")
  public ResponseEntity<GenericMessageResponse> register(@Valid @RequestBody RegisterRequestDto request) {
    RegisterUserCommand command = iamWebMapper.toCommand(request);
    RegisterUserResult result = registerUserUseCase.execute(command);
    GenericMessageResponse response = iamWebMapper.toResponse(result);

    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @PostMapping("/verify-email")
  public ResponseEntity<GenericMessageResponse> verifyEmail(@Valid @RequestBody VerifyEmailRequestDto request) {
    VerifyEmailCommand command = iamWebMapper.toCommand(request);
    VerifyEmailResult result = verifyEmailUseCase.execute(command);
    GenericMessageResponse response = iamWebMapper.toResponse(result);

    return ResponseEntity.status(HttpStatus.OK).body(response);
  }

  @PostMapping("/login")
  public ResponseEntity<LoginResponseDto> login(@Valid @RequestBody LoginRequestDto requestDto, HttpServletRequest request) {
    String clientIp = clientIpResolver.resolve(request);

    LoginCommand command = iamWebMapper.toCommand(requestDto, clientIp);
    LoginResult result = loginUseCase.execute(command);
    LoginResponseDto response = iamWebMapper.toResponse(result);

    ResponseCookie cookie = buildRefreshTokenCookie(result.refreshToken());
    return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie.toString()).body(response);
  }

  @PostMapping("/refresh")
  public ResponseEntity<RefreshTokenResponseDto> refresh(@CookieValue(required = false) String refreshToken) {
    if (refreshToken == null || refreshToken.isBlank()) throw new InvalidRefreshTokenException();

    RefreshTokenCommand command = iamWebMapper.toRefreshTokenCommand(refreshToken);
    RefreshTokenResult result = refreshTokenUseCase.execute(command);
    RefreshTokenResponseDto response = iamWebMapper.toResponse(result);

    ResponseCookie cookie = buildRefreshTokenCookie(result.newRefreshToken());
    return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie.toString()).body(response);
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout(@CookieValue(required = false) String refreshToken) {
    LogoutCommand command = iamWebMapper.toLogoutCommand(refreshToken);
    logoutUseCase.execute(command);

    ResponseCookie expiredCookie = buildExpireRefreshTokenCookie();
    return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, expiredCookie.toString()).build();
  }

  @PostMapping("/resend-verification")
  public ResponseEntity<GenericMessageResponse> resend(@Valid @RequestBody ResendVerificationRequest request) {
    ResendVerificationCommand command = iamWebMapper.toCommand(request);
    ResendVerificationResult result = resendVerificationUseCase.execute(command);
    GenericMessageResponse response = iamWebMapper.toResponse(result);

    return ResponseEntity.status(HttpStatus.OK).body(response);
  }

  private ResponseCookie buildRefreshTokenCookie(String refreshToken) {
    return ResponseCookie.from("refreshToken", refreshToken)
            .httpOnly(true)
            .secure(true)
            .sameSite("Strict")
            .path("/api/v1/auth")
            .maxAge(DEFAULT_MAX_AGE)
            .build();
  }

  private ResponseCookie buildExpireRefreshTokenCookie() {
    return ResponseCookie.from("refreshToken", "")
            .httpOnly(true)
            .secure(true)
            .sameSite("Strict")
            .path("/api/v1/auth")
            .maxAge(Duration.ZERO)
            .build();
  }
}
