package com.app.url_shortener.iam.presentation.docs;

import com.app.url_shortener.iam.presentation.dto.request.LoginRequestDto;
import com.app.url_shortener.iam.presentation.dto.request.RegisterRequestDto;
import com.app.url_shortener.iam.presentation.dto.request.ResendVerificationRequestDto;
import com.app.url_shortener.iam.presentation.dto.request.VerifyEmailRequestDto;
import com.app.url_shortener.iam.presentation.dto.response.GenericMessageResponseDto;
import com.app.url_shortener.iam.presentation.dto.response.LoginResponseDto;
import com.app.url_shortener.iam.presentation.dto.response.RefreshTokenResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(
    name = "Authentication",
    description = "Registro, verificação de e-mail, login, refresh token e logout.")
public interface AuthApiDocs {

  @Operation(
      operationId = "registerUser",
      summary = "Registrar usuário",
      description =
          """
          Cria uma conta de usuário e inicia o fluxo de verificação de e-mail.
          O endpoint é público e exige `Idempotency-Key`. O e-mail informado deve ser único.
          A conta criada permanece pendente até a confirmação do código de verificação.

          Requisições repetidas com a mesma chave idempotente e o mesmo payload retornam a resposta
          previamente gerada. Requisições com a mesma chave e payload diferente são rejeitadas.
          """)
  @Parameter(ref = "#/components/parameters/IdempotencyKeyHeader")
  @io.swagger.v3.oas.annotations.parameters.RequestBody(
      required = true,
      description = "Dados para criação da conta de usuário.",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = RegisterRequestDto.class)))
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "Usuário registrado e fluxo de verificação de e-mail iniciado.",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = GenericMessageResponseDto.class),
                examples =
                    @ExampleObject(
                        name = "registrationAccepted",
                        summary = "Registro aceito",
                        value =
                            """
                            {
                              "message": "Enviamos um código de verificação para o seu e-mail."
                            }
                            """))),
    @ApiResponse(
        responseCode = "400",
        description = "Payload inválido ou problema no header Idempotency-Key.",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(ref = "#/components/schemas/BadRequestError"),
                examples = {
                  @ExampleObject(
                      name = "invalidPayload",
                      summary = "Payload inválido",
                      value =
                          """
                          {
                            "type": "/errors/validation",
                            "title": "Validação",
                            "status": 400,
                            "detail": "Um ou mais campos estão inválidos.",
                            "errorCode": "REQUEST_VALIDATION_FAILED",
                            "errors": [
                              {
                                "field": "name",
                                "message": "size must be between 3 and 120"
                              },
                              {
                                "field": "email",
                                "message": "must be a well-formed email address"
                              },
                              {
                                "field": "password",
                                "message": "size must be between 6 and 128"
                              }
                            ]
                          }
                          """),
                  @ExampleObject(
                      name = "missingIdempotencyKey",
                      summary = "Idempotency-Key ausente",
                      value =
                          """
                          {
                            "type": "/errors/validation",
                            "title": "Validação",
                            "status": 400,
                            "detail": "O cabeçalho Idempotency-Key é obrigatório para esta operação.",
                            "errorCode": "IDEMPOTENCY_HEADER_MISSING"
                          }
                          """)
                })),
    @ApiResponse(
        responseCode = "409",
        description = "E-mail já registrado ou conflito de idempotência.",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(ref = "#/components/schemas/ApiError"),
                examples = {
                  @ExampleObject(
                      name = "emailAlreadyRegistered",
                      summary = "E-mail já registrado",
                      value =
                          """
                          {
                            "type": "/errors/conflict",
                            "title": "Conflito",
                            "status": 409,
                            "detail": "Email já cadastrado.",
                            "errorCode": "AUTH_EMAIL_ALREADY_EXISTS"
                          }
                          """),
                  @ExampleObject(
                      name = "idempotencyConflict",
                      summary = "Conflito de idempotência",
                      value =
                          """
                          {
                            "type": "/errors/conflict",
                            "title": "Conflito",
                            "status": 409,
                            "detail": "A requisição já está em processamento. Aguarde.",
                            "errorCode": "IDEMPOTENCY_IN_PROCESSING"
                          }
                          """)
                })),
    @ApiResponse(
        responseCode = "429",
        description = "Limite de requisições excedido.",
        headers = @Header(name = "Retry-After", ref = "#/components/headers/RetryAfter"),
        content =
            @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(ref = "#/components/schemas/ApiError"),
                examples =
                    @ExampleObject(
                        name = "tooManyRequests",
                        summary = "Rate limit excedido",
                        value =
                            """
                            {
                              "type": "/errors/too-many-requests",
                              "title": "Muitas requisições",
                              "status": 429,
                              "detail": "Muitas requisições. Por favor, tente novamente mais tarde.",
                              "errorCode": "TOO_MANY_REQUESTS"
                            }
                            """))),
    @ApiResponse(
        responseCode = "503",
        description = "Falha temporária em dependência necessária ao registro.",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(ref = "#/components/schemas/ApiError"),
                examples =
                    @ExampleObject(
                        name = "dependencyFailure",
                        summary = "Dependência temporariamente indisponível",
                        value =
                            """
                            {
                              "type": "/errors/infrastructure",
                              "title": "Infraestrutura",
                              "status": 503,
                              "detail": "Falha temporária em serviço de infraestrutura.",
                              "errorCode": "DEPENDENCY_FAILURE"
                            }
                            """)))
  })
  ResponseEntity<GenericMessageResponseDto> register(
      @Valid @RequestBody RegisterRequestDto requestDto,
      @Parameter(hidden = true) HttpServletRequest request);

  @Operation(
      operationId = "verifyEmail",
      summary = "Verificar e-mail",
      description =
          """
          Confirma o e-mail de uma conta pendente usando o código de verificação enviado ao usuário.
          O endpoint é público e exige `Idempotency-Key`. Em caso de sucesso, ativa a conta,
          consome o código informado e permite que o usuário faça login.

          Códigos incorretos, expirados, já consumidos ou associados a e-mail inexistente retornam
          a mesma resposta pública de validação para não revelar detalhes da conta.

          Requisições repetidas com a mesma chave idempotente e o mesmo payload retornam a resposta
          previamente gerada. Requisições com a mesma chave e payload diferente são rejeitadas.
          """)
  @Parameter(ref = "#/components/parameters/IdempotencyKeyHeader")
  @io.swagger.v3.oas.annotations.parameters.RequestBody(
      required = true,
      description = "E-mail e código de verificação de 6 dígitos.",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = VerifyEmailRequestDto.class)))
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "E-mail verificado e conta ativada com sucesso.",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = GenericMessageResponseDto.class),
                examples =
                    @ExampleObject(
                        name = "emailVerified",
                        summary = "E-mail verificado",
                        value =
                            """
                            {
                              "message": "E-mail verificado com sucesso. Agora você pode fazer login na sua conta."
                            }
                            """))),
    @ApiResponse(
        responseCode = "400",
        description =
            "Payload inválido, código inválido ou expirado, ou problema no header Idempotency-Key.",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(ref = "#/components/schemas/BadRequestError"),
                examples = {
                  @ExampleObject(
                      name = "invalidPayload",
                      summary = "Payload inválido",
                      value =
                          """
                          {
                            "type": "/errors/validation",
                            "title": "Validação",
                            "status": 400,
                            "detail": "Um ou mais campos estão inválidos.",
                            "errorCode": "REQUEST_VALIDATION_FAILED",
                            "errors": [
                              {
                                "field": "email",
                                "message": "must be a well-formed email address"
                              },
                              {
                                "field": "code",
                                "message": "Código de verificação deve conter 6 digitos"
                              }
                            ]
                          }
                          """),
                  @ExampleObject(
                      name = "invalidOrExpiredCode",
                      summary = "Código inválido ou expirado",
                      value =
                          """
                          {
                            "type": "/errors/validation",
                            "title": "Validação",
                            "status": 400,
                            "detail": "Código de verificação inválido ou expirado.",
                            "errorCode": "AUTH_INVALID_OR_EXPIRED_VERIFICATION_CODE"
                          }
                          """),
                  @ExampleObject(
                      name = "missingIdempotencyKey",
                      summary = "Idempotency-Key ausente",
                      value =
                          """
                          {
                            "type": "/errors/validation",
                            "title": "Validação",
                            "status": 400,
                            "detail": "O cabeçalho Idempotency-Key é obrigatório para esta operação.",
                            "errorCode": "IDEMPOTENCY_HEADER_MISSING"
                          }
                          """)
                })),
    @ApiResponse(
        responseCode = "409",
        description = "Conflito de idempotência.",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(ref = "#/components/schemas/ApiError"),
                examples =
                    @ExampleObject(
                        name = "idempotencyConflict",
                        summary = "Conflito de idempotência",
                        value =
                            """
                            {
                              "type": "/errors/conflict",
                              "title": "Conflito",
                              "status": 409,
                              "detail": "A requisição já está em processamento. Aguarde.",
                              "errorCode": "IDEMPOTENCY_IN_PROCESSING"
                            }
                            """))),
    @ApiResponse(
        responseCode = "429",
        description = "Limite de requisições excedido.",
        headers = @Header(name = "Retry-After", ref = "#/components/headers/RetryAfter"),
        content =
            @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(ref = "#/components/schemas/ApiError"),
                examples =
                    @ExampleObject(
                        name = "tooManyRequests",
                        summary = "Rate limit excedido",
                        value =
                            """
                            {
                              "type": "/errors/too-many-requests",
                              "title": "Muitas requisições",
                              "status": 429,
                              "detail": "Muitas requisições. Por favor, tente novamente mais tarde.",
                              "errorCode": "TOO_MANY_REQUESTS"
                            }
                            """))),
    @ApiResponse(
        responseCode = "503",
        description = "Falha temporária em dependência necessária à verificação de e-mail.",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(ref = "#/components/schemas/ApiError"),
                examples =
                    @ExampleObject(
                        name = "dependencyFailure",
                        summary = "Dependência temporariamente indisponível",
                        value =
                            """
                            {
                              "type": "/errors/infrastructure",
                              "title": "Infraestrutura",
                              "status": 503,
                              "detail": "Falha temporária em serviço de infraestrutura.",
                              "errorCode": "DEPENDENCY_FAILURE"
                            }
                            """)))
  })
  ResponseEntity<GenericMessageResponseDto> verifyEmail(
      @Valid @RequestBody VerifyEmailRequestDto request);

  @Operation(
      operationId = "loginUser",
      summary = "Autenticar usuário",
      description =
          """
          Autentica um usuário com e-mail e senha.
          O endpoint é público e exige `Idempotency-Key`. Em caso de sucesso, retorna um JWT de
          acesso no corpo da resposta e emite um cookie `refreshToken` seguro, `HttpOnly`,
          `Secure`, `SameSite=Strict`, com path `/api/v1/auth` e duração de 7 dias.

          O e-mail é normalizado para minúsculas antes da autenticação. Contas pendentes,
          bloqueadas ou desabilitadas não recebem tokens.

          Requisições repetidas com a mesma chave idempotente e o mesmo payload retornam a resposta
          previamente gerada. Requisições com a mesma chave e payload diferente são rejeitadas.
          """)
  @Parameter(ref = "#/components/parameters/IdempotencyKeyHeader")
  @io.swagger.v3.oas.annotations.parameters.RequestBody(
      required = true,
      description = "Credenciais para autenticação do usuário.",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = LoginRequestDto.class)))
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Autenticação realizada com sucesso.",
        headers = @Header(name = "Set-Cookie", ref = "#/components/headers/SetCookie"),
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = LoginResponseDto.class),
                examples =
                    @ExampleObject(
                        name = "authenticated",
                        summary = "Usuário autenticado",
                        value =
                            """
                            {
                              "accessToken": "<access_token>",
                              "tokenType": "Bearer",
                              "expiresInSeconds": 900,
                              "user": {
                                "id": "019a16f1-ae7f-7c9d-9e18-44773f1ac123",
                                "name": "User Name",
                                "email": "user@example.com",
                                "plan": "FREE",
                                "roles": ["USER"]
                              }
                            }
                            """))),
    @ApiResponse(
        responseCode = "400",
        description =
            "Payload inválido, credenciais inválidas ou problema no header Idempotency-Key.",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(ref = "#/components/schemas/BadRequestError"),
                examples = {
                  @ExampleObject(
                      name = "invalidPayload",
                      summary = "Payload inválido",
                      value =
                          """
                          {
                            "type": "/errors/validation",
                            "title": "Validação",
                            "status": 400,
                            "detail": "Um ou mais campos estão inválidos.",
                            "errorCode": "REQUEST_VALIDATION_FAILED",
                            "errors": [
                              {
                                "field": "email",
                                "message": "must be a well-formed email address"
                              },
                              {
                                "field": "password",
                                "message": "size must be between 6 and 128"
                              }
                            ]
                          }
                          """),
                  @ExampleObject(
                      name = "invalidCredentials",
                      summary = "Credenciais inválidas",
                      value =
                          """
                          {
                            "type": "/errors/validation",
                            "title": "Validação",
                            "status": 400,
                            "detail": "Credenciais inválidas.",
                            "errorCode": "AUTH_INVALID_CREDENTIALS"
                          }
                          """),
                  @ExampleObject(
                      name = "missingIdempotencyKey",
                      summary = "Idempotency-Key ausente",
                      value =
                          """
                          {
                            "type": "/errors/validation",
                            "title": "Validação",
                            "status": 400,
                            "detail": "O cabeçalho Idempotency-Key é obrigatório para esta operação.",
                            "errorCode": "IDEMPOTENCY_HEADER_MISSING"
                          }
                          """)
                })),
    @ApiResponse(
        responseCode = "403",
        description = "Conta pendente de verificação, bloqueada ou desabilitada.",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(ref = "#/components/schemas/ApiError"),
                examples = {
                  @ExampleObject(
                      name = "pendingVerification",
                      summary = "Conta pendente de verificação",
                      value =
                          """
                          {
                            "type": "/errors/forbidden",
                            "title": "Proibido",
                            "status": 403,
                            "detail": "Conta pendente de verificação.",
                            "errorCode": "AUTH_ACCOUNT_PENDING_VERIFICATION"
                          }
                          """),
                  @ExampleObject(
                      name = "lockedAccount",
                      summary = "Conta bloqueada",
                      value =
                          """
                          {
                            "type": "/errors/forbidden",
                            "title": "Proibido",
                            "status": 403,
                            "detail": "Conta bloqueada.",
                            "errorCode": "AUTH_ACCOUNT_LOCKED"
                          }
                          """),
                  @ExampleObject(
                      name = "disabledAccount",
                      summary = "Conta desabilitada",
                      value =
                          """
                          {
                            "type": "/errors/forbidden",
                            "title": "Proibido",
                            "status": 403,
                            "detail": "Conta desabilitada.",
                            "errorCode": "USER_ACCOUNT_DISABLED"
                          }
                          """)
                })),
    @ApiResponse(
        responseCode = "409",
        description = "Conflito de idempotência.",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(ref = "#/components/schemas/ApiError"),
                examples =
                    @ExampleObject(
                        name = "idempotencyConflict",
                        summary = "Conflito de idempotência",
                        value =
                            """
                            {
                              "type": "/errors/conflict",
                              "title": "Conflito",
                              "status": 409,
                              "detail": "A requisição já está em processamento. Aguarde.",
                              "errorCode": "IDEMPOTENCY_IN_PROCESSING"
                            }
                            """))),
    @ApiResponse(
        responseCode = "429",
        description = "Limite de requisições excedido.",
        headers = @Header(name = "Retry-After", ref = "#/components/headers/RetryAfter"),
        content =
            @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(ref = "#/components/schemas/ApiError"),
                examples =
                    @ExampleObject(
                        name = "tooManyRequests",
                        summary = "Rate limit excedido",
                        value =
                            """
                            {
                              "type": "/errors/too-many-requests",
                              "title": "Muitas requisições",
                              "status": 429,
                              "detail": "Muitas requisições. Por favor, tente novamente mais tarde.",
                              "errorCode": "TOO_MANY_REQUESTS"
                            }
                            """))),
    @ApiResponse(
        responseCode = "503",
        description = "Falha temporária em dependência necessária ao login.",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(ref = "#/components/schemas/ApiError"),
                examples =
                    @ExampleObject(
                        name = "dependencyFailure",
                        summary = "Dependência temporariamente indisponível",
                        value =
                            """
                            {
                              "type": "/errors/infrastructure",
                              "title": "Infraestrutura",
                              "status": 503,
                              "detail": "Falha temporária em serviço de infraestrutura.",
                              "errorCode": "DEPENDENCY_FAILURE"
                            }
                            """)))
  })
  ResponseEntity<LoginResponseDto> login(
      @Valid @RequestBody LoginRequestDto requestDto,
      @Parameter(hidden = true) HttpServletRequest request);

  @Operation(
      operationId = "refreshAccessToken",
      summary = "Renovar token de acesso",
      description =
          """
          Renova a sessão a partir do cookie `refreshToken`.
          O endpoint é público e não usa JWT Bearer no header `Authorization`. Em caso de sucesso,
          rotaciona o refresh token atual, retorna um novo access token no corpo da resposta e
          emite um novo cookie `refreshToken` seguro, `HttpOnly`, `Secure`, `SameSite=Strict`,
          com path `/api/v1/auth` e duração de 7 dias.

          O refresh token antigo deixa de ser válido após a rotação. Reuso de token já rotacionado
          é tratado como comprometimento e invalida as sessões do usuário.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Access token renovado e refresh token rotacionado com sucesso.",
        headers = @Header(name = "Set-Cookie", ref = "#/components/headers/SetCookie"),
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = RefreshTokenResponseDto.class),
                examples =
                    @ExampleObject(
                        name = "refreshed",
                        summary = "Token renovado",
                        value =
                            """
                            {
                              "newAccessToken": "<access_token>"
                            }
                            """))),
    @ApiResponse(
        responseCode = "401",
        description = "Refresh token ausente, inválido, expirado, desconhecido ou comprometido.",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(ref = "#/components/schemas/ApiError"),
                examples = {
                  @ExampleObject(
                      name = "missingRefreshTokenCookie",
                      summary = "Cookie ausente",
                      value =
                          """
                          {
                            "type": "/errors/unauthorized",
                            "title": "Não autorizado",
                            "status": 401,
                            "detail": "Refresh token inválido.",
                            "errorCode": "AUTH_REFRESH_TOKEN_INVALID"
                          }
                          """),
                  @ExampleObject(
                      name = "expiredRefreshToken",
                      summary = "Refresh token expirado ou desconhecido",
                      value =
                          """
                          {
                            "type": "/errors/unauthorized",
                            "title": "Não autorizado",
                            "status": 401,
                            "detail": "Refresh token expirado.",
                            "errorCode": "AUTH_REFRESH_TOKEN_EXPIRED"
                          }
                          """),
                  @ExampleObject(
                      name = "compromisedRefreshToken",
                      summary = "Refresh token comprometido",
                      value =
                          """
                          {
                            "type": "/errors/unauthorized",
                            "title": "Não autorizado",
                            "status": 401,
                            "detail": "Refresh token comprometido.",
                            "errorCode": "AUTH_REFRESH_TOKEN_COMPROMISED"
                          }
                          """)
                })),
    @ApiResponse(
        responseCode = "404",
        description = "Usuário associado ao refresh token não encontrado.",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(ref = "#/components/schemas/ApiError"),
                examples =
                    @ExampleObject(
                        name = "userNotFound",
                        summary = "Usuário não encontrado",
                        value =
                            """
                            {
                              "type": "/errors/not-found",
                              "title": "Não encontrado",
                              "status": 404,
                              "detail": "Usuário não encontrado.",
                              "errorCode": "AUTH_USER_NOT_FOUND"
                            }
                            """))),
    @ApiResponse(
        responseCode = "503",
        description = "Falha temporária em dependência necessária à renovação da sessão.",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(ref = "#/components/schemas/ApiError"),
                examples =
                    @ExampleObject(
                        name = "dependencyFailure",
                        summary = "Dependência temporariamente indisponível",
                        value =
                            """
                            {
                              "type": "/errors/infrastructure",
                              "title": "Infraestrutura",
                              "status": 503,
                              "detail": "Falha temporária em serviço de infraestrutura.",
                              "errorCode": "DEPENDENCY_FAILURE"
                            }
                            """)))
  })
  ResponseEntity<RefreshTokenResponseDto> refresh(
      @Parameter(ref = "#/components/parameters/RefreshTokenCookieRequired")
          @CookieValue(required = false)
          String refreshToken);

  @Operation(
      operationId = "resendEmailVerification",
      summary = "Reenviar verificação de e-mail",
      description =
          """
          Solicita o reenvio do código de verificação para uma conta pendente.
          O endpoint é público e exige `Idempotency-Key`. Para evitar enumeração de contas, a API
          retorna a mesma mensagem de sucesso quando o e-mail não existe ou quando a conta já não
          está pendente de verificação.

          Quando o e-mail pertence a uma conta pendente, um novo envio de verificação é iniciado.
          Requisições repetidas com a mesma chave idempotente e o mesmo payload retornam a resposta
          previamente gerada. Requisições com a mesma chave e payload diferente são rejeitadas.
          """)
  @Parameter(ref = "#/components/parameters/IdempotencyKeyHeader")
  @io.swagger.v3.oas.annotations.parameters.RequestBody(
      required = true,
      description = "E-mail da conta que deve receber um novo código de verificação.",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = ResendVerificationRequestDto.class)))
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Solicitação de reenvio aceita.",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = GenericMessageResponseDto.class),
                examples =
                    @ExampleObject(
                        name = "resendAccepted",
                        summary = "Reenvio aceito",
                        value =
                            """
                            {
                              "message": "Enviamos um novo código de verificação para o seu e-mail."
                            }
                            """))),
    @ApiResponse(
        responseCode = "400",
        description = "Payload inválido ou problema no header Idempotency-Key.",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(ref = "#/components/schemas/BadRequestError"),
                examples = {
                  @ExampleObject(
                      name = "invalidPayload",
                      summary = "Payload inválido",
                      value =
                          """
                          {
                            "type": "/errors/validation",
                            "title": "Validação",
                            "status": 400,
                            "detail": "Um ou mais campos estão inválidos.",
                            "errorCode": "REQUEST_VALIDATION_FAILED",
                            "errors": [
                              {
                                "field": "email",
                                "message": "must be a well-formed email address"
                              }
                            ]
                          }
                          """),
                  @ExampleObject(
                      name = "missingIdempotencyKey",
                      summary = "Idempotency-Key ausente",
                      value =
                          """
                          {
                            "type": "/errors/validation",
                            "title": "Validação",
                            "status": 400,
                            "detail": "O cabeçalho Idempotency-Key é obrigatório para esta operação.",
                            "errorCode": "IDEMPOTENCY_HEADER_MISSING"
                          }
                          """)
                })),
    @ApiResponse(
        responseCode = "409",
        description = "Conflito de idempotência.",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(ref = "#/components/schemas/ApiError"),
                examples =
                    @ExampleObject(
                        name = "idempotencyConflict",
                        summary = "Conflito de idempotência",
                        value =
                            """
                            {
                              "type": "/errors/conflict",
                              "title": "Conflito",
                              "status": 409,
                              "detail": "A requisição já está em processamento. Aguarde.",
                              "errorCode": "IDEMPOTENCY_IN_PROCESSING"
                            }
                            """))),
    @ApiResponse(
        responseCode = "429",
        description = "Limite de requisições excedido.",
        headers = @Header(name = "Retry-After", ref = "#/components/headers/RetryAfter"),
        content =
            @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(ref = "#/components/schemas/ApiError"),
                examples =
                    @ExampleObject(
                        name = "tooManyRequests",
                        summary = "Rate limit excedido",
                        value =
                            """
                            {
                              "type": "/errors/too-many-requests",
                              "title": "Muitas requisições",
                              "status": 429,
                              "detail": "Muitas requisições. Por favor, tente novamente mais tarde.",
                              "errorCode": "TOO_MANY_REQUESTS"
                            }
                            """))),
    @ApiResponse(
        responseCode = "503",
        description = "Falha temporária em dependência necessária ao reenvio de verificação.",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(ref = "#/components/schemas/ApiError"),
                examples =
                    @ExampleObject(
                        name = "dependencyFailure",
                        summary = "Dependência temporariamente indisponível",
                        value =
                            """
                            {
                              "type": "/errors/infrastructure",
                              "title": "Infraestrutura",
                              "status": 503,
                              "detail": "Falha temporária em serviço de infraestrutura.",
                              "errorCode": "DEPENDENCY_FAILURE"
                            }
                            """)))
  })
  ResponseEntity<GenericMessageResponseDto> resend(
      @Valid @RequestBody ResendVerificationRequestDto request);

  @Operation(
      operationId = "logoutUser",
      summary = "Encerrar sessão",
      description =
          """
          Encerra a sessão do usuário autenticado.
          Exige JWT válido no header `Authorization` e `Idempotency-Key`. Quando o cookie
          `refreshToken` é informado, o refresh token ativo correspondente é revogado. Quando o
          cookie está ausente, vazio ou não corresponde a uma sessão ativa, a operação ainda retorna
          sucesso e expira o cookie no cliente.

          Em caso de sucesso, a resposta não possui corpo e emite `Set-Cookie` para expirar
          `refreshToken` com `HttpOnly`, `Secure`, `SameSite=Strict`, path `/api/v1/auth` e
          `Max-Age=0`.

          Requisições repetidas com a mesma chave idempotente e o mesmo payload retornam a resposta
          previamente gerada. Requisições com a mesma chave e payload diferente são rejeitadas.
          """,
      security = @SecurityRequirement(name = "bearerAuth"))
  @Parameter(ref = "#/components/parameters/IdempotencyKeyHeader")
  @ApiResponses({
    @ApiResponse(
        responseCode = "204",
        description = "Logout concluído e cookie de refresh token expirado.",
        headers = @Header(name = "Set-Cookie", ref = "#/components/headers/SetCookie"),
        content = @Content),
    @ApiResponse(
        responseCode = "400",
        description = "Header Idempotency-Key ausente ou inválido.",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(ref = "#/components/schemas/BadRequestError"),
                examples =
                    @ExampleObject(
                        name = "missingIdempotencyKey",
                        summary = "Idempotency-Key ausente",
                        value =
                            """
                            {
                              "type": "/errors/validation",
                              "title": "Validação",
                              "status": 400,
                              "detail": "O cabeçalho Idempotency-Key é obrigatório para esta operação.",
                              "errorCode": "IDEMPOTENCY_HEADER_MISSING"
                            }
                            """))),
    @ApiResponse(
        responseCode = "401",
        description = "JWT ausente, inválido ou expirado.",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(ref = "#/components/schemas/ApiError"),
                examples =
                    @ExampleObject(
                        name = "unauthorized",
                        summary = "Não autenticado",
                        value =
                            """
                            {
                              "type": "/errors/unauthorized",
                              "title": "Não autorizado",
                              "status": 401,
                              "detail": "Autenticação necessária ou token inválido.",
                              "errorCode": "AUTH_UNAUTHORIZED"
                            }
                            """))),
    @ApiResponse(
        responseCode = "409",
        description = "Conflito de idempotência.",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(ref = "#/components/schemas/ApiError"),
                examples =
                    @ExampleObject(
                        name = "idempotencyConflict",
                        summary = "Conflito de idempotência",
                        value =
                            """
                            {
                              "type": "/errors/conflict",
                              "title": "Conflito",
                              "status": 409,
                              "detail": "A requisição já está em processamento. Aguarde.",
                              "errorCode": "IDEMPOTENCY_IN_PROCESSING"
                            }
                            """))),
    @ApiResponse(
        responseCode = "503",
        description = "Falha temporária em dependência necessária ao logout.",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(ref = "#/components/schemas/ApiError"),
                examples =
                    @ExampleObject(
                        name = "dependencyFailure",
                        summary = "Dependência temporariamente indisponível",
                        value =
                            """
                            {
                              "type": "/errors/infrastructure",
                              "title": "Infraestrutura",
                              "status": 503,
                              "detail": "Falha temporária em serviço de infraestrutura.",
                              "errorCode": "DEPENDENCY_FAILURE"
                            }
                            """)))
  })
  ResponseEntity<Void> logout(
      @Parameter(ref = "#/components/parameters/RefreshTokenCookieOptional")
          @CookieValue(required = false)
          String refreshToken);
}
