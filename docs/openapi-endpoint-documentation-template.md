# Template para Documentar Endpoints OpenAPI

Use este template quando for documentar um endpoint da API usando o padrão de interfaces `*ApiDocs`.
O objetivo é produzir documentação precisa a partir do fluxo real implementado no código, evitando descrições genéricas, inventadas ou divergentes do comportamento testado.
A documentação deve representar o contrato público da API HTTP: método, rota, autenticação, permissões, request, responses, headers, cookies, status codes, validações e efeitos observáveis pelo cliente.
Não exponha detalhes internos de implementação, como nomes de tabelas, filas, chaves Redis, adapters, repositories, providers, locks, estruturas internas de cache ou decisões que não impactem diretamente o cliente HTTP.
---

## Entrada esperada
Ao usar este template, informe:
- Endpoint alvo: método HTTP e rota, por exemplo `POST /api/v1/auth/register`.
- Controller principal.
- Arquivos principais do fluxo, quando conhecidos.
- DTOs de request e response, quando conhecidos.
- Use case ou service chamado pelo controller, quando conhecido.
- Exception handler global, quando conhecido.
- Testes de integração, slice ou unitários relacionados, quando conhecidos.
- Configurações relevantes, quando conhecidas:
  - security;
  - rate limit;
  - idempotência;
  - cookies;
  - OpenAPI global.
Exemplo:
```text
Documentar POST /api/v1/auth/register.
Analisar:
- AuthController
- AuthApiDocs, se já existir
- RegisterUserUseCase / implementação
- RegisterRequestDto
- GenericMessageResponseDto
- GlobalExceptionHandler
- exceções de domínio relacionadas ao registro
- AuthRegistrationIT
- AuthControllerTest
- configuração de rate limit para register

⸻

Passo 0: Verificar configuração global OpenAPI

Antes de documentar endpoints individualmente, verifique se já existe uma configuração global OpenAPI no projeto.

Procurar por classes como:

* OpenApiConfig
* SwaggerConfig
* SpringDocConfig
* classes com @OpenAPIDefinition
* classes com @SecurityScheme
* configuração de /docs, /swagger-ui ou /v3/api-docs

A configuração global deve concentrar, quando aplicável:

* título da API;
* descrição geral;
* versão;
* servidores;
* tags globais;
* security scheme bearerAuth;
* descrição global dos schemas de erro;
* agrupamentos por domínio;
* customização do Swagger UI/springdoc.

Não crie configurações duplicadas por endpoint.

Se o endpoint exigir JWT, confirme que existe um security scheme compatível, por exemplo:

@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT"
)

Se não existir configuração global adequada, registre a lacuna antes de documentar endpoints protegidos.

⸻

Passo 1: Mapear o contrato HTTP real

Leia o método do controller e identifique:

* método HTTP;
* path completo;
* request body;
* query params;
* path variables;
* headers;
* cookies;
* status de sucesso;
* response body;
* headers emitidos;
* cookies emitidos;
* requisitos de autenticação;
* permissões via @PreAuthorize, quando houver;
* validações via Bean Validation;
* validadores customizados;
* content type consumido;
* content type produzido.

Não documente parâmetros internos de framework, como:

* HttpServletRequest;
* HttpServletResponse;
* Principal;
* Authentication;
* @AuthenticationPrincipal;
* mappers;
* commands;
* results internos;
* objetos de infraestrutura.

A documentação deve refletir o contrato público visível para o cliente HTTP.

⸻

Passo 2: Revisar o tratamento global de erros

Antes de documentar responses de erro, consulte o GlobalExceptionHandler ou componente equivalente.

Identifique:

* formato real do erro;
* status HTTP mapeado para cada exceção;
* campos retornados no corpo;
* content type;
* tratamento de validação Bean Validation;
* tratamento de autenticação;
* tratamento de autorização;
* tratamento de rate limit;
* tratamento de conflitos de negócio;
* tratamento de recurso não encontrado;
* tratamento de erros inesperados.

Documente o erro conforme o contrato público retornado pela API, não conforme o nome interno da exceção.

Exemplo:

Não documentar:

UserAlreadyExistsException

Preferir:

409 - E-mail já registrado.

Se o projeto usa ProblemDetail, documente os erros com esse schema.

Se o projeto usa um DTO próprio de erro, documente o schema padronizado do projeto.

Não documente exceções internas de infraestrutura individualmente, a menos que sejam convertidas em respostas HTTP públicas, estáveis e testadas.

Exemplos de detalhes que normalmente não devem aparecer na documentação pública:

* falha específica no Redis;
* erro específico do DynamoDB;
* nome de tabela;
* nome de índice;
* chave de cache;
* stack trace;
* nome de adapter;
* nome de repository;
* implementação de lock;
* detalhes de retry interno.

⸻

Passo 3: Seguir o fluxo de aplicação

Leia o fluxo chamado pelo controller até onde for necessário para entender o comportamento observável.

Analisar:

* mapper de request para command;
* command/result do caso de uso;
* use case e suas regras de orquestração;
* domain models e invariantes relevantes;
* validações de domínio;
* output ports/adapters apenas quando afetarem o contrato HTTP;
* exceções lançadas no fluxo;
* efeitos observáveis pelo cliente.

Mantenha a documentação no nível do contrato público da API.

Pode documentar:

* criação de recurso;
* alteração de estado;
* envio/início de verificação;
* emissão de cookie;
* expiração de cookie;
* redirect;
* idempotência;
* rate limit;
* permissões necessárias;
* status possíveis;
* headers públicos.

Não documentar:

* nomes de tabelas;
* nomes de filas;
* nomes de tópicos;
* nomes de buckets;
* chaves Redis;
* estrutura interna de entidades;
* implementação de adapters;
* algoritmos internos que não impactam o cliente;
* detalhes de infraestrutura não observáveis.

⸻

Passo 4: Usar testes como fonte de cenários

Leia principalmente os testes de integração do fluxo.

Usar os testes para identificar:

* cenário de sucesso principal;
* cenários alternativos;
* validação de entrada;
* autenticação ausente;
* autenticação inválida;
* permissão insuficiente;
* conflitos de negócio;
* recurso não encontrado;
* recurso expirado;
* recurso deletado;
* rate limit;
* idempotência;
* cookies esperados;
* headers esperados;
* formato dos erros retornados.

Prioridade das fontes:

1. Testes de integração.
2. Testes slice/web.
3. Controller.
4. Exception handler.
5. Use case.
6. Domain model.
7. Configurações transversais.

Quando houver divergência entre teste, controller e use case, não invente documentação.

Registre a divergência e proponha o menor ajuste necessário antes de documentar.

Exemplo de divergência:

Divergência encontrada:
- AuthController retorna 201 no registro.
- AuthRegistrationIT espera 200.
- A documentação não foi finalizada para este response até alinhar contrato e teste.
Ajuste sugerido:
- Definir 201 como status oficial para criação de usuário e ajustar o teste.

⸻

Passo 5: Criar ou atualizar a interface *ApiDocs

Use uma interface de documentação por controller.

Exemplos esperados:

iam.presentation.docs.AuthApiDocs
url.presentation.docs.UrlApiDocs
url.presentation.docs.RedirectApiDocs

O controller deve implementar sua interface:

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController implements AuthApiDocs {
}

A interface deve conter somente contrato de documentação.

Pode conter:

* @Tag no tipo;
* @Operation no método;
* @ApiResponses;
* @ApiResponse;
* @Parameter;
* @RequestBody, quando necessário para descrição ou exemplos;
* @SecurityRequirement, quando o endpoint exigir JWT;
* @Hidden, somente se o endpoint realmente não deve aparecer;
* assinaturas dos métodos do controller.

Não mover para a interface:

* lógica;
* validação;
* mapeamento;
* chamada de use case;
* tratamento de erro;
* regras de negócio;
* configuração de segurança;
* configuração de rate limit;
* configuração de idempotência.

A interface deve espelhar a assinatura pública do controller o suficiente para o springdoc associar a documentação ao endpoint.

⸻

Passo 6: Escrever @Tag

Use @Tag no tipo da interface para agrupar endpoints por domínio funcional.

Exemplos:

@Tag(
    name = "Auth",
    description = "Operações de autenticação, registro, verificação de e-mail e renovação de sessão"
)
public interface AuthApiDocs {
}
@Tag(
    name = "URLs",
    description = "Operações para criação, consulta, listagem e remoção de URLs encurtadas"
)
public interface UrlApiDocs {
}
@Tag(
    name = "Redirect",
    description = "Operações públicas de redirecionamento por short code"
)
public interface RedirectApiDocs {
}

Evite criar tags excessivamente específicas por endpoint.

⸻

Passo 7: Escrever @Operation

Cada endpoint deve ter:

* operationId;
* summary;
* description;
* tags, se necessário para garantir agrupamento;
* security, quando aplicável via @SecurityRequirement.

O operationId deve ser:

* único;
* estável;
* em inglês;
* orientado à ação;
* sem depender de nomes internos de classe.

Exemplos:

registerUser
loginUser
refreshAccessToken
verifyEmail
createShortUrl
listCurrentUserUrls
getCurrentUserUrlDetails
deleteCurrentUserUrl
redirectShortUrl

O summary deve ser:

* curto;
* objetivo;
* em português;
* em forma nominal ou verbo no infinitivo.

Bons exemplos:

Registrar usuário
Autenticar usuário
Renovar token de acesso
Verificar e-mail
Criar URL encurtada
Listar URLs do usuário autenticado
Consultar detalhes de URL
Remover URL encurtada
Redirecionar URL encurtada

Evite:

Endpoint que faz registro
API para cadastrar usuário no sistema
Realiza o cadastro de um usuário
Método para criar uma URL

A description deve explicar:

* comportamento prático;
* pré-condições;
* autenticação;
* permissões;
* efeitos observáveis;
* headers/cookies relevantes;
* idempotência, quando aplicável;
* rate limit, quando aplicável;
* observações de segurança.

Exemplo:

@Operation(
    operationId = "registerUser",
    summary = "Registrar usuário",
    description =
        """
        Cria uma conta de usuário e inicia o fluxo de verificação de e-mail.
        O endpoint é público. O e-mail informado deve ser único. A conta criada permanece pendente
        até a confirmação do código de verificação.
        """
)

⸻

Passo 8: Documentar autenticação e permissões

Para endpoint público:

* não adicione @SecurityRequirement;
* mencione na descrição que o endpoint é público, quando isso ajudar o consumidor da API.

Para endpoint autenticado:

* adicione @SecurityRequirement(name = "bearerAuth");
* documente na descrição que exige JWT válido;
* documente permissões relevantes;
* reflita exatamente o @PreAuthorize do controller.

Exemplo:

@Operation(
    operationId = "createShortUrl",
    summary = "Criar URL encurtada",
    description =
        """
        Cria uma nova URL encurtada para o usuário autenticado.
        Exige JWT válido e permissão `url:create`.
        """,
    security = @SecurityRequirement(name = "bearerAuth")
)

Não documente permissões que não sejam exigidas no código.

Se a regra do @PreAuthorize for composta, descreva de forma compreensível para o cliente, sem expor detalhes internos desnecessários.

Exemplo:

Exige usuário autenticado com permissão para criar URLs.

⸻

Passo 9: Documentar request body

Use as informações dos DTOs e validações.

Analisar:

* @NotNull;
* @NotBlank;
* @NotEmpty;
* @Email;
* @Size;
* @Min;
* @Max;
* @Pattern;
* enums;
* validators customizados;
* campos opcionais;
* campos obrigatórios;
* normalizações observáveis;
* defaults aplicados.

Use @RequestBody na interface quando for necessário melhorar descrição, obrigatoriedade, exemplos ou schema.

Exemplo:

@io.swagger.v3.oas.annotations.parameters.RequestBody(
    required = true,
    description = "Dados necessários para registrar um novo usuário"
)

Preferir exemplos seguros e realistas.

Não incluir:

* senhas reais;
* tokens reais;
* OTPs reais;
* cookies reais;
* e-mails reais de usuários;
* domínios internos;
* chaves de API;
* secrets;
* hashes reais.

Exemplos aceitáveis:

user@example.com
StrongPassword123!
https://example.com/articles/spring-boot
123456

Para OTP, token ou senha em exemplos, usar valores fictícios e claramente não reais.

Quando possível, colocar exemplos simples nos DTOs usando @Schema.

Exemplo:

@Schema(example = "user@example.com")
@Email
@NotBlank
String email;

⸻

Passo 10: Documentar parâmetros

Documente path variables e query params.

Para cada parâmetro, identificar:

* nome;
* localização;
* tipo;
* obrigatório/opcional;
* descrição;
* default;
* limites;
* enum values;
* formato;
* exemplo;
* validações.

Exemplo para path variable:

@Parameter(
    name = "shortCode",
    description = "Código curto da URL",
    required = true,
    example = "abc123"
)

Exemplo para query param:

@Parameter(
    name = "status",
    description = "Filtra URLs por status",
    example = "ACTIVE"
)

Não documente parâmetros internos de framework.

⸻

Passo 11: Documentar responses

Documente todos os status observáveis no controller, use case, exception handler e testes.

Para cada @ApiResponse, informe:

* responseCode;
* description;
* schema do corpo quando houver;
* ausência de corpo quando não houver;
* headers relevantes;
* cookies relevantes;
* content type, quando necessário.

A OpenAPI modela responses, headers, parâmetros, request body, security requirements e schemas como elementos próprios da operação, então esses itens devem aparecer quando fazem parte do contrato público.  

Exemplos devem ser coesos com o cenário real documentado.

Regras para exemplos de response:

* Schemas reutilizáveis devem descrever a estrutura do payload.
* Não colocar em schemas genéricos, como `ApiError`, exemplos de um cenário específico que possam aparecer em outros status.
* Exemplos de erro devem ficar no `@ApiResponse` do endpoint, no status e cenário correspondente.
* Cada cenário real relevante deve ter seu próprio `@ExampleObject`.
* Se o mesmo status tiver mais de um formato real de erro, use um schema reutilizável adequado, por exemplo `BadRequestError` com `oneOf`, e documente exemplos para cada formato.
* Para `400` com validação de DTO, incluir exemplo com `errors[]` e campos reais inválidos.
* Para `400` por `Idempotency-Key` ausente ou inválido, incluir exemplo sem `errors[]`, com o `errorCode` real.
* Para `409`, `429`, `401`, `403`, `404` e outros erros, usar `type`, `title`, `status`, `detail` e `errorCode` coerentes com o status e a causa real.
* Não reutilizar exemplo de `404`, como `URL_NOT_FOUND`, em responses `400`, `409` ou `429`.
* Não incluir tokens, senhas, cookies reais, OTPs ou valores sensíveis em exemplos.

Exemplo para `POST /api/v1/auth/register`:

```java
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
            }))
```

Exemplo:

@ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "Usuário registrado e verificação de e-mail iniciada",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = GenericMessageResponseDto.class)
        )
    ),
    @ApiResponse(
        responseCode = "400",
        description = "Dados inválidos",
        content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
            schema = @Schema(implementation = ProblemDetail.class)
        )
    ),
    @ApiResponse(
        responseCode = "409",
        description = "E-mail já registrado",
        content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
            schema = @Schema(implementation = ProblemDetail.class)
        )
    ),
    @ApiResponse(
        responseCode = "429",
        description = "Limite de requisições excedido",
        content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
            schema = @Schema(implementation = ProblemDetail.class)
        )
    )
})

Para respostas sem corpo, declarar ausência de content:

@ApiResponse(
    responseCode = "204",
    description = "URL removida com sucesso",
    content = @Content
)

Para 204, não declare schema de resposta.

Para 302, 301, 307 ou redirects similares, documente o header Location.

⸻

Passo 12: Documentar redirects

Para endpoints de redirect, documente obrigatoriamente:

* status HTTP exato usado no redirect;
* header Location;
* ausência de corpo;
* comportamento para short code inexistente;
* comportamento para short code deletado;
* comportamento para short code expirado, se existir;
* rate limit aplicável;
* cache headers, se forem emitidos;
* se o endpoint é público.

Exemplo:

@Operation(
    operationId = "redirectShortUrl",
    summary = "Redirecionar URL encurtada",
    description =
        """
        Redireciona o cliente para a URL original associada ao short code informado.
        O endpoint é público. Quando o short code é válido e ativo, a resposta contém o header
        `Location` com a URL de destino e não possui corpo.
        """
)
@ApiResponses({
    @ApiResponse(
        responseCode = "302",
        description = "Redirecionamento para a URL original",
        headers = {
            @Header(
                name = "Location",
                description = "URL original de destino",
                schema = @Schema(type = "string", format = "uri")
            )
        },
        content = @Content
    ),
    @ApiResponse(
        responseCode = "404",
        description = "Short code não encontrado",
        content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
            schema = @Schema(implementation = ProblemDetail.class)
        )
    ),
    @ApiResponse(
        responseCode = "429",
        description = "Limite de requisições excedido",
        content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
            schema = @Schema(implementation = ProblemDetail.class)
        )
    )
})

Não documente 302 se o controller usa outro status, como 301, 303, 307 ou 308.

⸻

Passo 13: Documentar paginação, filtros e ordenação

Para endpoints de listagem, documente:

* parâmetros de paginação;
* valores default;
* limites mínimos;
* limites máximos;
* filtros disponíveis;
* status aceitos;
* ordenação disponível;
* campo default de ordenação;
* direção default;
* schema da resposta paginada;
* comportamento para resultado vazio.

Exemplo:

Quando não houver URLs para os filtros informados, retorna página vazia com status 200.

Exemplo de parâmetros:

@Parameter(
    name = "page",
    description = "Número da página, iniciando em 0",
    example = "0"
)
@Parameter(
    name = "size",
    description = "Quantidade de itens por página",
    example = "20"
)
@Parameter(
    name = "status",
    description = "Filtra URLs por status",
    example = "ACTIVE"
)

Não documente filtros ou ordenações que não existem no código.

⸻

Passo 14: Documentar idempotência

Se o endpoint estiver protegido por idempotência, por exemplo em app.idempotency.protected-uris, documente:

* header Idempotency-Key;
* se é obrigatório;
* formato esperado, se houver;
* TTL, se for parte do contrato público;
* comportamento em replay;
* comportamento em conflito de payload;
* status retornado em conflito;
* quais respostas são cacheadas, se isso for definido como contrato.

Exemplo de descrição:

Este endpoint suporta idempotência por meio do header `Idempotency-Key`.
Requisições repetidas com a mesma chave e o mesmo payload retornam a resposta previamente gerada.
Requisições com a mesma chave e payload diferente são rejeitadas.

Exemplo de parâmetro:

@Parameter(
    name = "Idempotency-Key",
    description = "Chave única enviada pelo cliente para tornar a operação idempotente",
    required = true,
    in = ParameterIn.HEADER,
    example = "550e8400-e29b-41d4-a716-446655440000"
)

Não documente chaves Redis, hash interno do body ou detalhes de armazenamento.

⸻

Passo 15: Documentar rate limit

Se houver rate limit no fluxo, documente:

* resposta 429;
* schema do erro;
* header Retry-After, quando emitido;
* escopo geral do limite, quando for parte do contrato público;
* se o endpoint é protegido por política específica.

Exemplo:

@ApiResponse(
    responseCode = "429",
    description = "Limite de requisições excedido",
    headers = {
        @Header(
            name = "Retry-After",
            description = "Tempo em segundos até nova tentativa ser permitida",
            schema = @Schema(type = "integer", example = "60")
        )
    },
    content = @Content(
        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
        schema = @Schema(implementation = ProblemDetail.class)
    )
)

Não documente detalhes internos como:

* nome da policy interna;
* chave Redis;
* algoritmo interno;
* bucket interno;
* adapter usado;
* biblioteca usada, a menos que isso faça parte explícita da API pública.

⸻

Passo 16: Documentar cookies

Para endpoints que criam, renovam, exigem ou expiram cookies, documente:

* nome do cookie;
* se é criado;
* se é renovado;
* se é exigido;
* se é expirado;
* HttpOnly;
* Secure;
* SameSite;
* Path;
* Max-Age, se aplicável;
* resposta quando o cookie está ausente ou inválido.

Para refresh token, documentar o comportamento público sem expor o token.

Exemplo:

Em caso de autenticação bem-sucedida, o endpoint emite um cookie `refreshToken`
com `HttpOnly`, `Secure`, `SameSite=Strict` e path configurado para renovação de sessão.

Exemplo de response com Set-Cookie:

@ApiResponse(
    responseCode = "200",
    description = "Autenticação realizada com sucesso",
    headers = {
        @Header(
            name = "Set-Cookie",
            description = "Cookie HttpOnly contendo o refresh token",
            schema = @Schema(type = "string")
        )
    },
    content = @Content(
        mediaType = MediaType.APPLICATION_JSON_VALUE,
        schema = @Schema(implementation = LoginResponseDto.class)
    )
)

Não incluir valor real de cookie em exemplos.

⸻

Passo 17: Documentar schemas e DTOs

Ao revisar DTOs, adicionar ou ajustar @Schema quando isso melhorar a documentação.

Pode adicionar:

* description;
* example;
* requiredMode;
* minimum;
* maximum;
* enum values, se necessário;
* indicação de campos sensíveis.

Exemplo:

@Schema(description = "E-mail do usuário", example = "user@example.com")
@NotBlank
@Email
String email;

Para campos sensíveis:

@Schema(
    description = "Senha do usuário",
    example = "StrongPassword123!",
    accessMode = Schema.AccessMode.WRITE_ONLY
)
String password;

Evite duplicar no *ApiDocs exemplos que podem ficar melhor no próprio DTO.

Não alterar sem necessidade nomes de campos, serialização JSON ou comportamento de produção.

⸻

Passo 18: Validar endpoints públicos, autenticados e administrativos

Classifique o endpoint como:

* público;
* autenticado;
* autenticado com permissão;
* administrativo;
* interno/oculto.

Regras:

Endpoint público

* Não usar @SecurityRequirement.
* Descrever como público, quando útil.
* Documentar rate limit, se houver.

Endpoint autenticado

* Usar @SecurityRequirement(name = "bearerAuth").
* Descrever exigência de JWT válido.
* Documentar 401.

Endpoint com permissão

* Usar @SecurityRequirement(name = "bearerAuth").
* Descrever permissão exigida.
* Documentar 401 e 403.

Endpoint interno ou oculto

* Usar @Hidden somente se o endpoint realmente não deve aparecer na documentação pública.
* Não ocultar endpoint apenas por estar incompleto sem registrar a decisão.

⸻

Passo 19: Validar status codes esperados

Use esta lista como referência, mas documente somente o que for observável no código/testes:

* 200 OK: consulta, login, refresh, operação com corpo.
* 201 Created: criação de recurso.
* 202 Accepted: processamento assíncrono aceito.
* 204 No Content: sucesso sem corpo.
* 301/302/303/307/308: redirect.
* 400 Bad Request: request inválido, validação ou formato incorreto.
* 401 Unauthorized: autenticação ausente, inválida ou expirada.
* 403 Forbidden: autenticado sem permissão.
* 404 Not Found: recurso inexistente.
* 409 Conflict: conflito de negócio.
* 410 Gone: recurso removido/expirado, se o projeto usar.
* 422 Unprocessable Entity: regra semântica inválida, se o projeto usar.
* 429 Too Many Requests: rate limit.
* 500 Internal Server Error: erro interno genérico, se documentado como contrato.

Não adicione status apenas por possibilidade teórica.

⸻

Passo 20: Criar matriz de cenários do endpoint

Antes de finalizar, monte uma matriz com os cenários documentados.

Exemplo:

| Cenário | Status | Corpo | Headers/Cookies | Fonte |
|---|---:|---|---|---|
| Registro válido | 201 | GenericMessageResponseDto | - | AuthRegistrationIT |
| E-mail inválido | 400 | ProblemDetail | - | AuthControllerTest |
| E-mail já cadastrado | 409 | ProblemDetail | - | RegisterUserUseCaseTest |
| Rate limit excedido | 429 | ProblemDetail | Retry-After | RateLimitIT |

A matriz deve ser usada para conferir se a documentação está rastreável.

Se um status for documentado sem fonte clara, registrar como inferência e justificar.

⸻

Passo 21: Implementar a documentação

Ao implementar:

1. Criar ou atualizar a interface *ApiDocs no pacote presentation.docs.
2. Adicionar @Tag na interface.
3. Adicionar método correspondente ao endpoint.
4. Adicionar @Operation.
5. Adicionar @ApiResponses.
6. Adicionar @Parameter, quando necessário.
7. Adicionar @RequestBody, quando necessário.
8. Adicionar @SecurityRequirement, quando necessário.
9. Ajustar o controller para implementar a interface.
10. Ajustar DTOs com @Schema, se fizer sentido.
11. Não alterar comportamento de produção.

Exemplo estrutural:

@Tag(
    name = "Auth",
    description = "Operações de autenticação, registro, verificação de e-mail e renovação de sessão"
)
public interface AuthApiDocs {
    @Operation(
        operationId = "registerUser",
        summary = "Registrar usuário",
        description =
            """
            Cria uma conta de usuário e inicia o fluxo de verificação de e-mail.
            O endpoint é público. O e-mail informado deve ser único. A conta criada permanece
            pendente até a confirmação do código de verificação.
            """
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "201",
            description = "Usuário registrado e verificação de e-mail iniciada",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = GenericMessageResponseDto.class)
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Dados inválidos",
            content = @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = ProblemDetail.class)
            )
        ),
        @ApiResponse(
            responseCode = "409",
            description = "E-mail já registrado",
            content = @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = ProblemDetail.class)
            )
        ),
        @ApiResponse(
            responseCode = "429",
            description = "Limite de requisições excedido",
            content = @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = ProblemDetail.class)
            )
        )
    })
    ResponseEntity<GenericMessageResponseDto> register(
        @Valid @RequestBody RegisterRequestDto request
    );
}

⸻

Passo 22: Validar a documentação gerada

Após implementar a interface:

1. Rodar o menor teste relacionado, quando houver.
2. Rodar:

./mvnw clean compile

3. Se possível, subir a aplicação localmente e conferir:

/v3/api-docs
/docs
/swagger-ui.html

O caminho exato depende da configuração do springdoc no projeto. O springdoc gera a especificação OpenAPI a partir dos endpoints Spring e annotations, expondo a documentação em endpoints como /v3/api-docs e Swagger UI conforme configuração do projeto.  

Conferir manualmente:

* endpoint aparece na tag correta;
* summary está legível;
* description não está genérica;
* request schema está correto;
* response schemas estão corretos;
* exemplos de response são específicos do cenário real documentado;
* status codes batem com testes e código;
* security requirement aparece apenas onde deve;
* headers aparecem quando fazem parte do contrato;
* cookies aparecem quando fazem parte do contrato;
* endpoints públicos não aparecem como protegidos;
* endpoints protegidos aparecem com bearer auth;
* exemplos não contêm dados sensíveis.

⸻

Passo 23: Checklist final

Antes de concluir, confirmar:

* A interface *ApiDocs está no pacote presentation.docs.
* O controller implementa a interface correta.
* A documentação não mudou comportamento de produção.
* A documentação reflete controller, use case, exception handler e testes.
* Todos os status relevantes foram documentados.
* Nenhum status foi adicionado apenas por suposição.
* Headers relevantes foram documentados.
* Cookies relevantes foram documentados.
* Idempotência foi considerada.
* Rate limit foi considerado.
* Paginação, filtros e ordenação foram considerados em endpoints de listagem.
* Redirect foi documentado corretamente, quando aplicável.
* 204 não possui schema de resposta.
* 302/redirect possui Location, quando aplicável.
* Endpoints públicos não possuem @SecurityRequirement.
* Endpoints autenticados possuem @SecurityRequirement(name = "bearerAuth").
* Permissões documentadas refletem exatamente o código.
* Erros usam ProblemDetail ou o schema padronizado real do projeto.
* Exemplos de erro usam `type`, `title`, `status`, `detail` e `errorCode` coerentes com o cenário real.
* Schemas genéricos de erro não carregam exemplos específicos de outro status ou domínio.
* Nenhum segredo, token, OTP, cookie real ou credencial real foi adicionado.
* A documentação não expõe detalhes internos desnecessários.
* ./mvnw clean compile foi executado ou a impossibilidade foi registrada.
* Testes relacionados foram executados ou a impossibilidade foi registrada.

⸻

Formato esperado da entrega

Ao finalizar a documentação de um endpoint, informe:

## Endpoint documentado
- Endpoint: `METHOD /path`
- Interface criada/atualizada:
- Controller atualizado:
- DTOs ajustados:
- Configuração global alterada:
- Endpoint público/autenticado:
- Permissões exigidas:
- Idempotência:
- Rate limit:
- Cookies:
- Headers:
## Cenários cobertos
| Cenário | Status | Corpo | Headers/Cookies | Fonte |
|---|---:|---|---|---|
| ... | ... | ... | ... | ... |
## Testes consultados
- ...
## Comandos executados
- `./mvnw clean compile`
- ...
## Divergências encontradas
- Nenhuma.

Se houver divergência:

## Divergências encontradas
- Descrição da divergência:
- Arquivos envolvidos:
- Impacto na documentação:
- Ajuste mínimo sugerido:

Se algo não foi possível validar:

## Limitações
- Não foi possível executar os testes porque ...
- Não foi possível abrir `/docs` porque ...
- O comportamento de `429` não possui teste específico.
