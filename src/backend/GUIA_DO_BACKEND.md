# Guia do backend (para ler e apresentar)

Java 21 + Spring Boot 3.5 com **WebFlux** (reativo: tudo devolve `Mono` = 0 ou 1 valor, `Flux` = vários).
Código em `src/`, testes em `test/`. Os pacotes Java continuam `br.rhhub.*` (o nome do pacote não precisa repetir as pastas).

## 1. A ideia central: arquitetura hexagonal

O miolo do sistema não conhece banco, Kafka, Redis nem Google. Ele só conhece **interfaces** (portas).
Quem fala com o mundo de fora são os **adapters**, plugados por fora.

```
 navegador ──► application/web ──► service ──► application/port/out (interfaces)
                (controllers)     (regras de        ▲
                                   negócio)         │ implementadas por
                                                    │
                                                  infra/* (banco, Redis, Kafka, Google, IA, e-mail)
 domain/ (modelos e regras puras) é usado por todos e não depende de ninguém.
```

Vantagem para explicar: dá para trocar PostgreSQL por SQLite, ou a IA por uma resposta fixa, sem mexer nas regras. Os testes usam versões em memória (`test/.../service/Fakes.java`).

## 2. Mapa de pastas

| Pasta | O que tem | Exemplos |
|---|---|---|
| `application/web` | Controllers (rotas HTTP), filtros e conversão para JSON | `AuthController`, `VacancyController`, `AuthFilter`, `RateLimitFilter`, `dto/Views` |
| `application/port/in` | O que o sistema oferece aos consumidores de fila | `AnalyzeResume`, `DeliverEmail` |
| `application/port/out` | Interfaces do que o sistema precisa do mundo de fora | `UserRepository`, `AiProvider`, `EmailSender`, `RateLimiter` |
| `service` | Regras de negócio (um serviço por assunto) | `AuthService`, `ResumeService`, `VacancyService` |
| `domain/model` | Os "objetos" do sistema | `User`, `Vacancy`, `Resume`, `Notification` |
| `domain/rule` | Validações puras | `Rules` (e-mail, CPF, senha), `Stages` (etapas da vaga) |
| `domain/auth` | JWT, tipos de token, política de acesso | `JwtService`, `AccessPolicy`, `TokenType` |
| `domain/exception` | Erros de negócio (viram 400/401/403/404/409...) | `DomainException` |
| `infra/database` | Banco: PostgreSQL (produção) ou SQLite (local) | `*Adapter`, `table/*Table`, `SqlExecutor` |
| `infra/cache` | Cache-aside no Redis | `QueryCache`, `Cached*Repository` |
| `infra/redis` | Redis: limite de requisições, refresh token, códigos | `RedisRateLimiter`, `RedisVerificationCodeStore` |
| `infra/messaging` | Kafka: filas de análise e de e-mail, dead letter | `AnalysisConsumer`, `EmailConsumer`, `KafkaConfig` |
| `infra/llm` | IA que analisa o currículo (Groq/OpenAI) | `OpenAiCompatibleProvider` |
| `infra/email` | Envio de e-mail pelo n8n | `N8nEmailSender`, `HtmlEmailComposer` |
| `infra/google` | Login com Google (OAuth2) | `GoogleOAuthClient` |
| `infra/security` | Hash de senha (BCrypt) e beans de auth | `BcryptPasswordHasher`, `AuthConfig` |
| `infra/config` | Propriedades e beans de configuração | `AppProperties`, `WebProperties` |

## 3. Os três fluxos para contar na apresentação

**Login com senha:** `AuthController.login` → `AuthService.login` (confere senha com BCrypt; se tiver 2FA, manda código por e-mail) → gera dois JWT: *acesso* (15 min) e *refresh* → vão em cookies `HttpOnly`. O `AuthFilter` valida o cookie de acesso em toda rota `/api`.

**Login/cadastro com Google:** `GoogleController.start` redireciona ao Google guardando um `state` em cookie → o Google volta em `/login/oauth2/code/google` → `GoogleController.callback` confere o `state`, troca o `code` pelo perfil (`GoogleOAuthClient`) → `AuthService.loginWithGoogle` entra na conta ou cria (só no modo cadastro). Conta nova precisa completar CPF, idade e gênero.

**Candidatura com análise por IA:** `ResumeController.submit` (PDF/DOCX até 5 MB) → `ResumeService.submit` valida o arquivo, grava e publica no **Kafka** → `AnalysisConsumer` chama a IA (`AnalyzeResumeService`) → grava aprovado/reprovado e o motivo → publica um e-mail na fila → `EmailConsumer` envia pelo **n8n**. Se esgotar as tentativas, vai para a *dead letter* (tabela `dead_letters`) e o `PendingResumeRepublisher` reenvia as pendentes.

## 4. Segurança (pontos fortes para citar)

- Senha com BCrypt, fora da thread do servidor (`AsyncPasswordHasher`).
- JWT em cookie `HttpOnly` + `Secure` + `SameSite`; refresh token guardado no Redis (dá para encerrar sessões).
- Código por e-mail: vale uma vez, 1 por minuto, 5 tentativas.
- Limite de requisições por IP (`RateLimitFilter` + Redis) e cabeçalhos de segurança (`SecurityHeadersFilter`).
- Cache nunca guarda hash de senha nem `google_sub` (`User.withoutCredentials`).

## 5. Como ler um arquivo

1. Comece pelo comentário `/** ... */` no topo da classe: diz o papel dela.
2. Controller → mostra a rota e chama um `service`.
3. Service → tem a regra. Os `.flatMap(...)` encadeiam passos assíncronos; `switchIfEmpty` é o "senão".
4. Interface em `port/out` → o que ele precisa; a implementação está em `infra/`.

## 6. Nomes que mudaram

| Antes | Agora | Motivo |
|---|---|---|
| `service/Passwords` | `service/AsyncPasswordHasher` | faz o hash de senha fora da thread do servidor |
| `infra/email/JwtHs256` | `infra/email/N8nJwtSigner` | assina o JWT que o webhook do n8n exige |

## 7. Testes

`./mvnw test` roda tudo. Separados em `unit`, `integration` e `functional` (anotações em `test/.../testing`).
