package com.app.url_shortener.url.presentation.docs;

import com.app.url_shortener.security.principal.UserPrincipal;
import com.app.url_shortener.url.application.command.UrlStatusFilter;
import com.app.url_shortener.url.presentation.dto.request.ShortenUrlRequestDto;
import com.app.url_shortener.url.presentation.dto.response.UrlDetailsResponseDto;
import com.app.url_shortener.url.presentation.dto.response.UrlPageResponseDto;
import com.app.url_shortener.url.presentation.dto.response.UrlRankingResponseDto;
import com.app.url_shortener.url.presentation.dto.response.UrlResponseDto;
import com.app.url_shortener.url.presentation.validator.ValidRankingSize;
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
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@RequestMapping("/api/v1/urls")
@Tag(
    name = "URLs",
    description = "Operações para criação, consulta, listagem e remoção de URLs encurtadas.")
public interface UrlApiDocs {

  String SHORT_CODE_PATH = "/{shortCode:[a-zA-Z0-9]{1,64}}";

  @PostMapping
  @Operation(
      operationId = "createShortUrl",
      summary = "Criar URL encurtada",
      description =
          """
          Cria uma nova URL encurtada para o usuário autenticado.
          Exige JWT válido, permissão `url:create` e header `Idempotency-Key`.

          A URL informada deve usar HTTP ou HTTPS e apontar para um destino permitido pela política
          pública do encurtador. Em caso de sucesso, retorna os dados da URL criada e o header
          `Location` com a URL curta.

          Requisições repetidas com a mesma chave idempotente e o mesmo payload retornam a resposta
          previamente gerada. Requisições com a mesma chave e payload diferente são rejeitadas.
          """,
      security = @SecurityRequirement(name = "bearerAuth"))
  @Parameter(ref = "#/components/parameters/IdempotencyKeyHeader")
  @io.swagger.v3.oas.annotations.parameters.RequestBody(
      required = true,
      description = "Dados para criação da URL encurtada.",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = ShortenUrlRequestDto.class)))
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "URL encurtada criada com sucesso.",
        headers = @Header(name = HttpHeaders.LOCATION, ref = "#/components/headers/Location"),
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = UrlResponseDto.class),
                examples =
                    @ExampleObject(
                        name = "shortUrlCreated",
                        summary = "URL encurtada criada",
                        value =
                            """
                            {
                              "originalUrl": "https://example.com/articles/spring-boot",
                              "shortCode": "aB3dE",
                              "shortUrl": "http://localhost:8080/r/aB3dE",
                              "createdAt": "2026-05-10T14:30:00Z",
                              "status": "ACTIVE"
                            }
                            """))),
    @ApiResponse(
        responseCode = "400",
        description =
            "Payload inválido, destino não permitido ou problema no header Idempotency-Key.",
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
                                "field": "originalUrl",
                                "message": "A URL informada é inválida"
                              }
                            ]
                          }
                          """),
                  @ExampleObject(
                      name = "unsafeDestination",
                      summary = "Destino não permitido",
                      value =
                          """
                          {
                            "type": "/errors/validation",
                            "title": "Validação",
                            "status": 400,
                            "detail": "URL de destino não permitida.",
                            "errorCode": "URL_UNSAFE_DESTINATION"
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
        responseCode = "403",
        description = "Usuário autenticado sem permissão para criar URLs.",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(ref = "#/components/schemas/ApiError"),
                examples =
                    @ExampleObject(
                        name = "forbidden",
                        summary = "Permissão insuficiente",
                        value =
                            """
                            {
                              "type": "/errors/forbidden",
                              "title": "Acesso negado",
                              "status": 403,
                              "detail": "Acesso negado para esse recurso",
                              "errorCode": "AUTH_ACCESS_DENIED"
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
        responseCode = "429",
        description = "Limite de criação de URLs excedido.",
        headers = @Header(name = HttpHeaders.RETRY_AFTER, ref = "#/components/headers/RetryAfter"),
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
                            """)))
  })
  ResponseEntity<UrlResponseDto> shortenUrl(
      @Valid @RequestBody ShortenUrlRequestDto request,
      @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal user);

  @GetMapping(SHORT_CODE_PATH)
  ResponseEntity<UrlDetailsResponseDto> findUrlDetails(
      @PathVariable String shortCode,
      @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal user);

  @DeleteMapping(SHORT_CODE_PATH)
  ResponseEntity<Void> deleteUrl(
      @PathVariable String shortCode,
      @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal user);

  @GetMapping("/users/{userId}")
  ResponseEntity<UrlPageResponseDto> findAllUrlsByUserId(
      @PathVariable UUID userId,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
      @RequestParam(defaultValue = "ACTIVE") UrlStatusFilter status,
      @RequestParam(required = false) String cursor);

  @GetMapping("/me")
  ResponseEntity<UrlPageResponseDto> findAllMyUrls(
      @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal user,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
      @RequestParam(defaultValue = "ACTIVE") UrlStatusFilter status,
      @RequestParam(required = false) String cursor);

  @GetMapping("me/ranking")
  ResponseEntity<UrlRankingResponseDto> findMyTopUrls(
      @RequestParam(defaultValue = "3") @ValidRankingSize int rankingSize,
      @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal user);
}
