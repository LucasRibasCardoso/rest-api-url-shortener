# Relatorio de Auditoria Tecnica - Seguranca, Robustez e Escalabilidade

Data: 2026-05-18

Escopo analisado: IAM/Auth/Security, URL shortener, Redis, DynamoDB, PostgreSQL/JPA, configuracoes Spring, testes, Docker Compose, observabilidade e documentacao em `docs/`.

## 1. Resumo executivo

Principais riscos encontrados:

- **Critico:** `IdGeneratorAdapter` usa `AtomicLong` + `volatile baseId` de forma insegura sob concorrencia. Pode gerar IDs errados quando uma thread troca o bloco enquanto outra ainda calcula `baseId + offset`.
- **Alto:** rotacao de refresh token faz `find -> rotate -> save new -> save old` sem lock, versionamento ou update condicional. Duas requisicoes concorrentes com o mesmo refresh token podem emitir dois novos refresh tokens.
- **Alto:** codigo de verificacao de email e hash de token podem vazar por logs/toString: `ConsoleEmailSenderStrategy` loga o codigo; `UserAccount` e `RefreshToken` usam `@ToString` incluindo campos sensiveis.
- **Alto:** nao ha rate limit implementado para login, register, resend/verify email e redirect. Existe excecao `TooManyRequestsException`, mas nenhum componente concreto de rate limiting foi encontrado.
- **Medio/Alto:** scheduler `RefreshTokenCleanupTask` executara em todas as instancias. Hoje e idempotente o suficiente para limpeza, mas nao ha lock distribuido.
- **Medio:** URL nao possui status, expiracao, soft delete, contador de acessos nem invalidacao alem do delete fisico. O redirect redireciona qualquer item existente no DynamoDB/cache.
- **Medio:** configuracao AWS/DynamoDB usa endpoint e credenciais estaticas via propriedades obrigatorias, adequada para LocalStack, mas inadequada para producao sem profiles/env vars.
- **Medio:** observabilidade e testes de carga ainda sao iniciais. Actuator existe como dependencia, mas nao ha configuracao de endpoints, Prometheus, tracing, correlation ID ou metricas customizadas.

Pontos ja corretos:

- `SessionCreationPolicy.STATELESS` esta configurado em `SecurityConfig`.
- OSIV esta desativado em `application-dev.yml`.
- Refresh tokens sao persistidos em PostgreSQL, nao em memoria local.
- Idempotencia usa Redis com `SET NX EX` via `setIfAbsent`.
- Cache de URL usa Redis Cache com TTL de 24h.
- DynamoDB usa `attribute_not_exists(shortCode)` na criacao, evitando overwrite por colisao.
- `UserJpaRepository.findByEmailWithRolesAndPermissions` usa `@EntityGraph`, reduzindo risco de N+1 no login.
- Listagem de URLs por usuario usa DynamoDB GSI e cursor, com limite maximo no controller.
- Docker Compose possui PostgreSQL, Redis e LocalStack.
- Existem bases de teste com Testcontainers para PostgreSQL, Redis e LocalStack.

Pontos criticos antes de novas features:

1. Corrigir o gerador de IDs sob concorrencia.
2. Tornar a rotacao de refresh token atomicamente segura.
3. Remover vazamento de codigo/hash/senha de logs e `toString`.
4. Implementar rate limiting nos fluxos publicos.
5. Definir modelo de URL com `status`, `expiresAt`, soft delete e politica de cache.

Recomendacao geral: antes de adicionar features, feche os riscos de concorrencia e seguranca. Depois, avance para observabilidade, testes concorrentes e carga.

## 2. Matriz de riscos

| ID | Severidade | Area | Fluxo | Problema | Arquivos | Recomendacao |
|---|---|---|---|---|---|---|
| R1 | Critica | URL | Criacao de short URL | Gerador de ID local pode usar `baseId` de outro bloco sob concorrencia | `IdGeneratorAdapter` | Tornar estado do bloco atomico/sincronizado ou usar sequencia/Redis Lua/Id generator comprovado |
| R2 | Alta | IAM | Refresh | Rotacao sem lock/compare-and-set | `RefreshTokenUseCaseImpl`, `RefreshTokenJpaRepository` | Update atomico `where token_hash=? and revoked_at is null`, ou pessimistic/optimistic locking |
| R3 | Alta | Seguranca | Verificacao email/logs | Codigo de verificacao e hashes podem aparecer em logs/toString | `ConsoleEmailSenderStrategy`, `UserAccount`, `RefreshToken` | Nao logar OTP; excluir campos sensiveis de `toString` |
| R4 | Alta | Seguranca | Login/register/verify | Rate limit nao encontrado | `shared.exception.ratelimit` apenas | Criar rate limit Redis por IP/email/user/action |
| R5 | Media/Alta | Distribuido | Scheduler | Limpeza executa em todas as instancias | `RefreshTokenCleanupTask` | Usar ShedLock/lock Redis/Postgres ou mover para job externo |
| R6 | Media | URL | Redirect | Sem expiracao/status/soft delete; cache pode servir URL que deveria estar inativa no futuro | `Url`, `UrlEntity`, `ResolveUrlUseCaseImpl` | Adicionar estado/expiracao e validar antes do redirect |
| R7 | Media | URL | Contadores | Contagem de acessos nao encontrada | modulo `url` | Usar Redis `INCR/HINCRBY` e flush assincrono/agregado |
| R8 | Media | Observabilidade | Operacao | Sem metricas customizadas, tracing ou correlation ID | projeto | Micrometer timers/counters, request ID e OpenTelemetry |
| R9 | Media | Config | Producao | Secrets e credenciais no `application-dev.yml` | `application-dev.yml` | Usar env vars e profiles separados |
| R10 | Media | Transacoes | Register/verify/resend | Redis/email dentro ou acoplados a transacao relacional | use cases IAM | Usar after-commit/outbox quando entrega for critica |

## 3. Stateless e execucao distribuida

| Item | Status | Arquivo/classe/metodo | Risco | Como corrigir | Como testar |
|---|---|---|---|---|---|
| Sessao HTTP stateless | OK | `SecurityConfig.securityFilterChain` | `SessionCreationPolicy.STATELESS` esta configurado e CSRF desabilitado para API stateless | Manter | Web slice: endpoint protegido sem JWT retorna 401; com JWT valido funciona |
| Uso indevido de HTTP session | Nao encontrado | Busca por session nao encontrou uso | Sem risco identificado | Manter sem `HttpSession` | `rg "HttpSession|getSession"` no CI ou ArchUnit |
| JWT multi-instancia | OK com ressalva | `JwtConfig`, `JwtTokenService` | Valida localmente com segredo compartilhado. Em producao, segredo precisa vir de secret manager/env | Externalizar secret e rotacionar | Teste com duas instancias/profile apontando mesmo secret |
| Refresh token em memoria | OK | `RefreshTokenEntity`, `RefreshTokenRepositoryAdapter` | Persistido no PostgreSQL | Manter | JPA slice validando persistencia/revogacao |
| Idempotencia compartilhada | OK | `RedisIdempotencyStore.saveInProgress` | Usa Redis com `setIfAbsent` e TTL | Adicionar namespace por usuario/rota/body hash | Redis slice concorrente |
| Cache de URL compartilhado | OK | `UrlRepositoryAdapter.findByShortCode`, `CacheConfig` | Cache Redis TTL 24h | Definir TTL por cache e invalidacao por estado | Teste cache hit/miss com Redis real |
| Rate limit distribuido | Nao encontrado | Nao ha implementacao concreta | Fluxos publicos vulneraveis a abuso/brute force | Redis token bucket/sliding window | Teste concorrente com varias requests bloqueadas |
| Estado mutavel em singleton | Risco | `IdGeneratorAdapter` | `baseId`/`offset` mutaveis compartilhados causam race | Sincronizar geracao inteira ou trocar estrategia | Teste concorrente stress com barreiras |
| Jobs/schedulers | Risco | `RefreshTokenCleanupTask.cleanupExpiredTokens` | Executa em cada instancia | Lock distribuido ou job unico | Subir 2 instancias e verificar apenas uma execucao |

## 4. Race conditions e concorrencia

### R1 - Geracao de ID local

- Severidade: Critica
- Probabilidade: Media/Alta em carga concorrente
- Impacto: Consistencia, escalabilidade
- Fluxo afetado: `POST /api/v1/urls`
- Arquivos: `IdGeneratorAdapter`, `CounterIdAdapter`, `ShortenUrlUseCaseImpl`
- Evidencia: `generateId()` faz `offset.getAndIncrement()` e retorna `baseId + currentOffset`; `allocateBlock()` altera `baseId` e reseta `offset`.
- Por que e risco: uma thread pode capturar `currentOffset < blockSize`, outra alocar novo bloco e alterar `baseId`, e a primeira retornar um ID calculado com o bloco errado.
- Correcao: manter `baseId` e `offset` no mesmo estado atomico, sincronizar toda a decisao de geracao, ou delegar IDs a Redis/Lua, PostgreSQL sequence, DynamoDB atomic counter ou Snowflake/ULID.
- Teste: unit stress deterministico com `blockSize` pequeno, muitas threads, varias repeticoes, validando exatamente o conjunto esperado; teste Redis slice para bloco.

### R2 - Refresh token rotation

- Severidade: Alta
- Probabilidade: Media
- Impacto: Seguranca, consistencia
- Fluxo afetado: `POST /api/v1/auth/refresh`
- Arquivos: `RefreshTokenUseCaseImpl`, `RefreshTokenJpaRepository`, `RefreshTokenEntity`
- Evidencia: fluxo `findByTokenHash -> oldToken.rotate -> save(newToken) -> save(oldToken)`.
- Por que e risco: duas requisicoes simultaneas podem ler token ativo antes de qualquer uma salvar `revokedAt`, gerando dois tokens novos.
- Correcao: criar metodo atomico `rotateIfActive(oldHash, newHash, replacedById)` ou `revokeActiveTokenByHash` retornando count; so emitir novo token se exatamente 1 linha foi atualizada. Alternativas: `@Version`, `SELECT FOR UPDATE`, ou unique/partial constraint adicional para cadeia ativa.
- Teste: JPA/integration concorrente com duas chamadas simultaneas ao mesmo refresh token; sucesso esperado em uma, `TokenCompromisedException`/401 na outra.

### R3 - Verificacao de email

- Severidade: Media
- Probabilidade: Media
- Impacto: Consistencia, UX
- Fluxo: `POST /api/v1/auth/verify-email`
- Arquivos: `VerifyEmailUseCaseImpl`, `EmailVerificationTokenAdapter`
- Evidencia: token Redis e lido, usuario e salvo, token e removido apenas `afterCommit`.
- Risco real: duas requisicoes concorrentes com o mesmo codigo podem passar antes do delete. O dominio torna a segunda verificacao idempotente se usuario ja estiver ativo, e a PK de `user_roles` evita duplicidade, mas o fluxo ainda nao consome o token atomicamente.
- Correcao: consumir codigo com operacao atomica Redis (Lua `GET` + `DEL` se match) ou `GETDEL` quando adequado.
- Teste: duas verificacoes simultaneas; apenas uma deve consumir o token, a outra deve receber codigo expirado/invalido.

### R4 - Idempotencia

- Severidade: Media
- Probabilidade: Media
- Impacto: UX, consistencia
- Fluxo: todos os URIs protegidos por `app.idempotency.protected-uris`
- Arquivos: `IdempotencyFilter`, `RedisIdempotencyStore`
- Pontos fortes: usa `SET NX EX` para estado em progresso e TTL para respostas concluidas.
- Riscos: chave nao e vinculada ao usuario, rota, metodo ou hash do body; reutilizar a mesma chave em outra operacao pode retornar resposta errada. Headers da resposta original tambem nao sao preservados.
- Correcao: chave composta `user/method/path/idempotencyKey`, armazenar hash do body e rejeitar mismatch; cachear headers relevantes.
- Teste: mesma key com body diferente deve retornar conflito; mesma key com mesma request deve retornar resposta cacheada.

### R5 - DynamoDB URL creation

- Status: OK com melhoria
- Arquivo: `UrlRepositoryAdapter.save`
- Evidencia: usa `attribute_not_exists(#pk)` e traduz `ConditionalCheckFailedException`.
- Risco restante: `ShortenUrlUseCaseImpl` nao faz retry/backoff em colisao. Com gerador corrigido, colisao deve ser rara, mas ainda deve haver retry limitado.
- Teste: mock do repo falha com colisao na primeira tentativa e sucesso na segunda.

## 5. Banco de dados, constraints e transacoes

| Item | Status | Arquivo/Migration | Risco | Correcao recomendada |
|---|---|---|---|---|
| Email unico | OK | `V2__create_users_table.sql` `uk_users_email` | Baixo | Manter CITEXT + unique |
| Username | Nao encontrado | Nao ha username | N/A | Sem acao |
| Role name unico | OK | `V3__create_roles_and_permissions.sql` | Baixo | Nomear constraint explicitamente se quiser traducao fina |
| Permission name unico | OK | `V3__create_roles_and_permissions.sql` | Baixo | Nomear constraint explicitamente |
| Role default unico | OK | `V6__create_indexes.sql` `roles_one_default` | Baixo | Manter |
| User-role sem duplicidade | OK | `V3`, PK composta | Baixo | Manter |
| Role-permission sem duplicidade | OK | `V3`, PK composta | Baixo | Manter |
| Refresh token hash unico | OK | `V4` | Baixo | Manter |
| Indice refresh por hash | Precisa investigar | `token_hash UNIQUE` cria indice unico | Baixo | OK via unique index |
| Indice login por email | OK | `uk_users_email` | Baixo | Manter |
| Indice tokens ativos por user | OK com typo | `V6 idx_refresh_tokens_active_by_userc` | Baixo | Corrigir nome somente em nova migration se incomodar |
| Status/plan validos | OK | `V2`, `V7` checks | Baixo | Manter |
| `@Version` | Nao encontrado | entidades JPA | Medio em updates concorrentes | Adicionar onde houver alteracao concorrente relevante |
| Traducao de constraints | Parcial | `DataIntegrityExceptionTranslator` | So mapeia `uk_users_email` | Expandir quando constraints virarem erros de dominio |

Transacoes:

- `RegisterUserUseCaseImpl`, `VerifyEmailUseCaseImpl`, `LoginUseCaseImpl`, `RefreshTokenUseCaseImpl`, `LogoutUseCaseImpl` usam `@Transactional`.
- `ResendVerificationUseCaseImpl` usa `@Transactional(readOnly = true)`, mas escreve Redis e publica evento. Isso nao quebra JPA, mas a anotacao comunica semantica errada. Recomenda-se remover transacao ou manter read-only apenas para leitura relacional e publicar apos leitura.
- `ShortenUrlUseCaseImpl`, `DeleteUrlUseCaseImpl`, `ResolveUrlUseCaseImpl` nao usam transacao relacional, o que e aceitavel porque usam DynamoDB/Redis.
- Chamadas externas: envio de email esta com `@TransactionalEventListener(AFTER_COMMIT)`, ponto positivo. O armazenamento Redis do codigo de verificacao em register ocorre antes do commit relacional; se o commit falhar apos o Redis set, pode sobrar codigo para usuario inexistente.
- Outbox Pattern: recomendavel quando email/verificacao precisar garantia forte de entrega e reprocessamento.

## 6. N+1 e JPA

Pontos corretos:

- `UserJpaRepository.findByEmailWithRolesAndPermissions` usa `@EntityGraph(attributePaths = {"roles", "roles.permissions"})`, adequado para login e autenticacao.
- `spring.jpa.open-in-view=false` em `application-dev.yml`, bom para revelar lazy loading indevido.
- Nao foram encontrados endpoints de listagem JPA com `findAll()` sem paginacao.
- Entidades JPA nao sao expostas diretamente em responses.

Riscos:

- `UserAccountRepositoryAdapter.findById` usa `findById` simples e depois mapeia `UserEntity` para dominio. Se o mapper acessar roles/permissions com OSIV off, pode gerar LazyInitializationException ou N+1 em evolucoes futuras. Hoje isso aparece no refresh, em `RefreshTokenUseCaseImpl.buildAuthenticatedUser`, que usa `userRepository.findById` e depois `user.getRoles()`.
- `@ToString` em dominio nao causa N+1 JPA diretamente porque dominio e separado de entidade, mas pode vazar dados sensiveis.

Correcoes:

- Criar `findByIdWithRolesAndPermissions` com `@EntityGraph` e usar em refresh quando for montar JWT.
- Para futuras listagens JPA, exigir `Pageable` e projections.
- Adicionar teste JPA slice para `findByEmailWithRolesAndPermissions` e `findByIdWithRolesAndPermissions`.

## 7. URL Shortener: criacao, redirect, cache e contadores

Fluxo atual encontrado:

1. `UrlController.shortenUrl` recebe usuario autenticado e `originalUrl`.
2. `ShortenUrlUseCaseImpl` chama `IdGeneratorPort.generateId()`.
3. `UrlEncoderAdapter` gera hashid/base62.
4. `UrlRepositoryAdapter.save` grava no DynamoDB com condition expression contra colisao.
5. `RedirectController.redirectToOriginalUrl` chama `ResolveUrlUseCaseImpl`.
6. `UrlRepositoryAdapter.findByShortCode` busca via Spring Cache Redis; em miss, DynamoDB `getItem`.
7. Redirect retorna `302 Location`.

Pontos fortes:

- Redirect e publico e simples.
- Cache Redis antes do DynamoDB via `@Cacheable`.
- TTL global de 24h.
- Delete invalida cache com `@CacheEvict`.
- Criacao no DynamoDB e condicional.
- Listagem por usuario usa GSI `user-index` e cursor.

Riscos:

- Gerador de ID concorrente e o maior risco do modulo.
- Nao ha retry/backoff em `ShortenUrlUseCaseImpl` para `ShortCodeCollisionException`.
- Nao ha `status`, `expiresAt` ou soft delete; logo nao existem os fluxos de URL expirada/desativada.
- Nao ha contador de acessos.
- Nao ha fallback explicito para Redis indisponivel; erro de cache pode derrubar redirect.
- Nao ha timeouts/retry policy customizados para DynamoDB client.
- `HttpUrlValidator` aceita hosts privados/localhost se enviados; isso pode facilitar phishing interno/SSRF indireto se consumidores seguirem URLs automaticamente.

Melhorias recomendadas:

- Corrigir ID generator.
- Adicionar retry limitado para colisao.
- Adicionar campos `status`, `expiresAt`, `deletedAt` no modelo DynamoDB e validar no resolve.
- Preferir soft delete + cache eviction.
- Adicionar contador Redis atomico no redirect e flush assincrono.
- Configurar timeouts/retries AWS SDK e comportamento de degradacao quando Redis falhar.

Metricas que deveriam ser medidas:

- `url.redirect.latency`, `url.redirect.cache.hit`, `url.redirect.cache.miss`, `url.redirect.not_found`, `url.redirect.expired`, `url.create.latency`, `url.create.collision`, `url.counter.increment.failed`, latencia DynamoDB/Redis.

Testes necessarios:

- Cache hit/miss com Redis real.
- Redirect para inexistente.
- Futuro: redirect expirado/desativado nao redireciona.
- Criacao concorrente de URLs.
- Colisao DynamoDB com retry.
- Redis indisponivel em redirect.

## 8. Redis

| Chave/Fluxo Redis | Uso atual | TTL | Atomico? | Risco | Recomendacao |
|---|---|---|---|---|---|
| `idempotency:{key}` | Estado em progresso/concluido | 2 min em progresso, 24h concluido | Sim no acquire (`SET NX EX`) | Key nao vinculada a rota/body/user | Chave composta + hash de payload |
| `auth:email-verification:{email}` | Codigo de verificacao | 10 min | Store e get separados | Duas verificacoes podem ler mesmo codigo | Usar consumo atomico via Lua/GETDEL |
| `url:counter:id` | Alocacao de blocos de ID | Sem TTL | `INCRBY` atomico | Sem TTL e intencional; risco se Redis perder persistencia | Redis persistente/replicado ou gerador alternativo |
| `urls::{shortCode}` | Cache Spring de URL | 24h global | Cache abstraction | Futuro risco de servir URL inativa se invalidacao falhar | TTL por cache + version/status + eviction robusta |
| Rate limit | Nao encontrado | N/A | N/A | Abuso/brute force | Token bucket/sliding window em Redis |
| Access counters | Nao encontrado | N/A | N/A | Sem metricas de uso | `INCR/HINCRBY` e flush |

## 9. DynamoDB

Modelo atual:

- Tabela `url`.
- Partition key: `shortCode`.
- GSI: `user-index` com partition key `userId`.
- Campos: `userId`, `shortCode`, `originalUrl`, `createdAt`.
- Criacao: `putItem` com `attribute_not_exists(shortCode)`.
- Leitura redirect/detalhe/delete: `getItem`/`deleteItem` por `shortCode`.
- Listagem por usuario: query no GSI, sem scan.

Pontos corretos:

- Nao foi encontrado `scan` em caminho critico.
- Criacao condicional existe.
- LocalStack configurado em Docker Compose e em suporte de teste.

Riscos:

- Sem sort key no GSI; listagem por usuario nao ordena por `createdAt` de forma controlada.
- Delete nao tem condicao de ownership/status no DynamoDB; ownership e validado antes no use case, mas ha janela entre leitura e delete.
- Sem TTL nativo para URL expirada.
- Sem timeout/retry policy explicita no client.
- Sem tratamento especifico para throttling alem do handler global de `SdkException`.

Recomendacoes para alta escala:

- GSI `userId + createdAt` se precisar listagem ordenada/paginacao previsivel.
- Conditional delete/update com `userId = :requester` quando nao for admin.
- TTL nativo para itens realmente descartaveis, ou status/expiration para auditoria.
- Retry/backoff controlado para conditional/throttling.
- Metricas de consumed capacity, throttling e latencia.

## 10. Seguranca

Pontos corretos:

- Stateless JWT.
- Authorities no claim `authorities` sem prefixo para permissao, coerente com `@PreAuthorize`.
- Refresh token em cookie `HttpOnly`, `Secure`, `SameSite=Strict`, path restrito.
- Password hashing com BCrypt.
- ProblemDetail centralizado.
- Mensagem de resend verification evita enumeracao direta.
- Ownership em detalhe/delete de URL e controlado por use case.

Problemas:

- Alta: `ConsoleEmailSenderStrategy` loga o codigo de verificacao. Em producao, OTP nao deve ir para log.
- Alta: `@ToString` em `UserAccount` inclui `passwordHash`; `@ToString` em `RefreshToken` inclui `tokenHash`.
- Alta: sem rate limit para login/register/resend/verify.
- Media: `application-dev.yml` contem secrets e credenciais. Pode ser aceitavel para dev, mas deve ser explicitamente impedido em prod.
- Media: CORS/headers de seguranca nao foram configurados explicitamente. Defaults do Spring ajudam, mas producao precisa politica clara.
- Media: JWT nao consulta estado atual do usuario a cada request. Isso e bom para performance/stateless, mas usuario bloqueado pode manter access token valido ate expirar.
- Media: URL validator permite `localhost`, IP privado, metadata IP e dominios internos. Para um shortener publico, isso pode ser usado para mascarar URL maliciosa.
- Baixa/Media: Login retorna excecoes distintas para conta desabilitada/bloqueada/not found em adapter; avaliar enumeracao conforme contrato desejado.

## 11. Performance e carga

Encontrado:

- Scripts k6/Gatling/JMeter: Nao encontrado no projeto.
- Testes concorrentes: parcial, `IdGeneratorAdapterTest`.
- Testcontainers: encontrado para PostgreSQL, Redis e LocalStack.
- Testes com Redis real: sim, slice Redis.
- Testes com Postgres real: sim, slice JPA.
- Testes com LocalStack/DynamoDB: suporte em integration base; teste concreto end-to-end nao evidenciado.
- Actuator: dependencia presente; configuracao de endpoints/Prometheus nao encontrada.
- Micrometer/Prometheus/Grafana/OpenTelemetry: nao encontrado no projeto alem do Actuator implicito.

Cenarios prioritarios de carga:

| Prioridade | Cenario | Endpoint | Carga inicial | Metricas | Criterio |
|---|---|---|---|---|---|
| P1 | Redirect cache hit | `GET /r/{code}` | 100 VUs, 5 min | p95/p99, hit ratio, erro | p95 baixo e erro < 0.1% |
| P1 | Criacao concorrente | `POST /api/v1/urls` | 50 VUs, 5 min | colisao, erro, latencia Redis/DDB | sem duplicidade e erro controlado |
| P1 | Refresh concorrente mesmo token | `POST /api/v1/auth/refresh` | 20 chamadas simultaneas | tokens emitidos | apenas 1 sucesso |
| P1 | Login brute force | `POST /api/v1/auth/login` | 20 VUs por email/IP | bloqueios | rate limit deve bloquear |
| P2 | Cache miss redirect | `GET /r/{code}` apos limpar Redis | 50 VUs | DDB latency/throttle | sem saturar DDB |
| P2 | Redis indisponivel | redirect/create | falha injetada | erro/fallback | redirect degradado conforme contrato |
| P2 | DynamoDB lento | redirect/create | latencia injetada | timeout/p99 | timeout controlado |
| P2 | Register/resend/verify | auth publicos | 20 VUs | email/Redis/Postgres | sem abuso e sem duplicidade |
| P3 | URL inexistente/expirada/desativada | `GET /r/{code}` | 50 VUs | 404/410 rate | respostas consistentes |

## 12. Observabilidade

Encontrado:

- `spring-boot-starter-actuator` no `pom.xml`.
- Healthcheck apenas no Docker Compose para PostgreSQL.
- Logs padronizados parcialmente no exception handler.

Nao encontrado no projeto:

- Configuracao `management.endpoints.web.exposure`.
- Prometheus registry.
- Grafana dashboards.
- OpenTelemetry/tracing.
- Correlation ID/request ID.
- Logs estruturados JSON.
- Metricas customizadas para redirect, cache, idempotencia, rate limit, refresh reuse, Redis/DynamoDB/PostgreSQL failures.

Prioridade:

1. Expor health/readiness/liveness e metrics.
2. Adicionar request/correlation ID.
3. Criar metricas customizadas do fluxo de redirect e auth.
4. Adicionar Prometheus/Grafana local.
5. Adicionar tracing distribuido quando houver mais dependencias/servicos.

## 13. Testes recomendados

| Prioridade | Tipo de teste | Fluxo | O que valida | Por que e importante |
|---|---|---|---|---|
| P1 | Unit stress | ID generator | IDs unicos/corretos sob concorrencia | Evita corrupcao de short code |
| P1 | Integration/JPA concorrente | Refresh | Apenas uma rotacao por token | Evita token reuse |
| P1 | Redis slice concorrente | Verify email | Token consumido uma vez | Evita dupla verificacao |
| P1 | Web/security slice | Auth public/protected | 401/403/permitAll corretos | Evita exposicao de endpoints |
| P1 | Security/unit | ToString/logs | Campos sensiveis ausentes | Evita vazamento |
| P2 | Redis slice | Idempotency | mesma key/body ok, body diferente conflito | Evita replay incorreto |
| P2 | Unit/integration | Short URL collision | retry/backoff | Resiliencia a colisao |
| P2 | Integration LocalStack | Redirect cache miss | DDB get + cache populate | Protege caminho critico |
| P2 | Redis slice | Cache TTL | TTL e eviction | Evita stale cache |
| P2 | JPA slice | EntityGraph | roles/permissions carregadas | Evita N+1/lazy errors |
| P2 | Load/k6 | Redirect hit/miss | p95/p99/erro | Baseline de performance |
| P3 | Chaos/integration | Redis/DDB indisponivel | fallback/erro padronizado | Robustez operacional |

## 14. Correcoes priorizadas

### Prioridade 1 - Corrigir antes de novas features

1. Corrigir `IdGeneratorAdapter`.
2. Corrigir refresh token rotation com operacao atomica.
3. Remover `@ToString` sensivel e log de OTP.
4. Implementar rate limit para login/register/verify/resend.
5. Adicionar testes concorrentes para ID generator e refresh.

### Prioridade 2 - Corrigir antes de publicar o projeto como portfolio

1. Adicionar status/expiracao/soft delete de URL.
2. Implementar contadores atomicos de redirect.
3. Melhorar idempotencia com body hash e escopo por rota/usuario.
4. Adicionar observabilidade minima: actuator config, Prometheus, metricas customizadas, request ID.
5. Criar scripts k6 para redirect e auth.
6. Externalizar secrets/configs por env vars.

### Prioridade 3 - Melhorias evolutivas

1. Outbox Pattern para email/eventos.
2. Lock distribuido para schedulers.
3. DynamoDB GSI com sort key `createdAt`.
4. Timeouts/retry policy customizados para AWS SDK e Redis.
5. Dashboards Grafana e tracing OpenTelemetry.

## 15. Plano de aprendizado

- Race conditions: estudar interleavings, atomicidade composta, CAS, locks e testes concorrentes.
- Optimistic locking: `@Version`, conflitos JPA e quando preferir pessimistic locking.
- Conditional writes no DynamoDB: `ConditionExpression`, conditional update/delete e tratamento de `ConditionalCheckFailedException`.
- Redis atomic operations: `SET NX EX`, `INCRBY`, `GETDEL`, Lua scripts e TTL.
- N+1 no Hibernate: lazy/eager, `EntityGraph`, `join fetch`, projections e OSIV off.
- Transacoes no Spring: boundaries, rollback, after commit, eventos transacionais e chamadas externas.
- Idempotencia: escopo de chave, payload hash, replay seguro e respostas cacheadas.
- Outbox Pattern: persistencia + publicacao confiavel.
- Observabilidade: RED/USE metrics, Micrometer, Prometheus, logs estruturados, tracing.
- Testes de carga com k6: cenarios, VUs, thresholds, p95/p99 e testes de falha parcial.
