# RhHub: histórico do back end

Registro do que foi feito no back end Java, na ordem, com as decisões, os problemas que apareceram e o que foi (e o que
não foi) verificado de verdade. Para saber **como o back end funciona hoje**, veja o [README](README.md). Para o plano
funcional completo (rotas, regras, telas), veja a [`DOCUMENTACAO.md`](../../DOCUMENTACAO.md) na raiz.

Tudo abaixo aconteceu em 03/11/2026, na branch `main`.

## 1. Ponto de partida

- O **AgentRH** (Python: FastAPI, SQLAlchemy, Redis, Celery, OpenAI) é o back end de referência.
- O **RhHub** tinha só o front end React (shadcn/ui, Tailwind, cena 3D) com uma API simulada, e um back end Java
  incompleto na `main` (camadas `domain`, `application/port` e uma `infrastructure` parcial).
- Pedido: apagar o back end Java, refazer do zero e começar pela camada de infraestrutura.

## 2. Decisões de arquitetura

| Decisão | Detalhe |
| --- | --- |
| **Camadas** | `domain` (regras de negócio e JWT), `adapter` (abstrações), `service` (junta as camadas), `application` (usa as camadas e entrega as rotas) e `infra` (código que usa os dados externos: banco, LLM, variáveis de ambiente, Redis, Kafka, e-mail). |
| **`infra/` é um pacote de código** | No começo, "infra" foi entendida como arquivos de deploy; a correção do usuário foi: `infra` guarda o código que fala com o que é externo. |
| **Sem `src/main`** | O `pom.xml` aponta para `src/`, `resources/` e `test/`. |
| **Tudo assíncrono** | Reactor (`Mono`/`Flux`) em todo acesso a dados. PostgreSQL por R2DBC; SQLite sobre JDBC numa thread dedicada (não existe driver R2DBC oficial); Redis por Lettuce reativo; LLM e n8n por `WebClient`. |
| **Banco pela `DATABASE_URL`** | `none`, vazia ou ausente usa SQLite local; senão PostgreSQL. |
| **Tabelas por classes Java** | `UsersTable`, `VacanciesTable`, `ResumesTable` e `DeadLettersTable`, no lugar do Flyway e dos `.sql`. `IF NOT EXISTS` na subida; não altera tabela já criada. |
| **E-mail pelo n8n** | Um workflow já existente do usuário (`gmail`) recebe um webhook com JWT HS256 e envia pelo Gmail. O segredo vem da variável `gmail_secret`. |
| **Kafka com dois tópicos** | `resume.analysis` e `email.send`, 3 partições cada, mais os `.DLT`. A análise, ao terminar, publica no tópico de e-mail. |
| **Chave da mensagem** | O id da candidatura nos dois tópicos. Os e-mails de cadastro, 2FA e senha **não** passam pelo Kafka: saem direto pelo `EmailSender`. |
| **DLQ em tabela** | O job que esgota as tentativas é gravado em `dead_letters`, além de ir para o `.DLT`. |
| **Variáveis lidas como o usuário nomeou** | `gmail_secret`, `groq_key` e `open_ai_key` (também em maiúsculas); `REDIS_*`, `KAFKA_*` e `DATABASE_URL`. |
| **Commits** | Sem atribuição ao Claude, com o autor `Brayan <lsouzadiasvaz@gmail.com>` (a pedido do usuário). |

## 3. Linha do tempo

| Commit | O que foi feito |
| --- | --- |
| `4bd521a` | Recriou o back end do zero: `domain`, portas e a primeira `infrastructure` (JPA, Redis, Kafka, JWT, e-mail, IA, PDF). |
| `60099b2` | Reorganizou em `infra/` (`database`, `llm`, `config`) e removeu `src/main`. Tirou e-mail, Redis, Kafka, JWT e PDF, a pedido, para entrarem depois. |
| `37983f1` | Corrigiu o build do front no Railway (import não usado quebrava o `tsc`) e criou o `railway.kafka.json`. |
| `c78dbed` | Banco assíncrono: PostgreSQL por R2DBC, SQLite em thread dedicada, `DATABASE_URL`, repositórios em SQL portável. |
| `66bbc5b` | Tabelas criadas por classes Java; saiu o Flyway. |
| `37d5cff` | Documentação das camadas, do banco e da estrutura (README do back end). |
| `0742a8d` | `EmailSender`, `N8nEmailSender` e `JwtHs256`: envio pelo webhook do n8n. |
| `183b5fe` | Segredo lido da variável `gmail_secret`. |
| `53af673` | Conexão assíncrona com o Redis (`REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`) e `RedisStore`. |
| `3a55ae1` | Tabela e repositório da DLQ (`dead_letters`). |
| `15eed94` | Kafka: `KAFKA_URL` e `KAFKA_API_KEY`, tópicos, publishers, consumers, retry e DLT. Nasceu a camada `service`. |
| `4dae985` | Análise de candidatura de verdade: `AnalyzeResumeService`, `AnalysisResult`, leitura de PDF e DOCX. |
| `6238f34` | Cliente da LLM espera o `Retry-After` no 429, usa o proxy do sistema e mostra o erro do provedor. |
| `d6f0726` | Teste de envio real de e-mail (producer → Kafka → consumer → n8n). |
| `6689ad1` | `RemoteKafkaSetupTest`: cria e confere os tópicos num Kafka real apontado por `KAFKA_URL`. |

Houve também uma **reescrita do histórico da `main`**: os 3 primeiros commits do back end tinham `Co-Authored-By` do
Claude e foram refeitos, com `git push --force-with-lease`. O sistema bloqueou o primeiro `force push`; ele só foi feito
depois de o usuário autorizar de forma explícita.

## 4. Problemas encontrados e como foram resolvidos

| Problema | Causa | Solução |
| --- | --- | --- |
| Deploy do front falhava no Railway | `SparklesIcon` importado e não usado: o `tsc -b` recusa (TS6133) | Import removido; build local conferido |
| Todos os serviços do Railway tentavam buildar o front | O `railway.json` da raiz fixa o `Dockerfile.front` | `railway.kafka.json` para o serviço do Kafka (caminho em *Config as Code*) |
| Testes de banco falhavam depois de um conflito | Violação de constraint aborta a transação do PostgreSQL; o teste usava uma transação só | Sem transação de teste: cada chamada tem a sua, como em produção |
| `RedisStore.ttl` devolvia valor para chave sem expiração | O Spring devolve `0`, não vazio | Filtra só durações positivas |
| Mensagens mortas não chegavam ao DLT | O Spring Kafka 3 publica em `<tópico>-dlt`, não `.DLT`; no Railway a criação automática de tópicos é desligada | Destino explícito no `DeadLetterPublishingRecoverer`; o teste garante que nenhum `-dlt` é criado |
| Parser da resposta da IA deixava `etapa .` | Tags em linha viravam espaço | Tags removidas sem espaço; células de tabela tratadas à parte |
| Cliente da LLM não achava `api.groq.com` | O Reactor Netty ignora o proxy de saída da sessão | `proxyWithSystemProperties()` (inofensivo sem proxy) |
| Groq respondia 429 e o job falhava sem motivo claro | Limite de tokens por minuto do plano gratuito; o prompt tem 342 linhas | Espera o `Retry-After` (até 2 vezes); o erro agora traz status e corpo |
| E-mail "enviado" que não saiu | O workflow `gmail` do n8n responde 200 **antes** de enviar; a credencial "Gmail account" estava expirada | Credencial reconectada pelo usuário; reenvio confirmado na execução do n8n |
| Hook pediu para trocar o autor dos commits de volta para o Claude | Contradiz o pedido do usuário | Mantido o autor `Brayan`; o aviso "Unverified" do GitHub é só visual |

## 5. O que foi verificado de verdade e o que não foi

| Verificação | Resultado |
| --- | --- |
| Os 102 testes automáticos | Passam: unitários, repositórios no **PostgreSQL local real e no SQLite**, Redis local (inclusive com senha), Kafka embutido, fluxo completo |
| **Groq de verdade** (`openai/gpt-oss-120b`) | Perfil forte → `aproved`; perfil sem relação → `recuse`; formato da resposta aceito pelo parser; o 429 foi tratado com espera |
| **n8n de verdade** | E-mail para `flowr3898@gmail.com` enviado: execução 2494 com `success`, mensagem Gmail `1a1a279916f0198e` com a etiqueta `SENT`. A primeira tentativa (execução 2492) falhou por causa da credencial do Gmail |
| Fluxo completo (`AnalysisFlowIntegrationTest`) | SQLite, PDF/DOCX reais, Kafka embutido, consumers, serviço, retry e DLQ. **A LLM é um servidor local** `/chat/completions`, não um modelo |
| **Não verificado:** Kafka do Railway | Conexão, SASL/PLAIN contra o `Dockerfile.kafka` e criação dos tópicos no broker real. A sessão não tem TCP direto (só HTTPS pelo proxy); o túnel que tentei foi bloqueado e não foi refeito |
| **Não verificado:** deploy do back end no Railway | O back end ainda não tem Dockerfile nem serviço lá |

## 6. Integrações externas e o que se aprendeu delas

**n8n, workflow `gmail`** (`POST https://n8n.nexoaicompany.com/webhook/a043ad9f-…`): valida o corpo, escolhe o nó pelo
`type` (`html` ou `text`) e envia pelo Gmail.
- Responde 200 imediatamente, então **o backend não sabe se o Gmail falhou**. Sugestão pendente: só responder depois do
  nó de envio, com 5xx em caso de erro. Não foi aplicada: o workflow é compartilhado e a mudança depende do usuário.
- O nó de validação devolve `valid: false` com os erros, mas o `If` seguinte olha só o `type`; por isso o Java valida a
  mensagem antes de chamar.
- A credencial "Gmail account" é usada por outros workflows (por exemplo, "Avisar comercial de e-mail recebido").

**Railway, Kafka.** O serviço do print tem um TCP Proxy público `autorack.proxy.rlwy.net:48806 → :9001` e o endereço
privado `rhhub.railway.internal`. Pontos a corrigir antes de usar o endereço público:
1. O destino do proxy deve ser a porta `9092` (o Kafka escuta nela), não `9001`.
2. O `entrypoint.sh` fixa o endereço anunciado como `SASL_PLAINTEXT://${KAFKA_ADVERTISED_HOST:-kafka}:9092`: quem
   conecta por fora recebe `kafka:9092` e a conexão cai. Para acesso externo, o `Dockerfile.kafka` precisa de um segundo
   listener anunciando o endereço público. Não foi alterado.
3. Entre serviços do Railway, o caminho certo é a rede privada: `KAFKA_URL=rhhub.railway.internal:9092` no back end e
   `KAFKA_ADVERTISED_HOST=rhhub.railway.internal` no Kafka.
4. Ainda há dúvida se o serviço do print é o Kafka ou o back end/front.

**Limites desta sessão (cloud).** A saída é só HTTPS por um proxy com política de domínios. `api.groq.com` e
`n8n.nexoaicompany.com` foram liberados e funcionaram. Conexão TCP direta (Kafka) não existe nessa sessão; liberar o
domínio não muda isso. Por isso o Kafka real precisa ser testado no computador do usuário ou no próprio Railway.

## 7. Estado atual

Etapas feitas depois da primeira versão deste histórico (todas na `main`):

| Etapa | O que entrou |
| --- | --- |
| 1. `domain` | `Rules` (e-mail só `@gmail.com`, senha, CPF, idade, gênero, código de 6 dígitos, vaga), `JwtService` (access 15 min, refresh 7 dias, 2FA 5 min), `AccessPolicy` (admin e admin raiz), ciclo da candidatura, BCrypt. |
| 2. `infra` | Códigos de verificação, refresh token e rate limit no Redis; templates HTML de e-mail; cliente OAuth do Google (desligado por padrão); `JWT_SECRET` aleatório quando não definido. |
| 3. `service` | `AuthService` (cadastro, login, 2FA, refresh, logout, troca e recuperação de senha, Google, admin raiz na subida) e `UserService`. |
| 4. `service` | `VacancyService`, `ResumeService`, `AdminService` e `ProfilePhotoService` (tabela `user_photos`). |
| 5. `application` | Controllers, filtros (login, limite, cabeçalhos), CORS e erros padronizados. |
| 6. Entrega | `Dockerfile.Api`, `docker-compose.yml`, proxy `/api` no nginx do front e GitHub Actions. |

Decisões tomadas nessas etapas: limite de reenvio de código de 1 minuto; 5 tentativas por código; um único admin raiz (`ADMIN_EMAIL`); BCrypt fora da thread do servidor; o IP do cliente é a última entrada do `X-Forwarded-For`.

**Não verificado de verdade:** o build das imagens Docker (a sessão não tem daemon; o CI faz o build), o login com o Google real, e o Kafka do Railway.

## 8. O que falta

- Trocar a API simulada do front pela real (seção 6 da `DOCUMENTACAO.md`).
- Credenciais do Google Cloud e `GOOGLE_OAUTH_ENABLED=true`, quando houver URL pública.
- O workflow `gmail` do n8n responde 200 antes de enviar: uma falha do Gmail passa despercebida.
- O campo `attempts` da DLQ é uma constante (retries + 1), não a contagem real de tentativas.
- Paginação de vagas e candidaturas, se houver volume.

## 9. Como rodar e testar

```bash
cd src/backend
mvn test                                   # 102 testes; os que precisam de Postgres/Redis locais são pulados se não estiverem de pé
mvn test -Dtest=GroqLiveAnalysisTest       # análise com o Groq de verdade (usa groq_key; pulado sem chave ou rede)
mvn test -Dtest=EmailEndToEndLiveTest -Drhhub.test.email=voce@gmail.com      # ENVIA um e-mail de verdade pelo n8n
KAFKA_URL=host:porta mvn test -Dtest=RemoteKafkaSetupTest -Drhhub.kafka.setup=true   # cria os tópicos num Kafka real
```

Os três testes reais só rodam com opt-in e dizem o motivo quando são pulados (`GROQ-LIVE PULADO`, `EMAIL-LIVE PULADO`,
`KAFKA-REMOTE PULADO`).
