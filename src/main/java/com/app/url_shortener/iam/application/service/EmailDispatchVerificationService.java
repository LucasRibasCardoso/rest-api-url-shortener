package com.app.url_shortener.iam.application.service;

import com.app.url_shortener.iam.application.event.EmailVerificationRequestedEvent;
import com.app.url_shortener.iam.application.service.model.PreparedEmailVerificationDispatch;

public interface EmailDispatchVerificationService {

  PreparedEmailVerificationDispatch findOrCreate(EmailVerificationRequestedEvent event);
}
