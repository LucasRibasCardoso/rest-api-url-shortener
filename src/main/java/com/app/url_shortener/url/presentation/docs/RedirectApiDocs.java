package com.app.url_shortener.url.presentation.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;

@Tag(name = "Redirect", description = "Operações públicas de redirecionamento por short code.")
public interface RedirectApiDocs {

  @Operation(
      operationId = "redirectShortUrl",
      summary = "Redirecionar URL encurtada",
      description =
          """
          Redireciona o cliente para a URL original associada ao short code informado.
          O endpoint é público, não exige JWT e aceita short codes alfanuméricos de 1 a 64 caracteres.

          Quando o short code existe e está ativo, a resposta contém o header `Location` com a URL
          de destino e não possui corpo. Short codes inexistentes ou associados a URLs deletadas
          retornam a mesma resposta pública de não encontrado.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "302",
        description = "Redirecionamento para a URL original.",
        headers = @Header(name = HttpHeaders.LOCATION, ref = "#/components/headers/Location"),
        content = @Content),
    @ApiResponse(
        responseCode = "404",
        description = "Short code não encontrado ou URL encurtada deletada.",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(ref = "#/components/schemas/ApiError"),
                examples = {
                  @ExampleObject(
                      name = "shortCodeNotFound",
                      summary = "Short code inexistente",
                      value =
                          """
                          {
                            "type": "/errors/not-found",
                            "title": "Não encontrado",
                            "status": 404,
                            "detail": "URL não encontrada.",
                            "errorCode": "URL_NOT_FOUND"
                          }
                          """),
                  @ExampleObject(
                      name = "deletedShortUrl",
                      summary = "URL encurtada deletada",
                      value =
                          """
                          {
                            "type": "/errors/not-found",
                            "title": "Não encontrado",
                            "status": 404,
                            "detail": "URL não encontrada.",
                            "errorCode": "URL_NOT_FOUND"
                          }
                          """)
                }))
  })
  ResponseEntity<?> redirectToOriginalUrl(
      @Parameter(
              name = "shortCode",
              description = "Código curto alfanumérico da URL encurtada.",
              required = true,
              example = "aB3dE",
              schema = @Schema(type = "string", minLength = 1, maxLength = 64))
          @PathVariable
          String shortCode);
}
