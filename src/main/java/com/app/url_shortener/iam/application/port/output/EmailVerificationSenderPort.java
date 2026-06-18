package com.app.url_shortener.iam.application.port.output;

import com.app.url_shortener.iam.application.port.output.model.EmailVerificationSendResult;

public interface EmailVerificationSenderPort {

  EmailVerificationSendResult sendEmailVerificationCode(String email, String code);
}
