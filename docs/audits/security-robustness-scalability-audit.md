# Backlog de Auditoria — Segurança, Robustez e Escalabilidade

> Projeto: `app-url-shortener`
> Origem: auditoria técnica de segurança, robustez, concorrência, performance e escalabilidade
> Objetivo: acompanhar as correções necessárias antes de avançar para novas features e estudos mais avançados.

---

## 1. Visão geral

Este backlog organiza os riscos encontrados na auditoria em tarefas executáveis, priorizadas por impacto técnico.

### Status geral dos riscos

| ID | Risco | Severidade | Status |
|---|---|---:|---:|
| R1 | `IdGeneratorAdapter` inseguro sob concorrência | Crítica | Concluído |
| R2 | Refresh token rotation sem operação atômica | Alta | Concluído |
| R3 | Vazamento de dados sensíveis em logs/`toString` | Alta | Pendente |
| R4 | Rate limit não implementado | Alta | Pendente |
| R5 | Scheduler executa em múltiplas instâncias | Média/Alta | Pendente |
| R6 | URL sem status, expiração, soft delete e política robusta de cache | Média | Pendente |
| R7 | Contadores de acesso não implementados | Média | Pendente |
| R8 | Observabilidade inicial/incompleta | Média | Pendente |
| R9 | Configuração AWS/DynamoDB inadequada para produção | Média | Pendente |
| R10 | Redis/email acoplados a transações relacionais | Média | Pendente |

---

## 2. Prioridade 1 — Corrigir antes de novas features

### TASK-R1 — Corrigir geração de ID sob concorrência

**Status:** Concluído
**Severidade:** Crítica
**Área:** URL
**Fluxo afetado:** `POST /api/v1/urls`
**Arquivos principais:**

- `IdGeneratorAdapter`
- `CounterIdAdapter`
- `ShortenUrlUseCaseImpl`

#### Problema

O `IdGeneratorAdapter` usa `AtomicLong` + `volatile baseId` de forma insegura. Uma thread pode capturar um `offset`, outra thread pode trocar o `baseId`, e a primeira pode calcular um ID usando o bloco errado.

#### Objetivo

Garantir que a geração de IDs seja segura sob concorrência, sem duplicidades e sem uso de `baseId` de outro bloco.

#### Subtasks

- [x] Revisar a implementação atual do `IdGeneratorAdapter`.
- [x] Decidir estratégia de correção:
  - [x] sincronizar a geração completa;
  - [x] encapsular `baseId` e `offset` em estado atômico único;
  - [x] usar Redis/Lua;
  - [x] usar DynamoDB atomic counter;
  - [x] usar outro gerador comprovado, como Snowflake/ULID, se fizer sentido.
- [x] Implementar a correção escolhida.
- [x] Criar/ajustar testes unitários de stress com `blockSize` pequeno.
- [x] Validar ausência de IDs duplicados.
- [x] Validar ausência de IDs calculados com bloco incorreto.
- [x] Rodar testes específicos do módulo URL.
- [x] Rodar suíte completa.

#### Critérios de aceite

- [x] IDs gerados sob concorrência são únicos.
- [x] A troca de bloco não gera IDs incorretos.
- [x] Teste concorrente falharia com a implementação antiga.
- [x] Código compila.
- [x] Suíte de testes passa.

---

### TASK-R1A — Migrar contador global de Redis para DynamoDB atomic counter

**Status:** Pendente
**Severidade:** Alta
**Área:** URL / Infraestrutura
**Fluxo afetado:** `POST /api/v1/urls`
**Arquivos principais:**

- `CounterIdAdapter`
- `DynamoDbCounterIdAdapter`
- `CounterIdRepository`
- configurações DynamoDB
- testes com LocalStack

#### Problema

O Redis é performático, mas não é a fonte mais adequada para um contador global que não pode voltar para trás durante anos de operação.

Como o sistema deve suportar 100 milhões de URLs geradas por dia, manter URLs por no mínimo 10 anos e operar em alta disponibilidade, o contador global precisa ser durável, atômico e seguro contra perda/restauração incorreta.

#### Objetivo

Substituir Redis como fonte global de alocação de blocos por DynamoDB atomic counter, mantendo a geração local por blocos.

#### Decisão técnica

Usar DynamoDB como única fonte de verdade do contador global.

A aplicação deve alocar blocos usando uma operação atômica no DynamoDB e consumir esses blocos localmente pelo `IdGeneratorAdapter`.

Redis não deve ser usado como fallback para geração de IDs.

#### Estratégia

- Usar `blockSize = 10_000`, configurável por ambiente.
- Alocar blocos exclusivos via DynamoDB atomic counter.
- Consumir IDs localmente no `IdGeneratorAdapter`.
- Se ainda houver bloco local, continuar gerando IDs mesmo que DynamoDB esteja temporariamente indisponível.
- Se o bloco local acabar e DynamoDB estiver indisponível, falhar de forma controlada no `POST /api/v1/urls`.
- Manter Redis apenas para cache, rate limit e dados temporários.

#### Subtasks

- [ ] Criar estrutura `url_counters` no DynamoDB/LocalStack.
- [ ] Criar item inicial do contador `url_short_code`.
- [ ] Implementar `DynamoDbCounterIdAdapter`.
- [ ] Usar operação atômica de incremento no DynamoDB.
- [ ] Retornar corretamente o `baseId` do bloco alocado.
- [ ] Tornar `blockSize` configurável.
- [ ] Remover Redis como fonte de verdade do contador global.
- [ ] Garantir falha controlada quando DynamoDB estiver indisponível e não houver bloco local.
- [ ] Garantir que a aplicação continue gerando IDs se ainda houver bloco local disponível.
- [ ] Criar testes de integração com LocalStack para alocação de blocos.
- [ ] Validar ausência de sobreposição entre blocos.
- [ ] Rodar testes específicos do módulo URL.
- [ ] Rodar suíte completa.

#### Critérios de aceite

- [ ] DynamoDB é a única fonte de verdade do contador global.
- [ ] Redis não é usado para gerar IDs ou alocar blocos.
- [ ] Cada chamada ao contador aloca uma faixa exclusiva de IDs.
- [ ] Com `blockSize = 10_000`, os blocos são calculados corretamente.
- [ ] Não há sobreposição entre blocos.
- [ ] Se DynamoDB estiver indisponível e houver bloco local, a geração continua.
- [ ] Se DynamoDB estiver indisponível e o bloco local acabar, o `POST /api/v1/urls` retorna falha controlada.
- [ ] Código compila.
- [ ] Testes passam.

---

### TASK-R2 — Corrigir refresh token rotation com operação atômica

**Status:** Concluído
**Severidade:** Alta
**Área:** IAM/Auth
**Fluxo afetado:** `POST /api/v1/auth/refresh`
**Arquivos principais:**

- `RefreshTokenUseCaseImpl`
- `RefreshTokenJpaRepository`
- `RefreshTokenRepositoryAdapter`
- `RefreshTokenEntity`
- `RefreshTokenRepositoryAdapterTest`

#### Problema

O fluxo antigo fazia `findByTokenHash -> oldToken.rotate -> save(newToken) -> save(oldToken)`, permitindo que duas requisições concorrentes com o mesmo refresh token emitissem dois novos refresh tokens.

#### Correção aplicada

- [x] Removido uso de `oldToken.rotate()` no fluxo persistente de refresh.
- [x] Criado método condicional `markTokenAsRotatedIfActive(...)`.
- [x] O método retorna `int` com a quantidade de linhas atualizadas.
- [x] O use case só continua quando `updatedRows == 1`.
- [x] Quando `updatedRows != 1`, o fluxo trata como token reuse/comprometimento.
- [x] Revogação por comprometimento delegada para `RefreshTokenSecurityService`.
- [x] `buildAuthenticatedUser` passou a usar `findByIdWithRolesAndPermissions(...)`.
- [x] Ajustados testes unitários existentes.
- [x] Criados testes JPA slice para validar a query condicional.

#### Testes existentes

- [x] Testes unitários do `RefreshTokenUseCaseImpl` atualizados.
- [x] Slice JPA validando token ativo rotacionado.
- [x] Slice JPA validando token já revogado não rotacionado.
- [x] Slice JPA validando token expirado não rotacionado.
- [x] Suíte completa passou após ajustes.

#### Tarefa futura opcional

- [ ] Criar teste concorrente end-to-end/integration com duas chamadas simultâneas usando o mesmo refresh token.

---

### TASK-R3 — Remover vazamento de dados sensíveis em logs e `toString`

**Status:** Pendente
**Severidade:** Alta
**Área:** Segurança
**Fluxos afetados:** verificação de email, refresh token, logs de domínio
**Arquivos principais:**

- `ConsoleEmailSenderStrategy`
- `UserAccount`
- `RefreshToken`
- demais models com token, hash, senha, OTP ou secrets

#### Problema

Campos sensíveis podem aparecer em logs ou `toString`, como:

- OTP/código de verificação;
- `passwordHash`;
- `tokenHash`;
- access token;
- refresh token;
- secrets.

A `ConsoleEmailSenderStrategy` loga o código de verificação intencionalmente para DEV, mas precisa estar protegida por profile.

#### Subtasks

- [ ] Revisar todos os models de domínio com `@ToString`.
- [ ] Remover `@ToString` automático de models sensíveis.
- [ ] Implementar `toString()` manual em `UserAccount`, sem `passwordHash`.
- [ ] Implementar `toString()` manual em `RefreshToken`, sem `tokenHash`.
- [ ] Verificar se existe model/value object de email verification com OTP/código em `toString`.
- [ ] Garantir que `ConsoleEmailSenderStrategy` esteja restrita a `dev`, `local` ou `test`.
- [ ] Garantir que nenhuma implementação de produção logue OTP.
- [ ] Criar testes unitários garantindo que `toString()` não expõe campos sensíveis.
- [ ] Rodar testes de segurança/unitários relevantes.

#### Critérios de aceite

- [ ] `passwordHash` não aparece em `UserAccount.toString()`.
- [ ] `tokenHash` não aparece em `RefreshToken.toString()`.
- [ ] OTP/código de verificação não é logado fora de ambiente DEV/local/test.
- [ ] Estratégia de console está protegida por profile.
- [ ] Testes passam.

---

### TASK-R4 — Implementar rate limit distribuído

**Status:** Pendente
**Severidade:** Alta
**Área:** Segurança
**Fluxos afetados:** auth, redirect, criação de URL
**Arquivos prováveis:**

- módulo `shared.exception.ratelimit`
- filtros/interceptors de segurança
- configuração Redis
- controllers/use cases de auth e URL

#### Problema

Não há implementação concreta de rate limit para fluxos públicos e sensíveis. Isso deixa a aplicação vulnerável a brute force, abuso de register, spam de OTP e alto volume de redirects.

#### Políticas iniciais sugeridas

| Fluxo | Chave sugerida | Limite inicial |
|---|---|---:|
| Login | `ip + email` | 5 tentativas / 15 min |
| Register | `ip` | 5 tentativas / hora |
| Verify email | `ip + email` | 5 tentativas / 15 min |
| Resend verification | `ip + email` | 3 tentativas / 15 min |
| Refresh | `ip` | 20 tentativas / 15 min |
| Redirect | `ip` | 300 req / min |
| Shorten anonymous | `ip` | 5 / dia |
| Shorten FREE | `userId` | 10 / hora |
| Shorten PREMIUM | `userId` | limite maior a definir |

#### Subtasks

- [ ] Definir abordagem: Bucket4j + Redis, Redis `INCR/EXPIRE`, ou Lua script.
- [ ] Criar propriedades configuráveis para limites.
- [ ] Criar serviço/porta de rate limit.
- [ ] Implementar adapter Redis distribuído.
- [ ] Aplicar rate limit em endpoints de auth.
- [ ] Aplicar rate limit em criação de URL.
- [ ] Avaliar rate limit em redirect.
- [ ] Retornar `429 Too Many Requests` com `ProblemDetail`.
- [ ] Criar testes Redis slice.
- [ ] Criar testes web/security slice.
- [ ] Atualizar documentação.

#### Critérios de aceite

- [ ] Limite funciona em Redis, não em memória local.
- [ ] Requisições abaixo do limite passam.
- [ ] Requisições acima do limite retornam `429`.
- [ ] Chaves são separadas por fluxo/usuário/IP/email quando aplicável.
- [ ] Testes passam.

---

### TASK-R11 — Criar testes concorrentes prioritários

**Status:** Pendente
**Severidade:** Alta
**Área:** Testes/Confiabilidade

#### Subtasks

- [ ] Criar teste concorrente para `IdGeneratorAdapter`.
- [ ] Criar teste concorrente futuro para refresh token rotation em nível integration/use case.
- [ ] Criar teste concorrente Redis para verificação de email após ajuste de consumo atômico.
- [ ] Definir padrão de uso de `ExecutorService`, `CountDownLatch` ou `CyclicBarrier`.
- [ ] Documentar padrão em `docs/testing` se fizer sentido.

#### Critérios de aceite

- [ ] Testes são determinísticos o suficiente para CI.
- [ ] Não usam `Thread.sleep` como sincronização principal.
- [ ] Falhariam com a implementação antiga.

---

## 3. Prioridade 2 — Corrigir antes de publicar como portfólio

### TASK-R5 — Proteger scheduler contra múltiplas instâncias

**Status:** Pendente
**Severidade:** Média/Alta
**Área:** Distribuído
**Arquivo principal:** `RefreshTokenCleanupTask`

#### Problema

O scheduler de limpeza executa em todas as instâncias da aplicação. Hoje a operação é relativamente idempotente, mas em ambiente distribuído isso pode causar execução duplicada, ruído operacional e carga desnecessária.

#### Subtasks

- [ ] Revisar implementação atual do scheduler.
- [ ] Decidir estratégia:
  - [ ] ShedLock com PostgreSQL;
  - [ ] lock distribuído em Redis;
  - [ ] job externo;
  - [ ] execução somente em uma instância/profile.
- [ ] Implementar lock distribuído ou estratégia escolhida.
- [ ] Criar testes unitários/slice quando aplicável.
- [ ] Documentar comportamento em múltiplas instâncias.

#### Critérios de aceite

- [ ] Apenas uma instância executa a limpeza por janela.
- [ ] Falha de uma instância não deixa lock preso indefinidamente.
- [ ] Configuração é segura para produção.

---

### TASK-R6 — Adicionar status, expiração e soft delete em URL

**Status:** Pendente
**Severidade:** Média
**Área:** URL
**Arquivos principais:**

- `Url`
- `UrlEntity`
- `ResolveUrlUseCaseImpl`
- `UrlRepositoryAdapter`
- `DeleteUrlUseCaseImpl`

#### Problema

O redirect redireciona qualquer item existente no DynamoDB/cache. Ainda não há status, expiração, soft delete ou validação robusta no resolve.

#### Subtasks

- [ ] Adicionar `status` ao modelo de URL.
- [ ] Adicionar `expiresAt`, se o produto exigir expiração.
- [ ] Adicionar `deletedAt` e, futuramente, `deletedBy`.
- [ ] Ajustar persistência no DynamoDB.
- [ ] Ajustar criação de URL.
- [ ] Ajustar resolve para bloquear URL expirada/inativa/deletada.
- [ ] Ajustar delete para soft delete.
- [ ] Invalidar cache no soft delete.
- [ ] Definir respostas: `404`, `410 Gone` ou outro padrão.
- [ ] Criar testes unitários e slice/LocalStack quando aplicável.

#### Critérios de aceite

- [ ] URL inativa não redireciona.
- [ ] URL expirada não redireciona.
- [ ] URL deletada não redireciona.
- [ ] Cache não serve URL invalidada.
- [ ] Testes passam.

---

### TASK-R7 — Implementar contadores atômicos de acesso

**Status:** Pendente
**Severidade:** Média
**Área:** URL/Analytics

#### Problema

Não há contagem de acessos. Atualizar contador de forma síncrona no DynamoDB durante redirect pode prejudicar latência.

#### Subtasks

- [ ] Definir métrica mínima: total de redirects por short code.
- [ ] Implementar incremento atômico em Redis com `INCR`/`HINCRBY`.
- [ ] Definir chave Redis, exemplo: `url:access-count:{shortCode}`.
- [ ] Definir TTL ou retenção.
- [ ] Definir estratégia de flush assíncrono/agregado para DynamoDB.
- [ ] Garantir que falha no contador não derrube redirect.
- [ ] Criar testes Redis slice.
- [ ] Adicionar métrica customizada de falha/sucesso no contador.

#### Critérios de aceite

- [ ] Redirect incrementa contador sem impactar significativamente a latência.
- [ ] Falha no Redis não quebra redirect, conforme contrato definido.
- [ ] Testes passam.

---

### TASK-R8 — Melhorar idempotência

**Status:** Pendente
**Severidade:** Média
**Área:** Consistência/UX
**Arquivos principais:**

- `IdempotencyFilter`
- `RedisIdempotencyStore`

#### Problema

A chave de idempotência não está vinculada ao usuário, rota, método ou hash do body. Reutilizar a mesma key em outra operação pode retornar resposta errada.

#### Subtasks

- [ ] Definir chave composta: `user/method/path/idempotencyKey`.
- [ ] Armazenar hash do body/payload.
- [ ] Retornar conflito quando mesma key for usada com payload diferente.
- [ ] Avaliar headers que devem ser cacheados/restaurados.
- [ ] Criar testes Redis slice.
- [ ] Criar testes web para replay com mesmo payload.
- [ ] Criar testes web para replay com payload diferente.

#### Critérios de aceite

- [ ] Mesmo payload + mesma key retorna resposta cacheada.
- [ ] Payload diferente + mesma key retorna conflito.
- [ ] Chaves são isoladas por usuário/rota/método.

---

### TASK-R9 — Melhorar configuração AWS/DynamoDB para produção

**Status:** Pendente
**Severidade:** Média
**Área:** Configuração/Infra
**Arquivos principais:**

- `application-dev.yml`
- configurações AWS/DynamoDB
- Docker Compose/LocalStack

#### Problema

A configuração atual usa endpoint e credenciais estáticas adequadas para LocalStack, mas inadequadas para produção.

#### Subtasks

- [ ] Separar claramente profile `dev/local` de `prod`.
- [ ] Externalizar secrets por env vars.
- [ ] Impedir uso acidental de credenciais LocalStack em produção.
- [ ] Configurar região via env var.
- [ ] Configurar endpoint override somente em profile local/dev.
- [ ] Documentar variáveis obrigatórias.
- [ ] Adicionar validação de configuração em startup, se necessário.

#### Critérios de aceite

- [ ] Produção não depende de secrets hardcoded.
- [ ] LocalStack continua funcionando em dev.
- [ ] Configuração fica documentada.

---

### TASK-R10 — Revisar transações em register/verify/resend e eventos Redis/email

**Status:** Pendente
**Severidade:** Média
**Área:** Transações/IAM

#### Problemas encontrados

- `ResendVerificationUseCaseImpl` usa `@Transactional(readOnly = true)`, mas escreve no Redis/publica evento.
- Em register, o código de verificação pode ser salvo no Redis antes do commit relacional.
- Quando entrega de email for crítica, será necessário avaliar Outbox Pattern.

#### Subtasks

- [ ] Revisar `RegisterUserUseCaseImpl`.
- [ ] Revisar `VerifyEmailUseCaseImpl`.
- [ ] Revisar `ResendVerificationUseCaseImpl`.
- [ ] Corrigir semântica de `@Transactional(readOnly = true)` quando houver escrita Redis/evento.
- [ ] Garantir publicação de eventos após commit quando necessário.
- [ ] Avaliar se armazenamento de OTP deve ocorrer after-commit.
- [ ] Documentar decisão atual.
- [ ] Criar tarefa futura para Outbox Pattern.

#### Critérios de aceite

- [ ] Anotações transacionais refletem o comportamento real.
- [ ] Não há evento/email enviado antes de persistência crítica ser confirmada, quando isso for requisito.
- [ ] Testes existentes continuam passando.

---

### TASK-R12 — Melhorar validação de URLs maliciosas

**Status:** Pendente
**Severidade:** Média
**Área:** Segurança/URL
**Arquivo principal:** `HttpUrlValidator`

#### Problema

O validador aceita `localhost`, IPs privados, metadata IP e domínios internos. Para um shortener público, isso pode mascarar URLs maliciosas ou facilitar abuso.

#### Subtasks

- [ ] Definir política do produto: permitir ou bloquear URLs privadas?
- [ ] Bloquear `localhost`.
- [ ] Bloquear IPs privados, loopback, link-local e metadata IP.
- [ ] Bloquear esquemas não HTTP/HTTPS.
- [ ] Avaliar punycode/homograph attacks.
- [ ] Criar testes unitários para URLs maliciosas.

#### Critérios de aceite

- [ ] URLs internas proibidas são rejeitadas.
- [ ] URLs HTTP/HTTPS públicas válidas continuam aceitas.
- [ ] Testes passam.

---

## 4. Prioridade 3 — Melhorias evolutivas

### TASK-R13 — Implementar observabilidade mínima

**Status:** Pendente
**Severidade:** Média
**Área:** Observabilidade

#### Subtasks

- [ ] Configurar Actuator endpoints.
- [ ] Expor health, readiness e liveness.
- [ ] Adicionar Micrometer Prometheus registry.
- [ ] Configurar Prometheus local.
- [ ] Configurar Grafana local.
- [ ] Adicionar request/correlation ID.
- [ ] Criar métricas customizadas:
  - [ ] `url.redirect.latency`
  - [ ] `url.redirect.cache.hit`
  - [ ] `url.redirect.cache.miss`
  - [ ] `url.create.latency`
  - [ ] `url.create.collision`
  - [ ] `auth.refresh.reuse.detected`
  - [ ] `idempotency.hit`
  - [ ] `idempotency.miss`
  - [ ] falhas Redis/DynamoDB/PostgreSQL
- [ ] Documentar como visualizar métricas localmente.

#### Critérios de aceite

- [ ] `/actuator/health` funciona.
- [ ] Métricas Prometheus são expostas.
- [ ] Dashboard local básico funciona.
- [ ] Métricas principais aparecem durante uso da aplicação.

---

### TASK-R14 — Criar scripts de carga com k6

**Status:** Pendente
**Severidade:** Média
**Área:** Performance/Testes de carga

#### Cenários prioritários

- [ ] Redirect cache hit.
- [ ] Redirect cache miss.
- [ ] Criação concorrente de URL.
- [ ] Refresh concorrente com mesmo token.
- [ ] Login brute force.
- [ ] Register/resend/verify.
- [ ] URL inexistente.
- [ ] Futuro: URL expirada/desativada.

#### Métricas mínimas

- [ ] p50.
- [ ] p95.
- [ ] p99.
- [ ] throughput.
- [ ] taxa de erro.
- [ ] cache hit ratio.
- [ ] latência Redis.
- [ ] latência DynamoDB.

#### Critérios de aceite

- [ ] Scripts ficam versionados em pasta própria, por exemplo `tests/k6` ou `load-tests/k6`.
- [ ] Scripts possuem thresholds básicos.
- [ ] README explica como executar localmente.

---

### TASK-R15 — Avaliar Outbox Pattern para eventos de email/auditoria

**Status:** Pendente
**Severidade:** Média
**Área:** Consistência/Eventos

#### Subtasks

- [ ] Identificar eventos críticos.
- [ ] Definir tabela de outbox.
- [ ] Definir publisher assíncrono.
- [ ] Definir retries e DLQ futura.
- [ ] Aplicar inicialmente em email verification, se fizer sentido.
- [ ] Documentar trade-offs.

#### Critérios de aceite

- [ ] Eventos críticos não são perdidos após commit.
- [ ] Falhas de envio podem ser reprocessadas.

---

### TASK-R16 — Melhorar modelo DynamoDB para listagem e ownership

**Status:** Pendente
**Severidade:** Média
**Área:** DynamoDB/URL

#### Subtasks

- [ ] Avaliar GSI `userId + createdAt`.
- [ ] Definir ordenação por data na listagem.
- [ ] Adicionar conditional delete/update com `userId = :requester`.
- [ ] Avaliar TTL nativo para URLs descartáveis.
- [ ] Adicionar métricas de throttling/latência.
- [ ] Configurar retry/backoff customizado no AWS SDK.

#### Critérios de aceite

- [ ] Listagem por usuário é ordenável e paginável de forma previsível.
- [ ] Delete/update não sofrem janela de race entre leitura e operação.
- [ ] Métricas de DynamoDB estão disponíveis.

---

### TASK-R17 — Criar estratégia de tracing distribuído

**Status:** Pendente
**Severidade:** Baixa/Média
**Área:** Observabilidade

#### Subtasks

- [ ] Definir stack OpenTelemetry.
- [ ] Configurar OTel agent ou SDK.
- [ ] Enviar traces para Tempo ou backend equivalente.
- [ ] Propagar correlation/request ID.
- [ ] Instrumentar chamadas Redis/DynamoDB/Postgres.
- [ ] Criar dashboard básico.

#### Critérios de aceite

- [ ] Requests possuem trace ID.
- [ ] Fluxo de redirect pode ser rastreado.
- [ ] Fluxo de auth pode ser rastreado.

---

## 5. Testes pendentes por prioridade

| Prioridade | Tipo | Fluxo | Status |
|---|---|---|---:|
| P1 | Unit stress | ID generator | Pendente |
| P1 | JPA slice | Refresh token rotation | Concluído |
| P1 | Integration futuro | Refresh token concorrente end-to-end | Pendente |
| P1 | Redis slice | Verify email consumo atômico | Pendente |
| P1 | Security/unit | `toString` sem dados sensíveis | Pendente |
| P1 | Web/security slice | Rate limit em auth | Pendente |
| P2 | Redis slice | Idempotência com body hash | Pendente |
| P2 | LocalStack slice/integration | Redirect cache miss | Pendente |
| P2 | Redis slice | Cache TTL/eviction | Pendente |
| P2 | JPA slice | `findByIdWithRolesAndPermissions` | Pendente |
| P2 | k6 | Redirect hit/miss | Pendente |
| P3 | Chaos/integration | Redis/DynamoDB indisponíveis | Pendente |

---

## 6. Ordem recomendada de execução

1. **TASK-R1 — Corrigir `IdGeneratorAdapter`**.
2. **TASK-R3 — Remover vazamentos em `toString`/logs**.
3. **TASK-R4 — Implementar rate limit mínimo**.
4. **TASK-R3/R4 — Ajustar testes de segurança correspondentes**.
5. **TASK-R6 — Adicionar status/expiração/soft delete em URL**.
6. **TASK-R8 — Melhorar idempotência**.
7. **TASK-R13 — Observabilidade mínima**.
8. **TASK-R14 — Scripts k6 básicos**.
9. **TASK-R5 — Lock distribuído para scheduler**.
10. **TASK-R10/R15 — Revisar transações e avaliar Outbox Pattern**.
11. **TASK-R16/R17 — Evoluções DynamoDB e tracing**.

---

## 7. Plano de aprendizado associado

### Concorrência e consistência

- [ ] Race conditions.
- [ ] Interleavings.
- [ ] Atomicidade composta.
- [ ] CAS.
- [ ] Locks.
- [ ] Testes concorrentes.

### JPA e transações

- [ ] `@Transactional`.
- [ ] Rollback.
- [ ] `REQUIRES_NEW`.
- [ ] `@Version`.
- [ ] Pessimistic locking.
- [ ] `EntityGraph`.
- [ ] N+1.
- [ ] OSIV off.

### Redis

- [ ] `SET NX EX`.
- [ ] `INCRBY`.
- [ ] `GETDEL`.
- [ ] Lua scripts.
- [ ] TTL.
- [ ] Rate limiting distribuído.

### DynamoDB

- [ ] `ConditionExpression`.
- [ ] Conditional update/delete.
- [ ] GSI.
- [ ] TTL nativo.
- [ ] Retry/backoff.
- [ ] Throttling.

### Observabilidade e carga

- [ ] RED metrics.
- [ ] USE metrics.
- [ ] Micrometer.
- [ ] Prometheus.
- [ ] Grafana.
- [ ] OpenTelemetry.
- [ ] k6.
- [ ] p95/p99.

---

## 8. Histórico de conclusão

### R2 — Refresh token rotation

**Status:** Concluído.

#### Evidências

- Fluxo antigo removido.
- Update condicional implementado.
- Use case só emite novo token quando `updatedRows == 1`.
- Revogação por comprometimento implementada.
- `findByIdWithRolesAndPermissions` usado para montar access token.
- Testes unitários ajustados.
- Slice JPA criado para validar query real.
- Suíte completa passou após ajustes.

#### Observação

Teste concorrente end-to-end fica como melhoria futura em testes de integração/confiabilidade.
