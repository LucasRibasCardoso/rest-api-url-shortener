package com.app.url_shortener.config;

import com.app.url_shortener.shared.idempotency.impl.PrincipalScopeResolver;
import com.app.url_shortener.shared.idempotency.impl.RequestBodyHasher;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(SpringExtension.class)
@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
public abstract class BaseWebSliceTest {

  @Autowired protected MockMvc mockMvc;

  @Autowired protected ObjectMapper objectMapper;

  @MockitoBean protected JwtDecoder jwtDecoder;

  @MockitoBean protected PrincipalScopeResolver principalScopeResolver;

  @MockitoBean protected RequestBodyHasher requestBodyHasher;
}
