package com.app.url_shortener.iam.presentation.docs;

import com.app.url_shortener.iam.presentation.dto.request.LoginRequestDto;
import com.app.url_shortener.iam.presentation.dto.request.RegisterRequestDto;
import com.app.url_shortener.iam.presentation.dto.response.GenericMessageResponseDto;
import com.app.url_shortener.iam.presentation.dto.response.LoginResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
}
