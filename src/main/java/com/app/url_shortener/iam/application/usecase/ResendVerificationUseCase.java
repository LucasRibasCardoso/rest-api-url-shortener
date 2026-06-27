package com.app.url_shortener.iam.application.usecase;

import com.app.url_shortener.iam.application.command.ResendVerificationCommand;
import com.app.url_shortener.iam.application.result.ResendVerificationResult;

public interface ResendVerificationUseCase {

  ResendVerificationResult execute(ResendVerificationCommand command);
}
