package com.app.url_shortener.shared.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  private static final String API_TITLE = "URL Shortener API";
  private static final String API_VERSION = "1.0.0";
  private static final String BEARER_AUTH_SCHEME = "bearerAuth";
  private static final String API_ERROR_SCHEMA = "ApiError";
  private static final String API_VALIDATION_ERROR_SCHEMA = "ApiValidationError";
  private static final String FIELD_ERROR_SCHEMA = "FieldError";
  private static final String IDEMPOTENCY_KEY_PARAMETER = "IdempotencyKeyHeader";
  private static final String REFRESH_TOKEN_COOKIE_REQUIRED_PARAMETER =
      "RefreshTokenCookieRequired";
  private static final String REFRESH_TOKEN_COOKIE_OPTIONAL_PARAMETER =
      "RefreshTokenCookieOptional";
  private static final String LOCATION_HEADER = "Location";
  private static final String RETRY_AFTER_HEADER = "RetryAfter";
  private static final String SET_COOKIE_HEADER = "SetCookie";
  private static final String API_DESCRIPTION =
      """
      API RESTful para encurtamento, gerenciamento e resolução de URLs.

      Esta documentação descreve os recursos HTTP disponíveis para clientes da API, incluindo
      autenticação, verificação de e-mail, criação e gerenciamento de URLs encurtadas,
      redirecionamento público e consulta de dados operacionais das URLs.

      ## Autenticação

      Endpoints protegidos usam JWT Bearer token no header `Authorization`.

      ```http
      Authorization: Bearer <access_token>
      ```

      O login e a renovação de sessão usam um refresh token em cookie seguro, `HttpOnly`,
      `Secure` e `SameSite=Strict`.

      ## Erros

      Erros são retornados no formato Problem Details (`application/problem+json`) e incluem
      um `errorCode` estável para tratamento por clientes.

      ## Idempotência

      Endpoints críticos de escrita exigem `Idempotency-Key` para evitar processamento duplicado
      em tentativas repetidas.

      ## Escopo do contrato HTTP

      Esta especificação descreve recursos, payloads, headers, cookies, códigos HTTP e erros
      observáveis pelos clientes da API. Detalhes internos de persistência, cache, mensageria e
      implementação não fazem parte do contrato público.

      ## Ambiente

      A documentação interativa é habilitada apenas em ambiente local/dev por configuração.
      """;

  @Bean
  public OpenAPI customOpenApi(
      ApplicationProperties applicationProperties,
      SpringApplicationProperties springApplicationProperties) {

    return new OpenAPI()
        .components(openApiComponents())
        .info(
            new Info()
                .title(API_TITLE)
                .description(API_DESCRIPTION)
                .version(API_VERSION)
                .contact(
                    new Contact()
                        .name("Lucas Ribas Cardoso")
                        .url("https://github.com/LucasRibasCardoso")
                        .email("lucas.rib.card@gmail.com"))
                .license(
                    new License().name("MIT License").url("https://opensource.org/licenses/MIT")))
        .servers(
            List.of(
                new Server()
                    .url(applicationProperties.baseUrl())
                    .description(
                        "Configured application server ("
                            + springApplicationProperties.name()
                            + ")")))
        .tags(
            List.of(
                new Tag()
                    .name("Authentication")
                    .description("Registro, login, refresh token e logout."),
                new Tag()
                    .name("URLs")
                    .description("Criação, consulta, listagem e remoção de URLs."),
                new Tag().name("Redirect").description("Resolução pública de short codes.")))
        .externalDocs(
            new ExternalDocumentation()
                .description("Documentação do projeto no GitHub")
                .url("https://github.com/LucasRibasCardoso/rest-api-url-shortener"));
  }

  private Components openApiComponents() {
    return new Components()
        .addSecuritySchemes(BEARER_AUTH_SCHEME, bearerAuthSecurityScheme())
        .addSchemas(FIELD_ERROR_SCHEMA, fieldErrorSchema())
        .addSchemas(API_ERROR_SCHEMA, apiErrorSchema())
        .addSchemas(API_VALIDATION_ERROR_SCHEMA, apiValidationErrorSchema())
        .addParameters(IDEMPOTENCY_KEY_PARAMETER, idempotencyKeyHeaderParameter())
        .addParameters(REFRESH_TOKEN_COOKIE_REQUIRED_PARAMETER, refreshTokenCookieParameter(true))
        .addParameters(REFRESH_TOKEN_COOKIE_OPTIONAL_PARAMETER, refreshTokenCookieParameter(false))
        .addHeaders(RETRY_AFTER_HEADER, retryAfterHeader())
        .addHeaders(LOCATION_HEADER, locationHeader())
        .addHeaders(SET_COOKIE_HEADER, setCookieHeader());
  }

  private SecurityScheme bearerAuthSecurityScheme() {
    return new SecurityScheme()
        .type(SecurityScheme.Type.HTTP)
        .scheme("bearer")
        .bearerFormat("JWT")
        .description("JWT access token emitido pelo fluxo de autenticação.");
  }

  private Schema<?> apiErrorSchema() {
    return new ObjectSchema()
        .description("Erro padrão da API no formato Problem Details.")
        .required(List.of("type", "title", "status", "detail", "errorCode"))
        .addProperty(
            "type",
            new StringSchema()
                .description("Tipo estável do problema.")
                .example("/errors/not-found"))
        .addProperty(
            "title", new StringSchema().description("Título do erro.").example("Não encontrado"))
        .addProperty("status", new IntegerSchema().description("Status HTTP.").example(404))
        .addProperty(
            "detail",
            new StringSchema()
                .description("Mensagem legível do erro.")
                .example("URL não encontrada."))
        .addProperty(
            "instance",
            new StringSchema()
                .description("Caminho da requisição, quando informado.")
                .example("/api/v1/urls/abc123"))
        .addProperty(
            "errorCode",
            new StringSchema()
                .description("Código estável para tratamento por clientes.")
                .example("URL_NOT_FOUND"));
  }

  private Schema<?> apiValidationErrorSchema() {
    return new ObjectSchema()
        .description("Erro de validação da API no formato Problem Details.")
        .required(List.of("type", "title", "status", "detail", "errorCode", "errors"))
        .addProperty(
            "type",
            new StringSchema()
                .description("Tipo estável do problema.")
                .example("/errors/validation"))
        .addProperty(
            "title", new StringSchema().description("Título do erro.").example("Validação"))
        .addProperty("status", new IntegerSchema().description("Status HTTP.").example(400))
        .addProperty(
            "detail",
            new StringSchema()
                .description("Mensagem legível do erro.")
                .example("Um ou mais campos estão inválidos."))
        .addProperty(
            "instance",
            new StringSchema()
                .description("Caminho da requisição, quando informado.")
                .example("/api/v1/urls"))
        .addProperty(
            "errorCode",
            new StringSchema()
                .description("Código estável para tratamento por clientes.")
                .example("REQUEST_VALIDATION_FAILED"))
        .addProperty(
            "errors",
            new ArraySchema()
                .description("Erros por campo ou parâmetro.")
                .items(new Schema<>().$ref("#/components/schemas/" + FIELD_ERROR_SCHEMA)));
  }

  private Schema<?> fieldErrorSchema() {
    return new ObjectSchema()
        .description("Item de erro de validação por campo ou parâmetro.")
        .required(List.of("field", "message"))
        .addProperty(
            "field",
            new StringSchema().description("Campo ou parâmetro inválido.").example("originalUrl"))
        .addProperty(
            "message",
            new StringSchema()
                .description("Mensagem de validação.")
                .example("URL deve ser HTTP ou HTTPS."));
  }

  private Parameter idempotencyKeyHeaderParameter() {
    return new Parameter()
        .name("Idempotency-Key")
        .in("header")
        .required(true)
        .description("Chave idempotente exigida por operações críticas de escrita.")
        .schema(new StringSchema().format("uuid").example("123e4567-e89b-12d3-a456-426614174000"));
  }

  private Parameter refreshTokenCookieParameter(boolean required) {
    return new Parameter()
        .name("refreshToken")
        .in("cookie")
        .required(required)
        .description("Refresh token seguro emitido em cookie HttpOnly, Secure e SameSite=Strict.")
        .schema(new StringSchema().example("<refresh_token>"));
  }

  private Header retryAfterHeader() {
    return new Header()
        .description("Tempo em segundos que o cliente deve aguardar antes de tentar novamente.")
        .schema(new IntegerSchema().example(60));
  }

  private Header locationHeader() {
    return new Header()
        .description("URI do recurso criado ou URL de destino em respostas de redirect.")
        .schema(new StringSchema().format("uri").example("https://example.com"));
  }

  private Header setCookieHeader() {
    return new Header()
        .description("Cookie `refreshToken` emitido ou expirado pela API de autenticação.")
        .schema(
            new StringSchema()
                .example(
                    "refreshToken=<refresh_token>; Path=/api/v1/auth; Max-Age=604800; Expires=Wed, 15 Jul 2026 12:00:00 GMT; Secure; HttpOnly; SameSite=Strict"));
  }
}
