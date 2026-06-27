package com.app.url_shortener.iam.application.service.model;

import com.app.url_shortener.iam.domain.model.EmailDispatch;
import com.app.url_shortener.iam.domain.model.EmailVerificationToken;

public record PreparedEmailVerificationDispatch(
    EmailDispatch dispatch, EmailVerificationToken token) {}
