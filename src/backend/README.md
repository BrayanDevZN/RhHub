# RhHub: back end

Back end Java (Spring Boot 3, WebFlux) do RhHub, portado do [AgentRH](https://github.com/BrayanDevZN/AgentRH).
Todo acesso a dados é assíncrono (`Mono`/`Flux`).

O que já foi feito, as decisões, os problemas encontrados e o que falta: veja o [HISTORICO.md](HISTORICO.md).

## Camadas

| Camada | Responsabilidade |
| --- | --- |
| `domain` | As **regras de negócio**: modelos, validações, erros e o token JWT. Não conhece banco, rede nem Spring. |
| `adapter` | As **abstrações**: as interfaces que o resto do sistema usa para falar com o que é externo (repositórios, LLM etc.). |
| `service` | **Junta as camadas**: orquestra domínio e adapters para cumprir cada caso de uso. |
| `application` | **Usa as camadas e entrega as rotas**: controllers, schemas de entrada e saída, cookies e filtros. |
| `infra` | Código que **usa os dados externos**: conexão com o banco, chamada à LLM e leitura das variáveis de ambiente. Implementa os adapters. |

Fluxo de uma requisição: `application` (rota) → `service` → `domain` + `adapter` (interface) → `infra` (implementação).

## Estado atual

| Camada | Situação |
| --- | --- |
| `infra` | **Pronta**: `config`, `database` (tabelas e repositórios), `redis` (códigos, refresh token, rate limit), `messaging` (Kafka), `llm`, `email` (n8n e templates), `google` (OAuth) e `security` (BCrypt, JWT). |
| `domain` | **Pronta**: modelos, regras de validação (`Rules`), JWT de sessão, política de acesso (`AccessPolicy`) e erros. |
| `service` | **Pronta**: autenticação, conta do usuário, foto de perfil, vagas, candidaturas, administração e a análise com a IA. |
| `application` | **Pronta**: rotas sob `/api` (controllers), filtros de login, de limite e de segurança, CORS e erros padronizados. |

As interfaces dos adapters ainda ficam em `application/port/out` (e as de entrada em `application/port/in`).

### Rotas

Todas em `/api`, JSON em snake_case, sessão por cookie `HttpOnly`. A lista completa e quem acessa cada uma está na seção 4.5 da [`DOCUMENTACAO.md`](../../DOCUMENTACAO.md). Além delas: `GET /api/health`, `PUT/GET/DELETE /api/users/me/photo` (foto de perfil: JPEG, PNG ou WebP, até 2 MB) e, com `GOOGLE_OAUTH_ENABLED=true`, `/oauth2/authorization/google`.

### Deploy

| Serviço | Dockerfile | Config do Railway |
| --- | --- | --- |
| API | `Dockerfile.Api` | `railway.api.json` (ou `RAILWAY_DOCKERFILE_PATH=Dockerfile.Api`) |
| Consumers (análise e e-mail) | `Dockerfile.Consumer` | `railway.consumer.json` (ou `RAILWAY_DOCKERFILE_PATH=Dockerfile.Consumer`) |
| Kafka | `Dockerfile.kafka` | `railway.kafka.json` (ou `RAILWAY_DOCKERFILE_PATH=Dockerfile.kafka`) |
| Front (nginx, repassa `/api` para a API; lê `BACKEND_URL`) | `Dockerfile` (o padrão da raiz) | nenhum: é o que o Railway usa sem configuração |

**Kafka e acesso de fora do projeto.** O broker escuta na porta **9092**. Entre serviços do mesmo projeto use a rede privada: `KAFKA_URL=<serviço do Kafka>.railway.internal:9092` no back end e `KAFKA_ADVERTISED_HOST=<serviço do Kafka>.railway.internal` no Kafka. De outro projeto (a rede privada não atravessa projetos) use o TCP Proxy público do Railway, com o **destino na porta 9092**, e informe o endereço público ao Kafka: `KAFKA_ADVERTISED_HOST=<host do proxy>` e `KAFKA_ADVERTISED_PORT=<porta do proxy>`; o back end usa `KAFKA_URL=<host do proxy>:<porta do proxy>`. Um proxy com destino errado (por exemplo 9001) fecha a conexão durante a autenticação.

**Kafka precisa de volume.** Sem um **Volume** do Railway montado em `/var/lib/kafka/data` (os dados ficam na subpasta `kafka-logs`, porque a raiz do volume tem `lost+found` e o Kafka não sobe com ela), todo reinício do Kafka (inclusive os deploys automáticos a cada push) apaga os tópicos e as mensagens. O consumer recria os tópicos sozinho a cada minuto (`TopicEnsurer`) e reenfileira as candidaturas pendentes (`PendingResumeRepublisher`), mas o que estava na fila no momento do reinício se perde. Em **Settings → Build → Watch Paths** de cada serviço, limite o deploy automático ao que é dele (por exemplo, no Kafka: `/Dockerfile.kafka` e `/src/backend/kafka/**`), para um push no front não reiniciar o Kafka.

**Cada serviço do Railway precisa apontar para o seu Dockerfile** (config path ou a variável acima). Sem isso ele constrói o `Dockerfile` da raiz, que é o do front: o sintoma é a URL da API mostrar a tela de login do site.

`docker compose up --build` sobe tudo local (veja `.env.compose.example` na raiz). Front e API precisam estar no **mesmo domínio**: os cookies são `SameSite=Strict`.

## Estrutura de pastas

Sem `src/main`: o `pom.xml` aponta para `src/`, `resources/` e `test/`.

```
src/backend/
├── pom.xml
├── .env.example
├── src/br/rhhub/
│   ├── RhHubApplication.java
│   ├── domain/
│   │   ├── model/            User, Vacancy, Resume, ResumeFile, enums
│   │   └── exception/        DomainException (Invalid, Unauthorized, Forbidden, NotFound, Conflict...)
│   ├── service/              DeliverEmailService
│   ├── application/port/in/  AnalyzeResume, DeliverEmail (casos de uso que os consumers chamam)
│   ├── application/port/out/ interfaces: UserRepository, VacancyRepository, ResumeRepository, AiProvider, EmailSender, EmailMessage, EmailPublisher, ResumeAnalysisPublisher, DeadLetterRepository
│   └── infra/
│       ├── config/           AppProperties (variáveis de ambiente), ClockConfig
│       ├── database/
│       │   ├── DatabaseUrl, DatabaseConfig, SqlExecutor, Params, Row
│       │   ├── PostgresSqlExecutor, SqliteSqlExecutor
│       │   ├── table/        Dialect, TableDefinition, UsersTable, VacanciesTable, ResumesTable, TableCreator
│       │   └── adapter/      UserRepositoryAdapter, VacancyRepositoryAdapter, ResumeRepositoryAdapter
│       ├── email/            N8nEmailSender, JwtHs256, EmailConfig
│       ├── redis/            RedisConfig (conexão), RedisStore (operações)
│       ├── messaging/        KafkaConfig, KafkaUrl, KafkaTopics, publishers, AnalysisConsumer, EmailConsumer, DeadLetterRecoverer
│       └── llm/              OpenAiCompatibleProvider, FixedAiProvider, LlmConfig
├── resources/application.yml
├── test/
└── kafka/entrypoint.sh       usado pelo Dockerfile.kafka (na raiz do repositório)
```

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
| `vacancies` | `VacanciesTable` | `name` único. `created_by` vira nulo se o admin for excluído. |
| `resumes` | `ResumesTable` | Uma candidatura por usuário e vaga. Apagar a vaga ou o usuário apaga as candidaturas. O arquivo fica em `file` (`BYTEA`/`BLOB`). |
| `dead_letters` | `DeadLettersTable` | **DLQ**: o job que esgotou as tentativas (ver abaixo). |

`CREATE TABLE IF NOT EXISTS` só cria o que falta: **não altera tabelas já criadas**. Mudar uma coluna pede um comando novo (por exemplo `ALTER TABLE`) numa classe de tabela.

### Repositórios

Cada repositório devolve `Mono`/`Flux`. Erros de negócio saem como `DomainException`:

- e-mail, CPF, `google_sub`, nome de vaga ou candidatura repetidos: `Conflict`;
- candidatura de usuário ou vaga inexistente, ou resultado para candidatura inexistente: `NotFound`.

### DLQ (`dead_letters`)

Guarda o resultado do job que falhou de vez: `topic`, `message_key` (o id da candidatura), `kafka_partition` e `kafka_offset` (de onde veio), `payload` (a mensagem original), `error_type`, `error_message`, `attempts`, `status` (`failed` ou `resolved`), `created_at` e `resolved_at`.

- **Sem chave estrangeira** de propósito: a candidatura pode ser apagada e o registro da falha continua.
- **Nunca duplica:** `(topic, kafka_partition, kafka_offset)` é único. Salvar a mesma mensagem morta de novo devolve o registro que já estava gravado.
- `DeadLetterRepository`: `save`, `findById`, `findByKey(topic, key)` (histórico de uma candidatura), `findAll(status)` e `markResolved(id)`.
- `DeadLetter.failed(...)` monta o registro a partir da exceção do job.

As consultas de metadados do currículo não selecionam a coluna `file`; o PDF só é lido por `findFile`.

## LLM

`AiProvider.complete(system, user)` devolve `Mono<String>`. O provedor é escolhido pelas variáveis de ambiente:

1. `ENVIRONMENT=test`: resposta fixa (`FixedAiProvider`), sem rede.
2. `OPENAI_API_KEY` preenchida: OpenAI.
3. `GROQ_API_KEY` preenchida: Groq.
4. Nenhuma chave: sobe normalmente, mas cada chamada falha com `AiException`.

Os dois provedores usam a API `/chat/completions`. Qualquer falha de rede ou resposta vazia vira `AiException`, e a mensagem traz o provedor, o status HTTP e o começo do corpo do erro.

**Limite de taxa (429).** O plano gratuito do Groq limita tokens por minuto e cada análise manda o prompt inteiro. Ao receber 429, o cliente espera o tempo do cabeçalho `Retry-After` (padrão 20 s, no máximo 60 s) e tenta de novo, até 2 vezes. Esgotadas as esperas, o erro sobe e o job é repetido pelo consumer. O cliente usa os proxies das propriedades do sistema (`https.proxyHost` etc.), quando existirem.

O modelo padrão é `openai/gpt-oss-120b` (o do teste real com o Groq). Para usar outro, defina `GROQ_MODEL`.

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

`RedisStore` reúne as operações, todas devolvendo `Mono`: `set` (com ou sem expiração), `setIfAbsent`, `get`, `exists`, `delete`, `increment`, `incrementWithTtl` (soma 1 e define a expiração só na primeira vez, de forma atômica), `expire`, `ttl` e `ping`. Os adapters que vêm depois (códigos de verificação, refresh token, rate limit) só montam as chaves e usam esses métodos.

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

## Análise da candidatura

`AnalyzeResumeService` (camada `service`) faz a análise quando o consumer recebe o id de uma candidatura:

1. Busca a candidatura. Se não existe mais, ou **não está `pending`**, ignora (idempotente).
2. Busca a vaga, o candidato e o arquivo, e **extrai o texto** do PDF (PDFBox) ou do DOCX (POI), reconhecendo o tipo pelo conteúdo. Limite de 20 000 caracteres.
3. Chama a IA com o prompt de triagem (`resources/prompt/resume_analysis.md`, o mesmo do AgentRH) e esta entrada: `JOB_DESCRIPTION` (título e descrição da vaga), `CANDIDATE_NAME` e `CANDIDATE_RESUME`.
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

O `N8nEmailSender` faz `POST` no webhook com um **JWT HS256** novo a cada chamada (`iss=rhhub`, validade de 1 minuto, assinado com `gmail_secret` como texto, igual à credencial "JWT Auth" do n8n, Key Type Passphrase) e este corpo:

```json
{ "type": "html", "to": "pessoa@gmail.com", "subject": "Assunto", "content": "<p>corpo</p>" }
```

`type` é `html` ou `text`. O `EmailMessage` tem os atalhos `EmailMessage.html(...)` e `EmailMessage.text(...)`.

- **Validação antes da chamada.** O n8n responde 200 assim que recebe, *antes* de validar e enviar, então um corpo inválido não gera erro para quem chamou. Por isso o Java valida as mesmas regras do workflow (`to` com e-mail válido, `subject` e `content` não vazios, `type` informado) e falha com `DomainException.Invalid` sem chamar o n8n.
- **Falha no envio.** Resposta diferente de 2xx (por exemplo 403 de JWT errado), timeout ou n8n fora do ar viram `EmailException`.
- **Sem confirmação de entrega.** 200 do n8n quer dizer só "recebi". Se o Gmail falhar depois, o backend não fica sabendo; isso aparece nas execuções do workflow no n8n.
- **Escolha pelo ambiente:** `ENVIRONMENT=test` não envia nada (só registra no log); com `gmail_secret` usa o n8n; sem o segredo a aplicação sobe, mas cada envio falha com `EmailException`.

## Variáveis de ambiente

Veja o `.env.example`.

| Variável | Padrão | Uso |
| --- | --- | --- |
| `ENVIRONMENT` | `production` | `test` troca a IA por uma resposta fixa |
| `DATABASE_URL` | `none` | PostgreSQL ou SQLite (ver acima) |
| `SQLITE_PATH` | `./data/rhhub.db` | Arquivo do SQLite |
| `DATABASE_POOL_SIZE` | `10` | Conexões do pool do PostgreSQL |
| `OPENAI_API_KEY`, `OPENAI_MODEL`, `OPENAI_BASE_URL` | modelo `gpt-4o-mini` | OpenAI |
| `GROQ_API_KEY`, `GROQ_MODEL`, `GROQ_BASE_URL` | modelo `openai/gpt-oss-120b` | Groq |
| `gmail_secret` | vazio | Segredo da credencial JWT do webhook de e-mail do n8n |
| `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` | `localhost`, `6379`, vazio | Redis (sem senha: só host e porta) |
| `KAFKA_URL`, `KAFKA_API_USER`, `KAFKA_API_KEY` | `none` (local), `rhhub`, vazio | Kafka (com a chave, SASL/PLAIN) |
| `GROQ_API_KEY` ou `groq_key`, `GROQ_MODEL` | vazio, `openai/gpt-oss-120b` | Groq (também `OPENAI_API_KEY` ou `open_ai_key`) |
| `N8N_EMAIL_WEBHOOK_URL` | URL de produção do workflow `gmail` | Webhook de envio de e-mail |
| `PORT` | `8080` | Porta HTTP |

## Como rodar

```bash
cd src/backend
./mvnw spring-boot:run          # SQLite local, sem configurar nada
DATABASE_URL=postgresql://rhhub:rhhub@localhost:5432/rhhub ./mvnw spring-boot:run
```

## Testes

```bash
./mvnw test
```

- Os **mesmos 13 cenários** de repositório rodam contra o SQLite e contra o PostgreSQL, para os dois se comportarem igual.
- O teste do PostgreSQL usa o banco `rhhub_test` em `localhost:5432` (usuário e senha `rhhub`) e é **pulado** se ele não estiver de pé.
- **Fluxo completo** (`AnalysisFlowIntegrationTest`): banco SQLite, PDF e DOCX reais, Kafka embutido, consumers, serviço de análise, o cliente HTTP da LLM e DLQ. A LLM desse teste é um **servidor local** que fala `/chat/completions`: prova o nosso código ponta a ponta, mas **não é um modelo de verdade**. O e-mail também é um gravador.
- **Groq de verdade** (`GroqLiveAnalysisTest`): analisa um perfil forte e um sem relação com a vaga e confere aprovado/recusado, assunto e HTML. Lê a chave de `groq_key` ou `GROQ_API_KEY` e **é pulado** se não houver chave ou se `api.groq.com` não estiver acessível (o motivo aparece como `GROQ-LIVE PULADO` na saída). Para rodar: `groq_key=gsk_... ./mvnw test -Dtest=GroqLiveAnalysisTest`.
- Há também testes de `DatabaseUrl`, de `TableCreator` (criação do zero, idempotência e constraints) da escolha do provedor de LLM, da conexão e das operações do Redis (contra um Redis local, inclusive um com senha) do Kafka (broker embutido: tópicos, publicação, consumers, novas tentativas, DLT e DLQ) e do envio de e-mail (contrato do webhook, assinatura do JWT, validação e erros, contra um servidor HTTP local; nenhum e-mail real é enviado nos testes).

## Testes

Quatro categorias, por tag do JUnit 5 (pacote `br.rhhub.testing`), sempre com `ENVIRONMENT=test`:

| Categoria | Comando | O que é |
| --- | --- | --- |
| `@UnitTest` | `mvn test -Pcategory-unit` | Memória: domínio, serviços com `Fakes`, validações |
| `@IntegrationTest` | `mvn test -Pcategory-integration` | Uma peça real: SQLite, PostgreSQL, Redis, Kafka embarcado, HTTP local |
| `@FunctionalTest` | `mvn test -Pcategory-functional` | API HTTP inteira e o fluxo completo de análise |
| `@LiveTest` | `mvn test -Pcategory-live` | Groq e n8n reais (gasta cota). Fica fora de `mvn test` e do CI |

`mvn test` sem perfil roda as três primeiras. Ao criar um teste, anote a classe com a categoria. Detalhes na seção 7.1 da [`DOCUMENTACAO.md`](../../DOCUMENTACAO.md).
