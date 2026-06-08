package com.app.url_shortener.iam.presentation.mapper;

import com.app.url_shortener.iam.application.command.*;
import com.app.url_shortener.iam.application.result.*;
import com.app.url_shortener.iam.domain.valueobject.VerificationCode;
import com.app.url_shortener.iam.presentation.dto.request.LoginRequestDto;
import com.app.url_shortener.iam.presentation.dto.request.RegisterRequestDto;
import com.app.url_shortener.iam.presentation.dto.request.ResendVerificationRequestDto;
import com.app.url_shortener.iam.presentation.dto.request.VerifyEmailRequestDto;
import com.app.url_shortener.iam.presentation.dto.response.GenericMessageResponseDto;
import com.app.url_shortener.iam.presentation.dto.response.LoginResponseDto;
import com.app.url_shortener.iam.presentation.dto.response.RefreshTokenResponseDto;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface IamWebMapper {

  RegisterUserCommand toRegisterUserCommand(RegisterRequestDto request);

  GenericMessageResponseDto toGenericMessageResponse(RegisterUserResult result);

  VerifyEmailCommand toVerifyEmailCommand(VerifyEmailRequestDto request);

  GenericMessageResponseDto toGenericMessageResponse(VerifyEmailResult result);

  LoginCommand toLoginCommand(LoginRequestDto request, String clientIp);

  LoginResponseDto toLoginResponse(LoginResult result);

  LogoutCommand toLogoutCommand(String refreshToken);

  ResendVerificationCommand toResendVerificationCommand(ResendVerificationRequestDto request);

  GenericMessageResponseDto toGenericMessageResponse(ResendVerificationResult result);

  RefreshTokenCommand toRefreshTokenCommand(String refreshToken);

  RefreshTokenResponseDto toRefreshTokenResponse(RefreshTokenResult result);

  default VerificationCode toVerificationCode(String value) {
    if (value == null) return null;
    return VerificationCode.of(value);
  }

  default String toVerificationCodeValue(VerificationCode verificationCode) {
    if (verificationCode == null) return null;
    return verificationCode.value();
  }
}
