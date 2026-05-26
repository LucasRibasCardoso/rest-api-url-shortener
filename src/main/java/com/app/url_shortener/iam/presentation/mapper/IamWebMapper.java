package com.app.url_shortener.iam.presentation.mapper;

import com.app.url_shortener.iam.application.command.*;
import com.app.url_shortener.iam.application.result.*;
import com.app.url_shortener.iam.domain.valueobject.VerificationCode;
import com.app.url_shortener.iam.presentation.dto.request.LoginRequestDto;
import com.app.url_shortener.iam.presentation.dto.request.RegisterRequestDto;
import com.app.url_shortener.iam.presentation.dto.request.ResendVerificationRequest;
import com.app.url_shortener.iam.presentation.dto.request.VerifyEmailRequestDto;
import com.app.url_shortener.iam.presentation.dto.response.GenericMessageResponse;
import com.app.url_shortener.iam.presentation.dto.response.LoginResponseDto;
import com.app.url_shortener.iam.presentation.dto.response.RefreshTokenResponseDto;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface IamWebMapper {

  RegisterUserCommand toCommand(RegisterRequestDto request);

  GenericMessageResponse toResponse(RegisterUserResult result);

  VerifyEmailCommand toCommand(VerifyEmailRequestDto request);

  GenericMessageResponse toResponse(VerifyEmailResult result);

  LoginCommand toCommand(LoginRequestDto request, String clientIp);

  LoginResponseDto toResponse(LoginResult result);

  LogoutCommand toLogoutCommand(String refreshToken);

  ResendVerificationCommand toCommand(ResendVerificationRequest request);

  GenericMessageResponse toResponse(ResendVerificationResult result);

  RefreshTokenCommand toRefreshTokenCommand(String refreshToken);

  RefreshTokenResponseDto toResponse(RefreshTokenResult result);

  default VerificationCode mapToVerificationCode(String value) {
    if (value == null) return null;
    return VerificationCode.of(value);
  }

  default String mapToString(VerificationCode verificationCode) {
    if (verificationCode == null) return null;
    return verificationCode.value();
  }
}
