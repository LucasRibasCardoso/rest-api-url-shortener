package com.app.url_shortener.iam.application.service;

import com.app.url_shortener.iam.application.event.EmailVerificationRequestedEvent;

public interface EmailVerificationEventProcessorService {

  void process(EmailVerificationRequestedEvent event);
}
