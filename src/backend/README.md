# RhHub: back end

Back end Java (Spring Boot 3.5, WebFlux) do RhHub, portado do [AgentRH](https://github.com/BrayanDevZN/AgentRH).
Todo acesso a dados é assíncrono (`Mono`/`Flux`).

Este README explica **como o back end é organizado e como configurá-lo**. Para a visão do sistema inteiro veja a [`docs/ARQUITETURA.md`](../../docs/ARQUITETURA.md); para a lista de rotas, regras de validação e fluxos de autenticação, a [`DOCUMENTACAO.md`](../../DOCUMENTACAO.md); para um roteiro de leitura do código, o [`GUIA_DO_BACKEND.md`](GUIA_DO_BACKEND.md).

## Índice

[Camadas](#camadas) · [Um jar, dois papéis](#um-jar-dois-papéis) · [Estrutura de pastas](#estrutura-de-pastas) · [Rotas](#rotas) · [Banco de dados](#banco-de-dados) · [Cache](#cache-redis) · [LLM](#llm) · [Redis](#redis) · [Kafka](#kafka) · [Análise da candidatura](#análise-da-candidatura) · [E-mail](#e-mail) · [Variáveis de ambiente](#variáveis-de-ambiente) · [Como rodar](#como-rodar) · [Testes](#testes) · [Deploy](#deploy)

## Camadas

É uma arquitetura hexagonal: o miolo não conhece tecnologia, só interfaces (as *portas*). Banco, Redis, Kafka, IA e e-mail são *adapters* plugados por fora.

| Camada | Responsabilidade |
| --- | --- |
| `domain` | As **regras de negócio**: modelos, validações (`Rules`), etapas (`Stages`), JWT, política de acesso e erros. Não conhece banco, rede nem Spring. |
| `service` | **Junta as camadas**: orquestra o domínio e as portas para cumprir cada caso de uso (autenticação, usuários, vagas, candidaturas, etapas, notificações, análise com a IA). |
| `application` | **Só onde a aplicação e as rotas começam**: controllers, middleware (login, limite de requisições, cabeçalhos de segurança), CORS, handler de erros, DTOs e utilitários. |
| `infra` | **Fala com o que é externo** e implementa as portas: banco, cache, Redis, Kafka, LLM, e-mail, Google e segurança. As **interfaces das portas** também ficam aqui, em `infra/repository`, `infra/integration` e `infra/store`. |

Fluxo de uma requisição: `application` (rota) → `service` → `domain` + portas (interfaces) → `infra` (implementação).

Isso permite testar os serviços com repositórios em memória e trocar uma implementação (a IA, o banco) sem mexer nas regras.

**Pastas e pacotes Java não coincidem em todos os lugares.** O `pom.xml` aponta o código para `src/`, e alguns pacotes guardam o nome de uma organização antiga. Ao procurar uma classe, use a pasta; ao importar, use o pacote:

| Pasta | Pacote Java |
| --- | --- |
| `application/controllers`, `middleware`, `cors`, `handlers`, `utils` | `br.rhhub.application.web` |
| `application/dto` | `br.rhhub.application.web.dto` |
| `infra/repository`, `infra/integration`, `infra/store` (as portas) | `br.rhhub.application.port.out` |
| `service/usecase` | `br.rhhub.application.port.in` |
| as demais (`domain/*`, `service`, `infra/*`) | `br.rhhub.<pasta com pontos>` (ex.: `br.rhhub.infra.cache`) |

## Um jar, dois papéis

O mesmo jar roda a **API** e o **consumer**. O que muda é a variável `KAFKA_CONSUMERS_ENABLED`:

| Papel | `KAFKA_CONSUMERS_ENABLED` | O que faz |
| --- | --- | --- |
| API | `false` | Atende o HTTP e só **publica** nas filas |
| Consumer | `true` (padrão) | **Lê** as filas de análise e de e-mail, cria os tópicos que faltam (`TopicEnsurer`) e reenfileira candidaturas pendentes (`PendingResumeRepublisher`) |

Em desenvolvimento, com o padrão `true`, um único processo faz as duas coisas.

## Estrutura de pastas

Sem `src/main`: o `pom.xml` aponta para `src/` (com os arquivos de configuração em `src/infra/resources/`) e `../../tests/` (os testes ficam na raiz do repositório).

```
src/backend/
├── pom.xml
├── .env.example
├── GUIA_DO_BACKEND.md        roteiro de leitura do código
├── kafka/entrypoint.sh       usado pelo Dockerfile.kafka (na raiz do repositório)
└── src/
    ├── RhHubApplication.java
    ├── domain/
    │   ├── model/            User, Vacancy, Resume, ResumeFile, Notification, ProfilePhoto, DeadLetter, AnalysisResult, enums
    │   ├── rule/             Rules (validações), Stages (etapas da vaga)
    │   ├── auth/             JwtService, TokenClaims, TokenType, AccessPolicy, PasswordHasher, CodeGenerator
    │   └── exception/        DomainException (Invalid, Unauthorized, Forbidden, NotFound, Conflict...)
    ├── service/              AuthService, UserService, VacancyService, ResumeService, ResumeStageService, AdminService,
    │                         NotificationService, ProfilePhotoService, AnalyzeResumeService, DeliverEmailService, AsyncPasswordHasher
    │   └── usecase/          AnalyzeResume, DeliverEmail (o que os consumers chamam)
    ├── application/
    │   ├── controllers/      Auth, Google, User, Vacancy, Resume, Notification, Admin e Health
    │   ├── middleware/       AuthFilter, RateLimitFilter, SecurityHeadersFilter
    │   ├── cors/  handlers/  dto/  utils/
    └── infra/
        ├── repository/       portas: UserRepository, VacancyRepository, ResumeRepository, NotificationRepository, fotos, DeadLetterRepository
        ├── integration/      portas: AiProvider, EmailSender, EmailPublisher, EmailComposer, ResumeAnalysisPublisher, DocumentTextExtractor, GoogleIdentityProvider, Notifier
        ├── store/            portas: RateLimiter, RefreshTokenStore, VerificationCodeStore
        ├── config/           AppProperties e demais propriedades (variáveis de ambiente), beans, admin raiz na subida
        ├── database/         DatabaseUrl, DatabaseConfig, SqlExecutor (PostgreSQL e SQLite), table/ (criação das tabelas), adapter/ (SQL)
        ├── cache/            QueryCache, CacheKeys, CacheStore e os Cached*Repository (cache-aside)
        ├── redis/            RedisConfig, RedisStore, RedisRateLimiter, RedisRefreshTokenStore, RedisVerificationCodeStore
        ├── messaging/        Kafka: config, publishers, consumers, TopicEnsurer, DeadLetterRecoverer, PendingResumeRepublisher
        ├── llm/              OpenAiCompatibleProvider, FixedAiProvider, LlmConfig
        ├── email/            N8nEmailSender, N8nJwtSigner, HtmlEmailComposer, EmailConfig
        ├── google/           GoogleOAuthClient, GoogleConfig
        ├── document/         PdfDocxTextExtractor (PDFBox e POI)
        ├── security/         BcryptPasswordHasher, AuthConfig (JWT)
        └── resources/        application.yml, prompt/ (prompt da IA) e templates/ (e-mail)
```

## Rotas

Todas em `/api`, JSON em snake_case, sessão por cookie `HttpOnly`. A lista completa e quem acessa cada uma está na seção 4.5 da [`DOCUMENTACAO.md`](../../DOCUMENTACAO.md). Além delas: `GET /api/health`, `PUT/GET/DELETE /api/users/me/photo` (foto de perfil: JPEG, PNG ou WebP, até 2 MB) e, com `GOOGLE_OAUTH_ENABLED=true`, `/oauth2/authorization/google`.

## Banco de dados

A variável `DATABASE_URL` escolhe o banco:

| `DATABASE_URL` | Banco |
| --- | --- |
| `postgresql://usuario:senha@host:5432/banco` | PostgreSQL por **R2DBC**, com pool (`DATABASE_POOL_SIZE`, padrão 10). Também aceita `postgres://` e `postgresql+asyncpg://`. Usuário e senha com caracteres especiais vão codificados na URL (`@` vira `%40`). `?sslmode=require` liga o SSL. |
| `none`, vazia ou ausente | **SQLite local** em `SQLITE_PATH` (padrão `./data/rhhub.db`). A pasta é criada sozinha. |

Qualquer outro banco (`mysql://...`) derruba a subida com uma mensagem clara.

**Assíncrono nos dois.** O PostgreSQL usa driver R2DBC. Não existe driver R2DBC oficial para SQLite, então ele roda sobre JDBC numa única thread dedicada, como o `aiosqlite` do Python: quem chama recebe `Mono`/`Flux` e não bloqueia, e as escritas ficam serializadas.

**Mesmo SQL nos dois.** Os repositórios usam parâmetros nomeados (`:nome`) e `RETURNING`, que os dois bancos aceitam. O `SqlExecutor` converte `UUID`, `Instant` e `Boolean` para o que o SQLite guarda (`TEXT`, `TEXT`, `INTEGER`).

### Tabelas

Criadas por classes Java na subida (`TableCreator`), uma por tabela, na ordem das chaves estrangeiras. `Dialect` traz os tipos de coluna de cada banco.

| Tabela | Classe | Observações |
| --- | --- | --- |
| `users` | `UsersTable` | `email`, `cpf`, `public_id` e `google_sub` únicos. Conta Google não tem senha, CPF, idade nem gênero. |
| `user_photos` | `UserPhotosTable` | Foto de perfil, separada para as consultas de usuário não carregarem a imagem. |
| `vacancies` | `VacanciesTable` | `name` único. `created_by` vira nulo se o admin for excluído. Guarda as etapas (`stages`) e a expiração opcional (`expires_at`). |
| `vacancy_photos` | `VacancyPhotosTable` | Foto de capa da vaga. |
| `resumes` | `ResumesTable` | Uma candidatura por usuário e vaga. Apagar a vaga ou o usuário apaga as candidaturas. O arquivo fica em `file` (`BYTEA`/`BLOB`). Guarda a etapa atual (`stage`). |
| `notifications` | `NotificationsTable` | Avisos do sino de cada usuário. |
| `dead_letters` | `DeadLettersTable` | **DLQ**: o job que esgotou as tentativas (ver abaixo). |

**Evolução do esquema.** `CREATE TABLE IF NOT EXISTS` só cria o que falta e não altera uma tabela que já existe. Para colunas novas, cada classe de tabela lista em `newColumns` as colunas que bancos antigos podem não ter, e o `TableCreator` confere cada uma na subida e faz `ALTER TABLE ... ADD COLUMN` só se faltar. **Só acrescentar coluna é automático**: renomear, remover ou mudar o tipo de uma coluna pede um comando novo escrito à mão.

### Repositórios

Cada repositório devolve `Mono`/`Flux`. Erros de negócio saem como `DomainException`:

- e-mail, CPF, `google_sub`, nome de vaga ou candidatura repetidos: `Conflict`;
- candidatura de usuário ou vaga inexistente, ou resultado para candidatura inexistente: `NotFound`.

As consultas de metadados do currículo não selecionam a coluna `file`; o PDF só é lido por `findFile`.

### DLQ (`dead_letters`)

Guarda o resultado do job que falhou de vez: `topic`, `message_key` (o id da candidatura), `kafka_partition` e `kafka_offset` (de onde veio), `payload` (a mensagem original), `error_type`, `error_message`, `attempts`, `status` (`failed` ou `resolved`), `created_at` e `resolved_at`.

- **Sem chave estrangeira** de propósito: a candidatura pode ser apagada e o registro da falha continua.
- **Nunca duplica:** `(topic, kafka_partition, kafka_offset)` é único. Salvar a mesma mensagem morta de novo devolve o registro que já estava gravado.
- `DeadLetterRepository`: `save`, `findById`, `findByKey(topic, key)` (histórico de uma candidatura), `findAll(status)` e `markResolved(id)`.
- `DeadLetter.failed(...)` monta o registro a partir da exceção do job.

## Cache (Redis)

As leituras mais repetidas passam por um cache no Redis, no padrão **cache-aside** (código em `infra/cache`): lê do cache; se não achar, lê do banco e grava com prazo (TTL); quem altera ou apaga um dado **invalida** a entrada.

**Como a escolha "Redis ou banco" acontece:**

1. **Na subida.** Cada `Cached*Repository` é um *decorator* `@Primary` do repositório SQL correspondente e só existe com `cache.enabled=true` (padrão). O service recebe o decorator sem saber que ele existe. Com `CACHE_ENABLED=false` os decorators não são criados e o service usa o adapter SQL direto.
2. **Por método.** Cada método do decorator decide o que passa pelo cache (`cache.one` e `cache.list`) e o que vai direto ao banco.
3. **A cada leitura.** O `QueryCache` tenta o Redis; se ele não devolver nada (chave ausente, cache pausado, erro ou mais de 300 ms), chama o banco e grava o resultado. Não há um `if (redis)`: o "Redis respondeu?" é o `Mono` vir vazio ou não (`switchIfEmpty`).

| O que fica em cache | Chave | TTL | Quando é invalidado |
| --- | --- | --- | --- |
| Identidade de quem está logado (lida em **toda** requisição autenticada) | `cache:user:identity:{publicId}` | 5 min | Salvar ou apagar o usuário |
| Lista de vagas e cada vaga por id | `cache:vacancy:all`, `cache:vacancy:id:{id}` | 5 min | Salvar ou apagar vaga, criar ou apagar candidatura (muda o contador de candidatos) e apagar usuário |
| Avisos do sino: lista e contador de não lidos | `cache:notif:{userId}:*` | 60 s | Criar aviso, marcar como lido, marcar todos, apagar usuário |

- **O que não entra no cache, de propósito:** candidaturas (o status muda a todo instante e o arquivo é grande), fotos, leituras com credenciais (login, 2FA, troca de senha) e verificações de existência.
- **Sem credenciais no Redis.** A identidade guardada vem sem o hash da senha e sem o id do Google (`User.withoutCredentials()`).
- **O cache nunca derruba a aplicação.** Falha do Redis vira "não achei" e a leitura segue pelo banco. Depois de uma falha, o cache fica pausado por 30 s para não pagar o tempo limite a cada requisição; nesse período a leitura vai ao banco e nada é gravado, mas apagar entradas continua sendo tentado.
- **Valores vazios:** um valor único que não existe não é guardado; uma lista vazia é.
- **TTL curto como rede de segurança:** se uma invalidação falhar, o dado velho some sozinho em, no máximo, 5 min.

## LLM

`AiProvider.complete(system, user)` devolve `Mono<String>`. O provedor é escolhido pelas variáveis de ambiente:

1. `ENVIRONMENT=test`: resposta fixa (`FixedAiProvider`), sem rede.
2. `OPENAI_API_KEY` preenchida: OpenAI.
3. `GROQ_API_KEY` preenchida: Groq.
4. Nenhuma chave: sobe normalmente, mas cada chamada falha com `AiException`.

Os dois provedores usam a API `/chat/completions`. Qualquer falha de rede ou resposta vazia vira `AiException`, e a mensagem traz o provedor, o status HTTP e o começo do corpo do erro.

**Limite de taxa (429).** O plano gratuito do Groq limita tokens por minuto e cada análise manda o prompt inteiro. Ao receber 429, o cliente espera o tempo do cabeçalho `Retry-After` (padrão 20 s, no máximo 60 s) e tenta de novo, até 2 vezes. Esgotadas as esperas, o erro sobe e o job é repetido pelo consumer. O cliente usa os proxies das propriedades do sistema (`https.proxyHost` etc.), quando existirem.

O modelo padrão do Groq é `openai/gpt-oss-120b`; o da OpenAI, `gpt-4o-mini`. Para usar outro, defina `GROQ_MODEL` ou `OPENAI_MODEL`.

## Redis

Conexão assíncrona (Lettuce, API reativa) configurada em `infra/redis/RedisConfig`, lendo `REDIS_HOST`, `REDIS_PORT` e `REDIS_PASSWORD` (maiúsculas ou minúsculas):

| Situação | Comportamento |
| --- | --- |
| Nenhuma variável | Redis local padrão: `localhost:6379`, sem senha |
| Só `REDIS_HOST` e/ou `REDIS_PORT` | Conecta só com host e porta |
| `REDIS_PASSWORD` vazia, `none` ou `null` | Sem senha: conecta só com host e porta |
| `REDIS_PASSWORD` preenchida | Conecta com a senha |
| `REDIS_PORT` fora de 1 a 65535 | A subida falha com mensagem clara |

Na subida o log diz se o Redis respondeu (`Redis conectado em host:porta (com senha|sem senha)`). Se ele estiver fora do ar, a aplicação sobe do mesmo jeito e só registra um aviso.

`RedisStore` reúne as operações, todas devolvendo `Mono`: `set` (com ou sem expiração), `setIfAbsent`, `get`, `exists`, `delete`, `increment`, `incrementWithTtl` (soma 1 e define a expiração só na primeira vez, de forma atômica), `expire`, `ttl` e `ping`. Sobre elas ficam os usos de negócio: **códigos de verificação**, **refresh tokens** e o **limite de requisições**. O cache de leituras (seção acima) usa a interface `CacheStore`, implementada por `RedisCacheStore`.

## Kafka

Configurado em `infra/messaging/KafkaConfig`.

| Variável | Comportamento |
| --- | --- |
| `KAFKA_URL` | `none`, vazia ou ausente: Kafka local, `localhost:9092`. Aceita `host`, `host:porta`, `kafka://host:porta` e lista separada por vírgula. `KAFKA_BOOTSTRAP_SERVERS` também é aceita. |
| `KAFKA_API_KEY` | Preenchida: conecta por **SASL_PLAINTEXT/PLAIN** com `KAFKA_API_USER` (padrão `rhhub`), igual ao `Dockerfile.kafka`. Vazia ou `none`: sem autenticação. Só aceita letras, números, `_` e `-`. |

**Tópicos** (criados na subida, o broker roda com criação automática desligada): `resume.analysis` e `email.send`, cada um com **3 partições**, mais `resume.analysis.DLT` e `email.send.DLT`, também com 3 (a mensagem morta vai para a mesma partição). Se o Kafka não estiver no ar, a aplicação sobe do mesmo jeito (em cerca de 10 s) e registra erros de conexão.

**Chave da mensagem:** o id da candidatura nos dois tópicos. A análise e o e-mail de uma candidatura caem na mesma partição, e a ordem de uma candidatura é mantida. Os e-mails de cadastro, 2FA e senha **não** passam pelo Kafka: saem direto pelo `EmailSender`.

```
resume.analysis  {"resumeId": 42}
email.send       {"resumeId": 42, "to": "...", "subject": "...", "content": "<html>", "type": "html"}
```

**Criar os tópicos num Kafka real.** A aplicação os cria sozinha ao subir. Para criar e conferir sem subir a aplicação (sem consumers), use o `RemoteKafkaSetupTest`, que lê `KAFKA_URL`, `KAFKA_API_KEY` e `KAFKA_API_USER` do ambiente (a URL nunca fica no código), cria o que falta, mostra o endereço que o broker anuncia e confere as 3 partições. Não publica nem consome mensagens, e é pulado sem a flag:

```bash
KAFKA_URL=host:porta ./mvnw test -Dtest=RemoteKafkaSetupTest -Drhhub.kafka.setup=true
```

**Publishers** (`ResumeAnalysisPublisher`, `EmailPublisher`) devolvem `Mono<Void>` e só terminam quando o broker confirma (`acks=all`, idempotente).

**Consumers** (grupos `rhhub-analysis` e `rhhub-email`, 3 threads cada, uma por partição):
- `AnalysisConsumer` chama o caso de uso `AnalyzeResume` (ver *Análise da candidatura*).
- `EmailConsumer` chama `DeliverEmail`, que usa o `EmailSender` (n8n).

**Falhas.** O consumer repete com espera crescente (2 s, 4 s, 8 s) e, esgotadas as tentativas:

| Tópico | Tentativas |
| --- | --- |
| `resume.analysis` | 4 (1 + 3 repetições) |
| `email.send` | 3 (1 + 2: poucas, porque o n8n responde 200 antes de enviar e uma repetição pode duplicar o e-mail) |
| Mensagem malformada ou e-mail inválido | 1 (repetir não adianta) |

Depois disso o `DeadLetterRecoverer` **grava o job na tabela `dead_letters`** (com tópico, chave, partição, offset, mensagem original, erro e tentativas) e **publica a mensagem no `.DLT`**. Se a gravação ou a publicação falhar, o Kafka entrega a mensagem de novo (gravar duas vezes não duplica), então o job não se perde.

**Candidatura que ficou sem job.** Se o Kafka estiver fora na hora do envio, a candidatura já está gravada como `pending`. O `PendingResumeRepublisher` (só no consumer) reenfileira, a cada minuto, as pendentes há mais de 3 minutos, no máximo uma vez a cada 10. É seguro porque a análise ignora candidatura que já saiu de `pending`.

## Análise da candidatura

`AnalyzeResumeService` (camada `service`) faz a análise quando o consumer recebe o id de uma candidatura:

1. Busca a candidatura. Se não existe mais, ou **não está `pending`**, ignora (idempotente).
2. Busca a vaga, o candidato e o arquivo, e **extrai o texto** do PDF (PDFBox) ou do DOCX (POI), reconhecendo o tipo pelo conteúdo. Limite de 20 000 caracteres.
3. Chama a IA com o prompt de triagem (`src/infra/resources/prompt/resume_analysis.md`, o mesmo do AgentRH) e esta entrada: `JOB_DESCRIPTION` (título e descrição da vaga), `CANDIDATE_NAME` e `CANDIDATE_RESUME`.
4. Lê a resposta `DECISÃO | ASSUNTO | HTML` (`AnalysisResult.parse`): a decisão tem de ser exatamente `aproved` ou `recuse`, o assunto não pode estar vazio nem ter `|`, e o corpo tem de ser um documento HTML. Tolera espaços, maiúsculas e cercas de código (```). O HTML pode conter ` | `.
5. **Grava `status` e `reason`** (o assunto e o corpo em texto, sem tags, para o front mostrar) e **enfileira o e-mail** para o candidato.

Casos de borda:

| Situação | O que acontece |
| --- | --- |
| Arquivo corrompido, de tipo desconhecido ou sem texto | O job **não falha**: a IA recebe um aviso no lugar do currículo e recusa, como o prompt manda. O candidato recebe a resposta. |
| Resposta da IA fora do formato | `Unprocessable`: a candidatura continua `pending`, o job é repetido (4 tentativas) e depois vai para a DLQ. Nenhum e-mail sai. |
| IA fora do ar, limite de taxa ou timeout | `AiException`: mesma repetição e DLQ. |
| Não foi possível enfileirar o e-mail | O resultado volta para `pending` e o erro sobe, para a repetição analisar de novo em vez de dar a candidatura por concluída sem e-mail. |
| A mesma candidatura publicada duas vezes | Analisada e avisada uma vez só. |

## E-mail

O envio é feito pelo workflow **`gmail`** do n8n, que manda o e-mail pela conta Gmail conectada lá. `EmailSender.send(EmailMessage)` devolve `Mono<Void>`.

O `N8nEmailSender` faz `POST` no webhook com um **JWT HS256** novo a cada chamada (`iss=rhhub`, validade de 1 minuto, assinado por `N8nJwtSigner` com `gmail_secret` como texto, igual à credencial "JWT Auth" do n8n, Key Type Passphrase) e este corpo:

```json
{ "type": "html", "to": "pessoa@gmail.com", "subject": "Assunto", "content": "<p>corpo</p>" }
```

`type` é `html` ou `text`. O `EmailMessage` tem os atalhos `EmailMessage.html(...)` e `EmailMessage.text(...)`. O HTML dos e-mails do sistema é montado por `HtmlEmailComposer`, a partir dos templates em `src/infra/resources/templates`.

- **Validação antes da chamada.** O n8n responde 200 assim que recebe, *antes* de validar e enviar, então um corpo inválido não gera erro para quem chamou. Por isso o Java valida as mesmas regras do workflow (`to` com e-mail válido, `subject` e `content` não vazios, `type` informado) e falha com `DomainException.Invalid` sem chamar o n8n.
- **Falha no envio.** Resposta diferente de 2xx (por exemplo 403 de JWT errado), timeout ou n8n fora do ar viram `EmailException`.
- **Sem confirmação de entrega.** 200 do n8n quer dizer só "recebi". Se o Gmail falhar depois, o backend não fica sabendo; isso aparece nas execuções do workflow no n8n.
- **Escolha pelo ambiente:** `ENVIRONMENT=test` não envia nada (só registra no log); com `gmail_secret` usa o n8n; sem o segredo a aplicação sobe, mas cada envio falha com `EmailException`.

## Variáveis de ambiente

Veja o [`.env.example`](.env.example) (back end) e o [`.env.compose.example`](../../.env.compose.example) (Docker Compose). Os padrões abaixo vêm do `application.yml`.

| Variável | Padrão | Uso |
| --- | --- | --- |
| `PORT` | `8080` | Porta HTTP |
| `ENVIRONMENT` | `production` | `test`: IA com resposta fixa, e-mail só no log, códigos de verificação devolvidos na resposta e cookies sem `Secure` |
| `DATABASE_URL` | `none` | PostgreSQL ou SQLite (ver *Banco de dados*) |
| `SQLITE_PATH` | `./data/rhhub.db` | Arquivo do SQLite |
| `DATABASE_POOL_SIZE` | `10` | Conexões do pool do PostgreSQL |
| `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` | `localhost`, `6379`, vazio | Redis (sem senha: só host e porta) |
| `CACHE_ENABLED` | `true` | `false` remove os decorators de cache (ver *Cache*) |
| `KAFKA_URL`, `KAFKA_API_USER`, `KAFKA_API_KEY` | `none` (local), `rhhub`, vazio | Kafka (com a chave, SASL/PLAIN) |
| `KAFKA_CONSUMERS_ENABLED` | `true` | `false` na API (só publica); `true` no consumer |
| `GROQ_API_KEY` (ou `groq_key`), `GROQ_MODEL`, `GROQ_BASE_URL` | vazio, `openai/gpt-oss-120b`, API do Groq | Groq |
| `OPENAI_API_KEY` (ou `open_ai_key`), `OPENAI_MODEL`, `OPENAI_BASE_URL` | vazio, `gpt-4o-mini`, API da OpenAI | OpenAI |
| `N8N_EMAIL_WEBHOOK_URL` | URL de produção do workflow `gmail` | Webhook de envio de e-mail |
| `gmail_secret` | vazio | Segredo da credencial JWT do webhook do n8n |
| `JWT_SECRET` | vazio | Segredo dos tokens de sessão. Vazio: um aleatório a cada subida (as sessões acabam ao reiniciar e duas réplicas não aceitam os tokens uma da outra) |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD` | vazio | Admin raiz, criado na subida (o e-mail precisa ser `@gmail.com`) |
| `RATE_LIMIT`, `GLOBAL_RATE_LIMIT` | `60`, `1000` | Requisições por minuto: por IP nas rotas públicas e por usuário nas demais; e o teto global. Rotas públicas que enviam e-mail ou conferem senha têm um teto menor (10) |
| `GOOGLE_OAUTH_ENABLED`, `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `GOOGLE_REDIRECT_URI` | `false`, vazios | Login com Google |
| `FRONTEND_URL` (ou `FRONTEND_ORIGIN`) | vazio | Origens liberadas no CORS (várias, separadas por vírgula) e destino do retorno do login com Google |
| `COOKIE_SECURE`, `COOKIE_SAMESITE` | vazio (ligado), `Strict` | Atributos dos cookies de sessão. Use `COOKIE_SECURE=false` só em http local |

## Como rodar

Java 21 e Maven (o `mvnw` já vem no repositório):

```bash
cd src/backend
./mvnw spring-boot:run          # SQLite local, sem configurar nada
DATABASE_URL=postgresql://rhhub:rhhub@localhost:5432/rhhub ./mvnw spring-boot:run
```

Sem Redis ou sem Kafka a aplicação sobe do mesmo jeito e só registra avisos de conexão, mas o que depende deles não funciona: o Redis guarda o refresh token, os códigos de verificação e o limite de requisições, e o Kafka carrega a análise do currículo e os e-mails de resultado. Para o sistema completo (PostgreSQL, Redis, Kafka, API, consumer e front), use o `docker compose up --build` na raiz do repositório.

## Testes

Os testes ficam em `tests/` na **raiz do repositório** (o `pom.xml` aponta para `../../tests`), separados em categorias por tag do JUnit 5 (anotações em `br.rhhub.testing`), sempre com `ENVIRONMENT=test`: nenhum depende de e-mail ou chave externa.

| Categoria | Comando | O que é |
| --- | --- | --- |
| `@UnitTest` | `./mvnw test -Pcategory-unit` | Memória: domínio, serviços com `Fakes`, validações, composição de e-mail |
| `@IntegrationTest` | `./mvnw test -Pcategory-integration` | Uma peça real de cada vez: SQLite, PostgreSQL, Redis, Kafka embutido, clientes HTTP (Google, n8n, LLM) contra um servidor local |
| `@FunctionalTest` | `./mvnw test -Pcategory-functional` | A API HTTP inteira e o fluxo completo candidatura → fila → análise → e-mail |
| `@LiveTest` | `./mvnw test -Pcategory-live` | Groq e n8n **reais** (gasta cota). Fica fora de `mvn test` e do CI |

`./mvnw test` sem perfil roda as três primeiras. Ao criar um teste, anote a classe com a categoria; sem anotação ele só roda na execução completa.

Pontos que valem saber:

- Os **mesmos cenários de repositório** rodam contra o SQLite e contra o PostgreSQL, para os dois se comportarem igual. O teste do PostgreSQL usa o banco `rhhub_test` em `localhost:5432` (usuário e senha `rhhub`) e é **pulado** se ele não estiver de pé. O mesmo vale para o Redis (inclusive um com senha).
- O **fluxo completo** (`AnalysisFlowIntegrationTest`) usa SQLite, PDF e DOCX reais, Kafka embutido, os consumers, o serviço de análise e o cliente HTTP da LLM. A LLM desse teste é um **servidor local** que fala `/chat/completions`: prova o nosso código ponta a ponta, mas **não é um modelo de verdade**. O e-mail também é um gravador.
- O **Groq de verdade** (`GroqLiveAnalysisTest`) analisa um perfil forte e um sem relação com a vaga. Lê a chave de `groq_key` ou `GROQ_API_KEY` e é pulado se não houver chave ou se `api.groq.com` não estiver acessível: `groq_key=gsk_... ./mvnw test -Dtest=GroqLiveAnalysisTest`.
- Os testes de e-mail (contrato do webhook, assinatura do JWT, validação e erros) rodam contra um servidor HTTP local: nenhum e-mail real é enviado.

## Deploy

| Serviço | Dockerfile |
| --- | --- |
| API | `Dockerfile.Api` |
| Consumers (análise e e-mail) | `Dockerfile.Consumer` |
| Kafka | `Dockerfile.kafka` |
| Front (nginx, repassa `/api` para a API; lê `BACKEND_URL`) | `Dockerfile.front` |

`docker compose up --build` sobe tudo local (veja `.env.compose.example` na raiz). Front e API precisam estar no **mesmo domínio**: os cookies são `SameSite=Strict`.

**Kafka e acesso de fora do projeto.** O broker escuta na porta **9092**. Entre serviços do mesmo projeto use a rede privada: `KAFKA_URL=<serviço do Kafka>.railway.internal:9092` no back end e `KAFKA_ADVERTISED_HOST=<serviço do Kafka>.railway.internal` no Kafka. De outro projeto (a rede privada não atravessa projetos) use o TCP Proxy público do Railway, com o **destino na porta 9092**, e informe o endereço público ao Kafka: `KAFKA_ADVERTISED_HOST=<host do proxy>` e `KAFKA_ADVERTISED_PORT=<porta do proxy>`; o back end usa `KAFKA_URL=<host do proxy>:<porta do proxy>`. Um proxy com destino errado (por exemplo 9001) fecha a conexão durante a autenticação.

**Kafka precisa de volume.** Sem um **Volume** do Railway montado em `/var/lib/kafka/data` (os dados ficam na subpasta `kafka-logs`, porque a raiz do volume tem `lost+found` e o Kafka não sobe com ela), todo reinício do Kafka (inclusive os deploys automáticos a cada push) apaga os tópicos e as mensagens. O consumer recria os tópicos sozinho a cada minuto (`TopicEnsurer`) e reenfileira as candidaturas pendentes (`PendingResumeRepublisher`), mas o que estava na fila no momento do reinício se perde. Em **Settings → Build → Watch Paths** de cada serviço, limite o deploy automático ao que é dele (por exemplo, no Kafka: `/Dockerfile.kafka` e `/src/backend/kafka/**`), para um push no front não reiniciar o Kafka.

**Cada serviço do Railway precisa apontar para o seu Dockerfile** (pela variável `RAILWAY_DOCKERFILE_PATH`, por exemplo `Dockerfile.Api`). Não existe um `Dockerfile` padrão na raiz: sem apontar, o Railway não sabe qual construir.
