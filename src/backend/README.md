# RhHub: back end

Back end Java (Spring Boot 3, WebFlux) do RhHub, portado do [AgentRH](https://github.com/BrayanDevZN/AgentRH).
Todo acesso a dados é assíncrono (`Mono`/`Flux`).

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
| `infra` | **Pronta**: `config`, `database` (tabelas e repositórios), `redis`, `llm` e `email`. Falta só a conexão do Kafka. |
| `domain` | Parcial: só os modelos (`User`, `Vacancy`, `Resume`...) e `DomainException`. Faltam as regras e o JWT. |
| `adapter` | As **interfaces** estão hoje em `application/port/out` e as **implementações** do banco em `infra/database/adapter`. Falta reorganizar para o desenho acima. |
| `service` | Não existe ainda. |
| `application` | Não existe ainda (não há rotas). |

Ficou de fora de propósito, para entrar depois: Kafka, o JWT de sessão e BCrypt (o JWT do e-mail já existe em `infra/email`), leitura de PDF/DOCX e os templates HTML de e-mail.

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
│   ├── application/port/out/ interfaces: UserRepository, VacancyRepository, ResumeRepository, AiProvider, EmailSender, EmailMessage
│   └── infra/
│       ├── config/           AppProperties (variáveis de ambiente), ClockConfig
│       ├── database/
│       │   ├── DatabaseUrl, DatabaseConfig, SqlExecutor, Params, Row
│       │   ├── PostgresSqlExecutor, SqliteSqlExecutor
│       │   ├── table/        Dialect, TableDefinition, UsersTable, VacanciesTable, ResumesTable, TableCreator
│       │   └── adapter/      UserRepositoryAdapter, VacancyRepositoryAdapter, ResumeRepositoryAdapter
│       ├── email/            N8nEmailSender, JwtHs256, EmailConfig
│       ├── redis/            RedisConfig (conexão), RedisStore (operações)
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

`CREATE TABLE IF NOT EXISTS` só cria o que falta: **não altera tabelas já criadas**. Mudar uma coluna pede um comando novo (por exemplo `ALTER TABLE`) numa classe de tabela.

### Repositórios

Cada repositório devolve `Mono`/`Flux`. Erros de negócio saem como `DomainException`:

- e-mail, CPF, `google_sub`, nome de vaga ou candidatura repetidos: `Conflict`;
- candidatura de usuário ou vaga inexistente, ou resultado para candidatura inexistente: `NotFound`.

As consultas de metadados do currículo não selecionam a coluna `file`; o PDF só é lido por `findFile`.

## LLM

`AiProvider.complete(system, user)` devolve `Mono<String>`. O provedor é escolhido pelas variáveis de ambiente:

1. `ENVIRONMENT=test`: resposta fixa (`FixedAiProvider`), sem rede.
2. `OPENAI_API_KEY` preenchida: OpenAI.
3. `GROQ_API_KEY` preenchida: Groq.
4. Nenhuma chave: sobe normalmente, mas cada chamada falha com `AiException`.

Os dois provedores usam a API `/chat/completions`. Qualquer falha de rede ou resposta vazia vira `AiException`.

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
| `GROQ_API_KEY`, `GROQ_MODEL`, `GROQ_BASE_URL` | modelo `llama-3.3-70b-versatile` | Groq |
| `gmail_secret` | vazio | Segredo da credencial JWT do webhook de e-mail do n8n |
| `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` | `localhost`, `6379`, vazio | Redis (sem senha: só host e porta) |
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

- Os **mesmos 9 cenários** de repositório rodam contra o SQLite e contra o PostgreSQL, para os dois se comportarem igual.
- O teste do PostgreSQL usa o banco `rhhub_test` em `localhost:5432` (usuário e senha `rhhub`) e é **pulado** se ele não estiver de pé.
- Há também testes de `DatabaseUrl`, de `TableCreator` (criação do zero, idempotência e constraints) da escolha do provedor de LLM, da conexão e das operações do Redis (contra um Redis local, inclusive um com senha) e do envio de e-mail (contrato do webhook, assinatura do JWT, validação e erros, contra um servidor HTTP local; nenhum e-mail real é enviado nos testes).
